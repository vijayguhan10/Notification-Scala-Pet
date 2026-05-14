
package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class RabbitMqConfig @Inject()(
    config: Configuration
) {

  val host =
    config.get[String]("rabbitmq.host")

  val port =
    config.get[Int]("rabbitmq.port")

  val username =
    config.get[String]("rabbitmq.username")

  val password =
    config.get[String]("rabbitmq.password")

  val exchange =
    config.get[String]("rabbitmq.exchange")

  val queue =
    config.get[String]("rabbitmq.queue")

  val routingKey =
    config.get[String]("rabbitmq.routingKey")

  val dlq =
    config.get[String]("rabbitmq.dlq")
}