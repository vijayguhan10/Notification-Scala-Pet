package services

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class KafkaPublisher @Inject()(
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  private val isClosed = new AtomicBoolean(false)

  private val bootstrapServers = config.get[String]("kafka.bootstrapServers")
  private val clientId = config.getOptional[String]("kafka.clientId").getOrElse("notification-server")

  private val props = new Properties()
  props.put("bootstrap.servers", bootstrapServers)
  props.put("client.id", clientId)
  props.put("acks", "1")
  props.put("linger.ms", "5")
  props.put("batch.size", "32768")
  props.put("key.serializer", classOf[StringSerializer].getName)
  props.put("value.serializer", classOf[StringSerializer].getName)

  private val producer = new KafkaProducer[String, String](props)

  lifecycle.addStopHook { () =>
    close()
    Future.successful(())
  }

  def publish(topic: String, key: String, value: String): Unit = {
    if (isClosed.get()) return

    try {
      val record = new ProducerRecord[String, String](topic, key, value)
      producer.send(
        record,
        (_, exception) => {
          if (exception != null) {
            logger.warn(s"Kafka publish failed (topic=$topic)", exception)
          }
        }
      )
    } catch {
      case t: Throwable =>
        logger.warn(s"Kafka publish threw (topic=$topic)", t)
    }
  }

  def close(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      try producer.close()
      catch {
        case t: Throwable => logger.warn("Kafka producer close failed", t)
      }
    }
  }
}
