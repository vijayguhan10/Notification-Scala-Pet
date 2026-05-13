package modules

import com.google.inject.AbstractModule
import consumers.KafkaDbConsumer
import startup.FlywayMigrator

class Module extends AbstractModule{
  override def configure(): Unit = {
    bind(classOf[FlywayMigrator]).asEagerSingleton()
    bind(classOf[KafkaDbConsumer]).asEagerSingleton()
  }
}
