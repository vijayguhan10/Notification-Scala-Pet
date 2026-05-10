package services

import akka.actor.{ActorSystem, Cancellable}
import play.api.{Configuration, Logging}
import play.api.libs.json.Json

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    kafka: KafkaPublisher
)(implicit ec: ExecutionContext)
    extends Logging {

  private final case class Session(
      streamId: String,
      topic: String,
      startedAt: Instant,
      ratePerSecond: Int,
      batchEveryMillis: Int,
      published: AtomicLong,
      cancellable: Cancellable
  )

  private val sessions = new ConcurrentHashMap[String, Session]()

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

    val published = new AtomicLong(0L)
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
        var i = 0
        while (i < batchSize) {
          val event = generator.generate()
          val payload = Json.toJson(event).toString()
          kafka.publish(topic, event.userId, payload)
          published.incrementAndGet()
          i += 1
          print("data: " + payload + "\n\n")
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
      published = published,
      cancellable = cancellable
    )

    sessions.put(streamId, session)

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
    val removed = sessions.remove(streamId)
    if (removed == null) return false

    try removed.cancellable.cancel()
    catch {
      case t: Throwable =>
        logger.warn(s"Failed cancelling streamId=$streamId", t)
    }

    true
  }

  def status(streamId: String): Option[StreamSessionStatus] = {
    val s = sessions.get(streamId)
    if (s == null) None
    else {
      Some(
        StreamSessionStatus(
          streamId = s.streamId,
          topic = s.topic,
          startedAt = s.startedAt.toString,
          ratePerSecond = s.ratePerSecond,
          batchEveryMillis = s.batchEveryMillis,
          published = s.published.get(),
          running = true
        )
      )
    }
  }
}
