package services

import akka.actor.{ActorSystem, Cancellable}
import play.api.{Configuration, Logging}
import play.api.libs.json.Json

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

final case class StreamSessionStatus(
    streamId: String,
    topic: String,
    startedAt: String,
    ratePerSecond: Int,
    batchEveryMillis: Int,
    published: Long,
    running: Boolean
)

object StreamSessionStatus {
  implicit val writes = Json.writes[StreamSessionStatus]
}

@Singleton
class EventStreamManager @Inject() (
    actorSystem: ActorSystem,
    config: Configuration,
    generator: EventGenerator,
    kafka: KafkaPublisher,
    redis: RedisClientProvider
)(implicit ec: ExecutionContext)
    extends Logging {

  private final case class Session(
      streamId: String,
      topic: String,
      startedAt: Instant,
      ratePerSecond: Int,
      batchEveryMillis: Int,
      cancellable: Cancellable
  )

  // Keep minimal in-memory control data (cancellable) only. All analytics
  // counters and metadata are stored in Redis so that websockets and other
  // processes can read them in real time.
  private val sessions = mutable.Map.empty[String, Session]

  private val hourFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)

  private val defaultTopic = config.get[String]("kafka.topic")
  private val defaultRatePerSecond =
    config.getOptional[Int]("eventStream.ratePerSecond").getOrElse(2000)
  private val defaultBatchEveryMillis =
    config.getOptional[Int]("eventStream.batchEveryMillis").getOrElse(10)

  def start(
      ratePerSecondOpt: Option[Int] = None,
      topicOpt: Option[String] = None,
      batchEveryMillisOpt: Option[Int] = None
  ): StreamSessionStatus = {

    val streamId = UUID.randomUUID().toString
    val topic = topicOpt.getOrElse(defaultTopic)
    val ratePerSecond = ratePerSecondOpt.getOrElse(defaultRatePerSecond)
    val batchEveryMillis =
      batchEveryMillisOpt.getOrElse(defaultBatchEveryMillis)

    val startedAt = Instant.now()

    val batchSize = math.max(
      1,
      (ratePerSecond.toLong * batchEveryMillis.toLong / 1000L).toInt
    )

    val cancellable = actorSystem.scheduler.scheduleAtFixedRate(
      0.millis,
      batchEveryMillis.millis
    ) { () =>
      try {
        // Performance: use a simple while-loop to avoid allocating iterator
        // objects on hot paths when generating large batches of events.
        var i = 0
        while (i < batchSize) {
          val event = generator.generate()
          val payload = Json.toJson(event).toString()
          kafka.publish(topic, event.userId, payload)

          // Update analytics counters in Redis: per-stream, per-topic total,
          // and per-topic-per-hour. These are lightweight increments that
          // external viewers (websockets) can read.
          try {
            redis.withJedis { jedis =>
              jedis.incr(s"stream:published:$streamId")
              jedis.incr(s"topic:total:$topic")
              val hourKey = hourFormatter.format(Instant.now())
              jedis.incr(s"topic:hour:$topic:$hourKey")
            }
          } catch {
            case t: Throwable =>
              logger.warn("Failed updating Redis analytics", t)
          }

          i += 1
        }
      } catch {
        case t: Throwable =>
          logger.warn(s"Event stream batch failed (streamId=$streamId)", t)
      }
    }

    val session = Session(
      streamId = streamId,
      topic = topic,
      startedAt = startedAt,
      ratePerSecond = ratePerSecond,
      batchEveryMillis = batchEveryMillis,
      cancellable = cancellable
    )

    sessions.synchronized { sessions.put(streamId, session) }

    // Persist session metadata and initialize counters in Redis
    try {
      redis.withJedis { jedis =>
        val metaKey = s"stream:meta:$streamId"
        jedis.hset(
          metaKey,
          Map(
            "topic" -> topic,
            "ratePerSecond" -> ratePerSecond.toString,
            "batchEveryMillis" -> batchEveryMillis.toString,
            "startedAt" -> startedAt.toString
          ).asJava
        )
        jedis.set(s"stream:published:$streamId", "0")
      }
    } catch {
      case t: Throwable =>
        logger.warn("Failed writing stream metadata to Redis", t)
    }

    StreamSessionStatus(
      streamId = streamId,
      topic = topic,
      startedAt = startedAt.toString,
      ratePerSecond = ratePerSecond,
      batchEveryMillis = batchEveryMillis,
      published = 0L,
      running = true
    )
  }

  def stop(streamId: String): Boolean = {
    val removedOpt = sessions.synchronized { sessions.remove(streamId) }
    if (removedOpt.isEmpty) return false

    try removedOpt.get.cancellable.cancel()
    catch {
      case t: Throwable =>
        logger.warn(s"Failed cancelling streamId=$streamId", t)
    }

    true
  }

  def status(streamId: String): Option[StreamSessionStatus] = {
    // Read metadata and counters from Redis when available; fall back to
    // in-memory session for configuration data.
    try {
      redis.withJedis { jedis =>
        val metaKey = s"stream:meta:$streamId"
        val meta = Option(jedis.hgetAll(metaKey)).filter(_.size() > 0)
        val published = Option(jedis.get(s"stream:published:$streamId"))
          .flatMap(s => scala.util.Try(s.toLong).toOption)
          .getOrElse(0L)

        meta.map { m =>
          StreamSessionStatus(
            streamId = streamId,
            topic = m.getOrDefault("topic", ""),
            startedAt = m.getOrDefault("startedAt", ""),
            ratePerSecond = m.getOrDefault("ratePerSecond", "0").toInt,
            batchEveryMillis = m.getOrDefault("batchEveryMillis", "0").toInt,
            published = published,
            running = sessions.synchronized { sessions.contains(streamId) }
          )
        }
      }
    } catch {
      case t: Throwable =>
        logger.warn("Failed reading stream status from Redis", t)
        // Fallback: check in-memory session
        sessions.synchronized {
          sessions.get(streamId).map { s =>
            StreamSessionStatus(
              streamId = s.streamId,
              topic = s.topic,
              startedAt = s.startedAt.toString,
              ratePerSecond = s.ratePerSecond,
              batchEveryMillis = s.batchEveryMillis,
              published = 0L,
              running = true
            )
          }
        }
    }
  }
}
