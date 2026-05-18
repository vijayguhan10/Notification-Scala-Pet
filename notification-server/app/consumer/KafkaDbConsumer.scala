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

  // ============================================================
  // Kafka Configuration
  // ============================================================

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

  // ============================================================
  // Advanced Kafka Config
  // ============================================================

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
    new KafkaConsumer[String, String](props)

  // ============================================================
  // Rebalance Listener
  // ============================================================

  private val rebalanceListener =
    new ConsumerRebalanceListener {

      override def onPartitionsAssigned(
          partitions: java.util.Collection[TopicPartition]
      ): Unit = {

        // println(
        //   s"[kafka-db-consumer] partitions assigned = ${partitions.asScala.mkString(", ")}"
        // )

        logger.info(
          s"Partitions assigned: ${partitions.asScala.mkString(", ")}"
        )
      }

      override def onPartitionsRevoked(
          partitions: java.util.Collection[TopicPartition]
      ): Unit = {

        // println(
        //   s"[kafka-db-consumer] partitions revoked = ${partitions.asScala.mkString(", ")}"
        // )

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
      "kafka-db-consumer"
    )

  // IMPORTANT:
  // non-daemon thread is safer for Kafka consumers

  thread.setDaemon(false)

  thread.start()

  // ============================================================
  // Consume Loop
  // ============================================================

  private def consumeLoop(): Unit = {

    try {

      while (running.get()) {

        try {

          // ====================================================
          // Poll Kafka
          // ====================================================

          val records =
            consumer.poll(
              Duration.ofMillis(
                pollTimeoutMillis
              )
            )

          val batch =
            records.asScala.toList

          // ====================================================
          // Debug Consumer State
          // ====================================================

          val assignments =
            consumer.assignment().asScala.toList

          val positions =
            assignments.map { partition =>
              s"$partition -> ${consumer.position(partition)}"
            }

          // println(
          //   s"""
          //      |====================================================
          //      |[kafka-db-consumer]
          //      |polledRecords=${batch.size}
          //      |assignments=$assignments
          //      |positions=$positions
          //      |====================================================
          //      |""".stripMargin
          // )

          // ====================================================
          // Empty Poll
          // ====================================================

          if (batch.isEmpty) {
            // println(
            //   "[kafka-db-consumer] no records polled"
            // )
          }

          // ====================================================
          // Process Batch
          // ====================================================

          if (batch.nonEmpty) {

            batch.take(5).foreach { record =>
              // println(
              //   s"[kafka-db-consumer] received=${record.value()}"
              // )
            }

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

            // ==================================================
            // DB INSERT
            // ==================================================

            try {

              // notifications are now handled by the RabbitMQ dispatcher
              // (do not send emails here)

              val start =
                System.currentTimeMillis()

              Await.result(
                repository.insertBatch(rows),
                30.seconds
              )

              val elapsed =
                System.currentTimeMillis() - start

              // println(
              //   s"""
              //      |====================================================
              //      |DB INSERT SUCCESS
              //      |batchSize=${rows.size}
              //      |insertTimeMs=$elapsed
              //      |====================================================
              //      |""".stripMargin
              // )

              logger.info(
                s"DB inserted batchSize=${rows.size}"
              )

              // ================================================
              // Manual Offset Commit
              // ================================================

              if (!enableAutoCommit) {

                consumer.commitSync()

                // println(
                //   s"""
                //      |====================================================
                //      |OFFSET COMMIT SUCCESS
                //      |committedBatchSize=${rows.size}
                //      |====================================================
                //      |""".stripMargin
                // )
              }

            } catch {

              case ex: Throwable =>

                // println(
                //   s"""
                //      |====================================================
                //      |DB INSERT FAILED
                //      |batchSize=${rows.size}
                //      |error=${ex.getMessage}
                //      |====================================================
                //      |""".stripMargin
                // )

                ex.printStackTrace()

                logger.error(
                  s"DB insert failed batchSize=${rows.size}",
                  ex
                )
            }
          }

        } catch {

          // ====================================================
          // Graceful Shutdown
          // ====================================================

          case _: WakeupException if !running.get() =>

            println(
              "[kafka-db-consumer] wakeup received, shutting down"
            )

          // ====================================================
          // Kafka Poll Failure
          // ====================================================

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

            // Prevent tight infinite error loop
            Thread.sleep(2000)
        }
      }

    } finally {

      // ========================================================
      // Cleanup
      // ========================================================

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

  // ============================================================
  // Graceful Shutdown Hook
  // ============================================================

  lifecycle.addStopHook { () =>
    println(
      "[kafka-db-consumer] application shutdown initiated"
    )

    running.set(false)

    // Safe cross-thread interruption
    consumer.wakeup()

    Future {

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
