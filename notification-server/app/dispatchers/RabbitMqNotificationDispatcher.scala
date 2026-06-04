package dispatchers

import com.rabbitmq.client._

import config.RabbitMqConfig

import messaging.RabbitMqTopology

import play.api.Logging
import play.api.inject.ApplicationLifecycle

import services.NotificationPushService

import java.nio.charset.StandardCharsets
import java.util.concurrent.{
  Executors,
  ScheduledExecutorService,
  ThreadFactory,
  TimeUnit
}

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

@Singleton
class RabbitMqNotificationDispatcher @Inject() (
    rabbitConfig: RabbitMqConfig,
    pushService: NotificationPushService,
    lifecycle: ApplicationLifecycle
) extends Logging {

  private val factory =
    new ConnectionFactory()

  factory.setHost(rabbitConfig.host)

  factory.setPort(rabbitConfig.port)
  factory.setUsername(rabbitConfig.username)
  factory.setPassword(rabbitConfig.password)

  factory.setAutomaticRecoveryEnabled(true)
  factory.setNetworkRecoveryInterval(5000)

  @volatile private var connection: Connection = _
  @volatile private var channel: Channel = _
  @volatile private var consumerTag: String = _

  private def closeResources(): Unit = {
    val ch = channel
    val conn = connection
    val tag = consumerTag

    try {
      if (ch != null && ch.isOpen && tag != null) {
        try ch.basicCancel(tag)
        catch {
          case _: Throwable => // ignore; channel may already be closing
        }
      }
    } catch {
      case t: Throwable => logger.warn("RabbitMQ consumer cancel failed", t)
    }

    try {
      if (ch != null && ch.isOpen) ch.close()
    } catch {
      case t: Throwable => logger.warn("RabbitMQ channel close failed", t)
    }

    try {
      if (conn != null && conn.isOpen) conn.close()
    } catch {
      case t: Throwable => logger.warn("RabbitMQ connection close failed", t)
    }

    consumerTag = null
    channel = null
    connection = null
  }

  private def startConsumerIfNeeded(): Unit = this.synchronized {
    if (channel != null && channel.isOpen) return

    try {
      val conn = factory.newConnection()
      val ch = conn.createChannel()

      RabbitMqTopology.initialize(
        ch,
        rabbitConfig
      )

      ch.basicQos(1)

      val consumer =
        new DefaultConsumer(ch) {

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

              ch.basicAck(
                envelope.getDeliveryTag,
                false
              )

            } catch {
              case t: Throwable =>
                logger.error(
                  "Notification push failed",
                  t
                )

                try {
                  ch.basicNack(
                    envelope.getDeliveryTag,
                    false,
                    false
                  )
                } catch {
                  case _: Throwable => // ignore; channel may already be closed
                }
            }
          }
        }

      val tag =
        ch.basicConsume(
          rabbitConfig.queue,
          false,
          consumer
        )

      connection = conn
      channel = ch
      consumerTag = tag

      logger.info(
        s"RabbitMQ dispatcher consuming from queue='${rabbitConfig.queue}' on ${rabbitConfig.host}:${rabbitConfig.port}"
      )
    } catch {
      case t: Throwable =>
        // Don't fail app startup if RabbitMQ isn't reachable.
        closeResources()
        logger.warn(
          s"RabbitMQ dispatcher could not connect to ${rabbitConfig.host}:${rabbitConfig.port}; will retry",
          t
        )
    }
  }

  private val schedulerThreadFactory: ThreadFactory = new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "rabbitmq-dispatcher-connector")
      t.setDaemon(true)
      t
    }
  }

  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(schedulerThreadFactory)

  // Kick off connection attempts in the background (immediate, then every 5s).
  scheduler.scheduleWithFixedDelay(
    new Runnable {
      override def run(): Unit = {
        try startConsumerIfNeeded()
        catch {
          case t: Throwable =>
            logger.warn("RabbitMQ dispatcher start loop failed", t)
        }
      }
    },
    0,
    5,
    TimeUnit.SECONDS
  )

  lifecycle.addStopHook { () =>
    try scheduler.shutdownNow()
    catch {
      case t: Throwable =>
        logger.warn("RabbitMQ dispatcher scheduler shutdown failed", t)
    }

    closeResources()

    Future.successful(())
  }
}
