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

  @volatile private var connection: Connection = _
  @volatile private var channel: Channel = _

  private def closeResources(): Unit = {
    val ch = channel
    val conn = connection

    try {
      if (ch != null && ch.isOpen) ch.close()
    } catch {
      case t: Throwable =>
        logger.warn("RabbitMQ publisher channel close failed", t)
    }

    try {
      if (conn != null && conn.isOpen) conn.close()
    } catch {
      case t: Throwable =>
        logger.warn("RabbitMQ publisher connection close failed", t)
    }

    channel = null
    connection = null
  }

  private def ensureChannel(): Channel = this.synchronized {
    if (channel != null && channel.isOpen) return channel

    val conn = factory.newConnection()
    val ch = conn.createChannel()

    RabbitMqTopology.initialize(
      ch,
      config
    )

    connection = conn
    channel = ch
    ch
  }

  lifecycle.addStopHook { () =>
    closeResources()

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

    try {
      val ch = ensureChannel()
      ch.basicPublish(
        config.exchange,
        routingKey,
        props,
        payload
      )
    } catch {
      case t: Throwable =>
        // Clear broken connection/channel so the next publish can retry.
        closeResources()
        throw t
    }
  }
}
