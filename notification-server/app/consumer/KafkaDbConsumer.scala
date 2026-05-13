package consumers

import config.KafkaConsumerConfig
import models.UserActivityEvent
import models.db.UserActivityEventRow
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.Configuration
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import repositories.UserActivityEventRepository
import startup.FlywayMigrator

import java.time.{Duration, Instant}
import java.util.{Collections, Properties}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class KafkaDbConsumer @Inject() (
    consumerConfig: KafkaConsumerConfig,
    config: Configuration,
    flyway: FlywayMigrator,
    repository: UserActivityEventRepository,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  logger.info("KafkaDbConsumer starting")
  println("[kafka-db-consumer] starting")

  private val kafka =
    config.get[Configuration]("kafka")

  private val dbWriter =
    kafka.get[Configuration]("dbWriter")

  private val groupId =
    dbWriter.get[String]("groupId")

  private val pollTimeoutMillis =
    dbWriter.get[Int]("pollTimeoutMillis")

  private val batchSize =
    dbWriter.get[Int]("batchSize")

  private val enableAutoCommit =
    dbWriter.get[Boolean]("enableAutoCommit")

  private val autoOffsetReset =
    dbWriter.get[String]("autoOffsetReset")

  private val maxPollRecords =
    dbWriter
      .getOptional[Int]("maxPollRecords")
      .getOrElse(batchSize)

  private val running = new AtomicBoolean(true)

  private val props = new Properties()

  props.put(
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
    consumerConfig.bootstrapServers
  )

  props.put(
    ConsumerConfig.GROUP_ID_CONFIG,
    groupId
  )

  props.put(
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
    classOf[StringDeserializer].getName
  )

  props.put(
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
    classOf[StringDeserializer].getName
  )

  props.put(
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
    enableAutoCommit.toString
  )

  props.put(
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
    autoOffsetReset
  )

  props.put(
    ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
    maxPollRecords.toString
  )

  private val consumer =
    new KafkaConsumer[String, String](props)

  consumer.subscribe(
    Collections.singletonList(
      consumerConfig.topic
    )
  )

  private val thread =
    new Thread(() => consumeLoop())

  thread.setDaemon(true)
  thread.start()

  private def consumeLoop(): Unit = {
    while (running.get()) {
      try {
        val records =
          consumer.poll(
            Duration.ofMillis(
              pollTimeoutMillis
            )
          )

        val batch =
          records.asScala.toList

        if (batch.nonEmpty) {
          println(s"[kafka-db-consumer] polled=${batch.size}")
          logger.info(s"Kafka polled batchSize=${batch.size}")
          batch.take(5).foreach { r =>
            println(s"[kafka-db-consumer] msg=${r.value()}")
          }
        }

        if (batch.nonEmpty) {
          val rows =
            batch.map { record =>
              val event =
                Json
                  .parse(record.value())
                  .as[UserActivityEvent]

              UserActivityEventRow(
                eventId = event.eventId,
                userId = event.userId,
                sessionId = event.sessionId,
                eventType = event.eventType,
                page = event.page,
                timestamp = Instant.parse(event.timestamp),
                device = event.device,
                browser = event.browser,
                scrollDepth = event.scrollDepth,
                location = event.location
              )
            }

          val future =
            repository.insertBatch(rows)

          future.foreach { _ =>
            println(s"[kafka-db-consumer] db-inserted batchSize=${rows.size}")
            logger.info(s"DB inserted batchSize=${rows.size}")
            if (!enableAutoCommit) {
              consumer.commitSync()
            }

            logger.info(
              s"Inserted batch size=${rows.size}"
            )
          }
        }
      } catch {
        case t: Throwable =>
          logger.error(
            "Kafka DB consumer failed",
            t
          )
      }
    }
  }

  lifecycle.addStopHook { () =>
    running.set(false)
    consumer.close()
    Future.successful(())
  }
}
