package dispatchers

// RabbitMQ Java Client
import com.rabbitmq.client._

// Application RabbitMQ configuration
import config.RabbitMqConfig

// Play logging support
import play.api.Logging

// Business service responsible for sending notifications
import services.NotificationPushService

// Used to convert byte[] -> String
import java.nio.charset.StandardCharsets

// Play dependency injection
import javax.inject.{Inject, Singleton}

@Singleton
class RabbitMqNotificationDispatcher @Inject() (
    rabbitConfig: RabbitMqConfig,
    pushService: NotificationPushService
) extends Logging {

  // ============================================================
  // RabbitMQ Connection Factory
  // ============================================================

  // Factory used to create RabbitMQ TCP connections
  private val factory =
    new ConnectionFactory()

  // Configure RabbitMQ host
  factory.setHost(rabbitConfig.host)

  // Configure RabbitMQ port + credentials (compose uses admin/admin123)
  factory.setPort(rabbitConfig.port)
  factory.setUsername(rabbitConfig.username)
  factory.setPassword(rabbitConfig.password)

  // Helps the client recover if the broker restarts
  factory.setAutomaticRecoveryEnabled(true)
  factory.setNetworkRecoveryInterval(5000)

  // ============================================================
  // RabbitMQ Connection
  // ============================================================

  // Creates real TCP connection to RabbitMQ broker
  private val connection =
    factory.newConnection()

  // ============================================================
  // RabbitMQ Channel
  // ============================================================

  // Channel = lightweight communication session
  // All RabbitMQ operations happen through channels
  private val channel =
    connection.createChannel()

  // ============================================================
  // Prefetch Configuration
  // ============================================================

  // Maximum unacknowledged messages allowed at once
  //
  // RabbitMQ will send at most 1 messages
  // before waiting for ACKs.
  //
  // Provides backpressure control.
  channel.basicQos(1)

  // ============================================================
  // RabbitMQ Consumer
  // ============================================================

  // Creates message handler object.
  //
  // RabbitMQ automatically calls handleDelivery()
  // whenever a message arrives in the queue.
  private val consumer =
    new DefaultConsumer(channel) {

      // ========================================================
      // Message Callback
      // ========================================================

      override def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
      ): Unit = {

        // ------------------------------------------------------
        // Convert RabbitMQ bytes -> String payload
        // ------------------------------------------------------

        val payload =
          new String(
            body,
            StandardCharsets.UTF_8
          )

        try {

          // ----------------------------------------------------
          // Business Logic
          // ----------------------------------------------------

          // Push notification to external system/service
          pushService.push(payload)

          // ----------------------------------------------------
          // ACK Message
          // ----------------------------------------------------

          // Notify RabbitMQ:
          // message processed successfully
          //
          // RabbitMQ removes message from queue.
          channel.basicAck(
            envelope.getDeliveryTag,
            false
          )

        } catch {

          case t: Throwable =>

            logger.error(
              "Notification push failed",
              t
            )

            // --------------------------------------------------
            // NACK Message
            // --------------------------------------------------

            // Notify RabbitMQ:
            // message processing failed
            //
            // requeue=false:
            // do not put message back into queue
            //
            // Message will move to DLQ
            // because queue has DLQ configuration.
            channel.basicNack(
              envelope.getDeliveryTag,
              false,
              false
            )
        }
      }
    }

  // ============================================================
  // Start Consumer
  // ============================================================

  // Subscribes this consumer to RabbitMQ queue.
  //
  // autoAck=false:
  // manual ACK/NACK mode enabled.
  //
  // RabbitMQ waits for:
  // - basicAck()
  // - basicNack()
  //
  // before considering message completed.
  // channel.basicConsume(
  //   rabbitConfig.queue,
  //   false,
  //   consumer
  // )
}

// clustering
// queue replication
// quorum queues
// mirrored queues
