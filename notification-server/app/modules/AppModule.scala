package modules

import com.google.inject.AbstractModule
import consumers.KafkaDbConsumer

final class AppModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[KafkaDbConsumer]).asEagerSingleton()
  }
}
