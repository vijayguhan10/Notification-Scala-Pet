package consumers

import config.KafkaConsumerConfig
import messaging.RabbitMqPublisher
import models.{NotificationMessage, UserActivityEvent}
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import services.NotificationBuilder
import services.{
  IntentScoringEngine,
  NotificationDelayPolicy,
  RedisBehavioralStateStore
}

import java.time.Duration
import java.util.{Collections, Properties}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters._

@Singleton
class KafkaNotificationConsumer @Inject() (
    consumerConfig: KafkaConsumerConfig,
    config: Configuration,
    notificationBuilder: NotificationBuilder,
    behavioralStateStore: RedisBehavioralStateStore,
    intentScoring: IntentScoringEngine,
    delayPolicy: NotificationDelayPolicy,
    rabbitPublisher: RabbitMqPublisher,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  logger.info("KafkaNotificationConsumer starting")

  // ============================================================
  // Kafka Configuration
  // ============================================================

  private val kafka =
    config.get[Configuration]("kafka")

  private val notificationWriter =
    kafka.get[Configuration](
      "notificationWriter"
    )

  private val groupId =
    notificationWriter.get[String](
      "groupId"
    )

  private val pollTimeoutMillis =
    notificationWriter.get[Int](
      "pollTimeoutMillis"
    )

  private val batchSize =
    notificationWriter.get[Int](
      "batchSize"
    )

  private val enableAutoCommit =
    notificationWriter.get[Boolean](
      "enableAutoCommit"
    )

  private val autoOffsetReset =
    notificationWriter.get[String](
      "autoOffsetReset"
    )

  private val maxPollRecords =
    notificationWriter
      .getOptional[Int](
        "maxPollRecords"
      )
      .getOrElse(batchSize)

  // ============================================================
  // Advanced Kafka Config
  // ============================================================

  private val fetchMaxBytes =
    kafka.get[Int]("fetchMaxBytes")

  private val maxPartitionFetchBytes =
    kafka.get[Int](
      "maxPartitionFetchBytes"
    )

  private val fetchMinBytes =
    kafka.get[Int](
      "fetchMinBytes"
    )

  private val fetchMaxWaitMs =
    kafka.get[Int](
      "fetchMaxWaitMs"
    )

  private val sessionTimeoutMs =
    kafka.get[Int](
      "sessionTimeoutMs"
    )

  private val heartbeatIntervalMs =
    kafka.get[Int](
      "heartbeatIntervalMs"
    )

  private val maxPollIntervalMs =
    kafka.get[Int](
      "maxPollIntervalMs"
    )

  // ============================================================
  // Running State
  // ============================================================

  private val running =
    new AtomicBoolean(true)

  // ============================================================
  // Kafka Consumer Properties
  // ============================================================

  private val props =
    new Properties()

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

  props.put(
    ConsumerConfig.FETCH_MAX_BYTES_CONFIG,
    fetchMaxBytes.toString
  )

  props.put(
    ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
    maxPartitionFetchBytes.toString
  )

  props.put(
    ConsumerConfig.FETCH_MIN_BYTES_CONFIG,
    fetchMinBytes.toString
  )

  props.put(
    ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
    fetchMaxWaitMs.toString
  )

  props.put(
    ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
    sessionTimeoutMs.toString
  )

  props.put(
    ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
    heartbeatIntervalMs.toString
  )

  props.put(
    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
    maxPollIntervalMs.toString
  )

  // ============================================================
  // Kafka Consumer
  // ============================================================

  private val consumer =
    new KafkaConsumer[String, String](
      props
    )

  // ============================================================
  // Rebalance Listener
  // ============================================================

  private val rebalanceListener =
    new ConsumerRebalanceListener {

      override def onPartitionsAssigned(
          partitions: java.util.Collection[
            TopicPartition
          ]
      ): Unit = {

        logger.info(
          s"Partitions assigned: ${partitions.asScala.mkString(", ")}"
        )
      }

      override def onPartitionsRevoked(
          partitions: java.util.Collection[
            TopicPartition
          ]
      ): Unit = {

        logger.warn(
          s"Partitions revoked: ${partitions.asScala.mkString(", ")}"
        )
      }
    }

  consumer.subscribe(
    Collections.singletonList(
      consumerConfig.topic
    ),
    rebalanceListener
  )

  // ============================================================
  // Consumer Thread
  // ============================================================

  private val thread =
    new Thread(
      () => consumeLoop(),
      "kafka-notification-consumer"
    )

  thread.setDaemon(false)

  thread.start()

  // ============================================================
  // Consume Loop
  // ============================================================

  private def consumeLoop(): Unit = {

    try {

      while (running.get()) {

        try {

          val records =
            consumer.poll(
              Duration.ofMillis(
                pollTimeoutMillis
              )
            )

          val batch =
            records.asScala
              .take(batchSize)
              .toList

          if (batch.nonEmpty) {

            batch.foreach { record =>
              try {

                val event =
                  Json
                    .parse(record.value())
                    .as[UserActivityEvent]

                val intent =
                  try {
                    behavioralStateStore.store(event)
                    intentScoring.updateAndGet(event)
                  } catch {
                    case t: Throwable =>
                      logger.warn(
                        s"Redis intent scoring failed (userId=${event.userId}); continuing without delay",
                        t
                      )
                      services.IntentScoreResult(0, "low")
                  }

                val notification: NotificationMessage =
                  notificationBuilder
                    .build(event, intent.score, intent.category)

                val delayMs =
                  delayPolicy.delayMs(intent.category)

                rabbitPublisher.publish(notification, delayMs)

              } catch {

                case ex: Throwable =>

                  logger.error(
                    s"""
                       |Notification processing failed
                       |offset=${record.offset()}
                       |partition=${record.partition()}
                       |""".stripMargin,
                    ex
                  )
              }
            }

            if (!enableAutoCommit) {

              consumer.commitSync()

              logger.info(
                s"Committed batch size=${batch.size}"
              )
            }
          }

        } catch {

          case _: WakeupException if !running.get() =>

            logger.info(
              "Kafka notification consumer shutting down"
            )

          case ex: Throwable =>

            logger.error(
              "Kafka notification consumer failed",
              ex
            )

            Thread.sleep(2000)
        }
      }

    } finally {

      try {

        consumer.close()

      } catch {

        case ex: Throwable =>
          logger.error(
            "Failed to close kafka consumer",
            ex
          )
      }

      logger.info(
        "Kafka notification consumer closed"
      )
    }
  }

  // ============================================================
  // Graceful Shutdown Hook
  // ============================================================

  lifecycle.addStopHook { () =>
    logger.info(
      "Kafka notification shutdown initiated"
    )

    running.set(false)

    consumer.wakeup()

    Future {

      blocking {

        thread.join(5000)
      }

      logger.info(
        "Kafka notification shutdown complete"
      )

      ()
    }
  }
}
