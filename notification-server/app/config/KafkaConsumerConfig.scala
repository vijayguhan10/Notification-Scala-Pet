package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class KafkaConsumerConfig @Inject() (config: Configuration) {

  private val kafka =
    config.get[Configuration]("kafka")

  val bootstrapServers: String =
    kafka.get[String]("bootstrapServers")

  val topic: String =
    kafka.get[String]("topic")

  val clientId: String =
    kafka
      .getOptional[String]("clientId")
      .getOrElse("notification-server")
}
