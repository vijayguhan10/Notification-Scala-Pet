package messaging

import com.rabbitmq.client.Channel

import config.RabbitMqConfig

object RabbitMqTopology {

  def initialize(
      channel: Channel,
      config: RabbitMqConfig
  ): Unit = {


    channel.exchangeDeclare(
      config.exchange,
      "direct",
      true
    )


    channel.queueDeclare(
      config.dlq,
      true,
      false,
      false,
      null
    )


    val args =
      new java.util.HashMap[String, Object]()


    args.put(
      "x-dead-letter-exchange",
      ""
    )

    args.put(
      "x-dead-letter-routing-key",
      config.dlq
    )


    channel.queueDeclare(
      config.queue,
      true,
      false,
      false,
      args
    )


    val delayArgs =
      new java.util.HashMap[String, Object]()

    delayArgs.put(
      "x-dead-letter-exchange",
      config.exchange
    )

    delayArgs.put(
      "x-dead-letter-routing-key",
      config.routingKey
    )

    channel.queueDeclare(
      config.delayQueue,
      true,
      false,
      false,
      delayArgs
    )


    channel.queueBind(
      config.queue,
      config.exchange,
      config.routingKey
    )

    channel.queueBind(
      config.delayQueue,
      config.exchange,
      config.delayRoutingKey
    )
  }
}
