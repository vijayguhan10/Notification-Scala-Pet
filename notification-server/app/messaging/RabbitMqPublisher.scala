package messaging

import com.rabbitmq.client._
import config.RabbitMqConfig
import models.NotificationMessage
import play.api.Logging
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}

@Singleton
class RabbitMqPublisher @Inject()(
    config: RabbitMqConfig
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

  def publish(
      notification: NotificationMessage
  ): Unit = {

    val payload =
      Json.toJson(notification)
        .toString()
        .getBytes()

    channel.basicPublish(
      config.exchange,
      config.routingKey,
      MessageProperties.PERSISTENT_TEXT_PLAIN,
      payload
    )
  }
}