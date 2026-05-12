package modules

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}

/** Guice module enabled via `conf/application.conf`.
  *
  * Currently this module does not register bindings. It exists so Play can
  * successfully load the configured module and start the application.
  */
final class DatabaseModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  override def configure(): Unit = {
    ()
  }
}
