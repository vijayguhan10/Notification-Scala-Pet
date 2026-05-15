package messaging

// RabbitMQ communication channel.
// A Channel is a lightweight virtual connection
// used to interact with RabbitMQ.
// Through this we can:
// - create exchanges
// - create queues
// - publish messages
// - consume messages
import com.rabbitmq.client.Channel

// Custom configuration object.
// Usually contains:
// - exchange name
// - queue name
// - routing key
// - dead letter queue name
import config.RabbitMqConfig

// Scala `object` = singleton.
// Only one instance exists.
// Used here because topology setup is utility logic.
object RabbitMqTopology {

  // initialize() sets up the complete RabbitMQ infrastructure.
  //
  // This method creates:
  // 1. Exchange
  // 2. Main Queue
  // 3. Dead Letter Queue (DLQ)
  // 4. Binding between exchange and queue
  //
  // Parameters:
  // channel -> communication session with RabbitMQ
  // config  -> all RabbitMQ names/settings
  def initialize(
      channel: Channel,
      config: RabbitMqConfig
  ): Unit = {

    // ------------------------------------------------------------
    // CREATE EXCHANGE
    // ------------------------------------------------------------

    // Creates an exchange inside RabbitMQ.
    //
    // Exchange responsibility:
    // Receives messages from producers
    // and routes them to queues.
    //
    // Exchange Type: "direct"
    // Meaning:
    // Route messages using exact routing-key matching.
    //
    // durable = true
    // Exchange survives RabbitMQ restart.
    channel.exchangeDeclare(
      config.exchange,
      "direct",
      true
    )

    // ------------------------------------------------------------
    // CREATE DEAD LETTER QUEUE (DLQ)
    // ------------------------------------------------------------

    // Creates a DLQ.
    //
    // DLQ stores failed messages.
    //
    // Messages reach DLQ when:
    // - consumer rejects message
    // - message expires
    // - processing repeatedly fails
    //
    // Parameters:
    //
    // config.dlq -> queue name
    // true       -> durable queue
    // false      -> not exclusive
    // false      -> do not auto delete
    // null       -> no extra arguments
    channel.queueDeclare(
      config.dlq,
      true,
      false,
      false,
      null
    )

    // ------------------------------------------------------------
    // CREATE QUEUE ARGUMENTS MAP
    // ------------------------------------------------------------

    // HashMap used to store RabbitMQ queue settings.
    //
    // RabbitMQ expects Java Map<String, Object>
    // for advanced queue configuration.
    val args =
      new java.util.HashMap[String, Object]()

    // ------------------------------------------------------------
    // DEAD LETTER EXCHANGE CONFIGURATION
    // ------------------------------------------------------------

    // Defines where failed messages should go.
    //
    // "" = RabbitMQ default exchange.
    //
    // Default exchange automatically routes:
    //
    // routingKey == queueName
    //
    // So failed messages will be routed
    // using the routing key below.
    args.put(
      "x-dead-letter-exchange",
      ""
    )

    // Routing key used when message fails.
    //
    // Example:
    // config.dlq = "notification.dlq"
    //
    // RabbitMQ internally does:
    //
    // exchange = ""
    // routingKey = "notification.dlq"
    //
    // Then default exchange routes message
    // directly to notification.dlq queue.
    args.put(
      "x-dead-letter-routing-key",
      config.dlq
    )

    // ------------------------------------------------------------
    // CREATE MAIN QUEUE
    // ------------------------------------------------------------

    // Creates the main processing queue.
    //
    // This queue receives normal messages.
    //
    // Important:
    // This queue now contains DLQ configuration.
    //
    // Meaning:
    // If messages fail here,
    // RabbitMQ automatically moves them
    // to the DLQ.
    channel.queueDeclare(
      config.queue,
      true,
      false,
      false,
      args
    )

    // ------------------------------------------------------------
    // BIND QUEUE TO EXCHANGE
    // ------------------------------------------------------------

    // Creates routing rule between:
    //
    // Exchange -> Queue
    //
    // using routing key.
    //
    // Example:
    //
    // exchange   = notification.exchange
    // routingKey = user.created
    // queue      = notification.queue
    //
    // Meaning:
    //
    // If exchange receives message with:
    // routingKey = user.created
    //
    // then send message to:
    // notification.queue
    channel.queueBind(
      config.queue,
      config.exchange,
      config.routingKey
    )
  }
}