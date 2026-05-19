package messaging

import com.rabbitmq.client._
import config.RabbitMqConfig
import models.NotificationMessage
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

@Singleton
class RabbitMqPublisher @Inject() (
  config: RabbitMqConfig,
  lifecycle: ApplicationLifecycle
) extends Logging {

  private val factory =
    new ConnectionFactory()

  factory.setHost(config.host)
  factory.setPort(config.port)
  factory.setUsername(config.username)
  factory.setPassword(config.password)

  private val connection =
    factory.newConnection()

  private val channel =
    connection.createChannel()

  RabbitMqTopology.initialize(
    channel,
    config
  )

  lifecycle.addStopHook { () =>
    try {
      if (channel.isOpen) channel.close()
    } catch {
      case t: Throwable => logger.warn("RabbitMQ publisher channel close failed", t)
    }

    try {
      if (connection.isOpen) connection.close()
    } catch {
      case t: Throwable => logger.warn("RabbitMQ publisher connection close failed", t)
    }

    Future.successful(())
  }

  def publish(
      notification: NotificationMessage,
      delayMs: Long
  ): Unit = {

    val payload =
      Json
        .toJson(notification)
        .toString()
        .getBytes()

    val propsBuilder =
      new AMQP.BasicProperties.Builder()
        .contentType("application/json")
        .deliveryMode(2)

    val (routingKey, props) =
      if (delayMs > 0) {
        (
          config.delayRoutingKey,
          propsBuilder.expiration(delayMs.toString).build()
        )
      } else {
        (
          config.routingKey,
          propsBuilder.build()
        )
      }

    channel.basicPublish(
      config.exchange,
      routingKey,
      props,
      payload
    )
  }
}
