package dispatchers

import com.rabbitmq.client._
import config.{NotificationConfig, RabbitMqConfig}
import play.api.Logging
import services.NotificationPushService

import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}

@Singleton
class RabbitMqNotificationDispatcher @Inject()(
    rabbitConfig: RabbitMqConfig,
    pushService: NotificationPushService
) extends Logging {

  private val factory =
    new ConnectionFactory()

  factory.setHost(rabbitConfig.host)

  private val connection =
    factory.newConnection()

  private val channel =
    connection.createChannel()

  channel.basicQos(100)

  private val consumer =
    new DefaultConsumer(channel) {

      override def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
      ): Unit = {

        val payload =
          new String(
            body,
            StandardCharsets.UTF_8
          )

        try {

          pushService.push(payload)

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

            channel.basicNack(
              envelope.getDeliveryTag,
              false,
              false
            )
        }
      }
    }

  channel.basicConsume(
    rabbitConfig.queue,
    false,
    consumer
  )
}