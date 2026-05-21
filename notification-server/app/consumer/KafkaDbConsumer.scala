package consumers

import config.KafkaConsumerConfig
import models.UserActivityEvent
import models.db.UserActivityEventRow
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import repositories.UserActivityEventRepository

import java.time.{Duration, Instant}
import java.util.{Collections, Properties}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

@Singleton
class KafkaDbConsumer @Inject() (
    consumerConfig: KafkaConsumerConfig,
    config: Configuration,
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

  private val fetchMaxBytes =
    kafka.get[Int]("fetchMaxBytes")

  private val maxPartitionFetchBytes =
    kafka.get[Int]("maxPartitionFetchBytes")

  private val fetchMinBytes =
    kafka.get[Int]("fetchMinBytes")

  private val fetchMaxWaitMs =
    kafka.get[Int]("fetchMaxWaitMs")

  private val sessionTimeoutMs =
    kafka.get[Int]("sessionTimeoutMs")

  private val heartbeatIntervalMs =
    kafka.get[Int]("heartbeatIntervalMs")

  private val maxPollIntervalMs =
    kafka.get[Int]("maxPollIntervalMs")

  private val running =
    new AtomicBoolean(true)

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

  private val consumer =
    new KafkaConsumer[String, String](props)

  private val rebalanceListener =
    new ConsumerRebalanceListener {

      override def onPartitionsAssigned(
          partitions: java.util.Collection[TopicPartition]
      ): Unit = {

        logger.info(
          s"Partitions assigned: ${partitions.asScala.mkString(", ")}"
        )
      }

      override def onPartitionsRevoked(
          partitions: java.util.Collection[TopicPartition]
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

  private val thread =
    new Thread(
      () => consumeLoop(),
      "kafka-db-consumer"
    )

  thread.setDaemon(false)

  // Intentionally non-daemon: keep this consumer thread alive so it can
  // complete in-flight work during shutdown. Daemon threads may be
  // terminated abruptly by the JVM which risks lost work.

  thread.start()

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
            records.asScala.toList

          

          if (batch.isEmpty) {}

          if (batch.nonEmpty) {

            batch.take(5).foreach { record => }

            val rows =
              batch.map { record =>
                try {

                  val event =
                    Json
                      .parse(record.value())
                      .as[UserActivityEvent]

                  UserActivityEventRow(
                    userId = event.userId,
                    parkingSearches = event.parkingSearches,
                    slotViews = event.slotViews,
                    bookingAttempts = event.bookingAttempts,
                    avgScrollDepth = event.avgScrollDepth,
                    location = event.location,
                    lastActivity = Instant.parse(event.lastActivity),
                    sessionDuration = event.sessionDuration
                  )

                } catch {

                  case ex: Throwable =>

                    println(
                      s"""
                         |====================================================
                         |JSON PARSE FAILED
                         |offset=${record.offset()}
                         |partition=${record.partition()}
                         |payload=${record.value()}
                         |error=${ex.getMessage}
                         |====================================================
                         |""".stripMargin
                    )

                    throw ex
                }
              }

            try {

              val start =
                System.currentTimeMillis()

              // Blocking DB write: we synchronously wait up to 30s for the
              // batch insert to complete. This will block the consumer thread
              // and is a deliberate trade-off to keep at-least-once ordering.
              // Consider async writes if throughput/latency become problematic.

              Await.result(
                repository.insertBatch(rows),
                30.seconds
              )

              val elapsed =
                System.currentTimeMillis() - start

              logger.info(
                s"DB inserted batchSize=${rows.size}"
              )

              if (!enableAutoCommit) {

                consumer.commitSync()

              }

            } catch {

              case ex: Throwable =>

                ex.printStackTrace()

                logger.error(
                  s"DB insert failed batchSize=${rows.size}",
                  ex
                )
            }
          }

        } catch {

          case _: WakeupException if !running.get() =>

            println(
              "[kafka-db-consumer] wakeup received, shutting down"
            )

          case ex: Throwable =>

            println(
              s"""
                 |====================================================
                 |KAFKA CONSUMER ERROR
                 |error=${ex.getMessage}
                 |====================================================
                 |""".stripMargin
            )

            ex.printStackTrace()

            logger.error(
              "Kafka DB consumer failed",
              ex
            )

            Thread.sleep(2000)
        }
      }

    } finally {

      println(
        "[kafka-db-consumer] closing kafka consumer"
      )

      try {
        consumer.close()
      } catch {
        case ex: Throwable =>
          ex.printStackTrace()
      }

      println(
        "[kafka-db-consumer] consumer closed"
      )
    }
  }

  lifecycle.addStopHook { () =>
    println(
      "[kafka-db-consumer] application shutdown initiated"
    )

    running.set(false)

    consumer.wakeup()

    Future {

      // Graceful shutdown: wait up to 5s for the consumer thread to finish.
      // This bounded join avoids hanging shutdowns while allowing in-flight
      // work to complete where possible.
      blocking {
        thread.join(5000)
      }

      println(
        "[kafka-db-consumer] shutdown complete"
      )

      ()
    }
  }
}
