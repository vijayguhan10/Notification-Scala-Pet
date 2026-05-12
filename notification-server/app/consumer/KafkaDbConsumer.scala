package consumers

import config.KafkaConsumerConfig
import models.UserActivityEvent
import models.db.UserActivityEventRow
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import repositories.UserActivityEventRepository

import java.time.{Duration, Instant}
import java.util.{Collections, Properties}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._