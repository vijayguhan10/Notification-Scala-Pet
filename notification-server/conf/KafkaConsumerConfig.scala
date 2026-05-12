package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class KafkaConsumerConfig @Inject()(config: Configuration) {

  val bootstrapServers: String =
    config.get[String]("kafka.bootstrapServers")

  val topic: String =
    config.get[String]("kafka.topic")

  val groupId: String =
    config.get[String]("kafka.consumer.groupId")

  val batchSize: Int =
    config.get[Int]("consumer.batchSize")

  val pollTimeoutMillis: Int =
    config.get[Int]("consumer.pollTimeoutMillis")
}