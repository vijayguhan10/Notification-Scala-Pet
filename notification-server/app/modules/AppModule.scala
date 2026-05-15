package modules

import com.google.inject.AbstractModule
import consumers.KafkaDbConsumer
import consumers._
import dispatchers._

final class AppModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[KafkaDbConsumer]).asEagerSingleton()
     bind(classOf[KafkaNotificationConsumer])
      .asEagerSingleton()

    bind(classOf[RabbitMqNotificationDispatcher])
      .asEagerSingleton()
  }
}
