package consumers

import config.KafkaConsumerConfig
import models.UserActivityEvent
import models.db.UserActivityEventRow
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import repositories.UserActivityEventRepository

import java.time.{Duration, Instant}
import java.util.{Collections, Properties}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class KafkaDbConsumer @Inject()(
    consumerConfig: KafkaConsumerConfig,
    repository: UserActivityEventRepository,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  private val running =
    new AtomicBoolean(true)

  private val props = new Properties()

  props.put(
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
    consumerConfig.bootstrapServers
  )

  props.put(
    ConsumerConfig.GROUP_ID_CONFIG,
    consumerConfig.groupId
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
    "false"
  )

  props.put(
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
    "earliest"
  )

  private val consumer =
    new KafkaConsumer[String, String](props)

  consumer.subscribe(
    Collections.singletonList(consumerConfig.topic)
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
              consumerConfig.pollTimeoutMillis
            )
          )

        val batch =
          records
            .asScala
            .take(consumerConfig.batchSize)
            .toList

        if (batch.nonEmpty) {

          val rows =
            batch.map { record =>

              val event =
                Json.parse(record.value())
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

            consumer.commitSync()

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