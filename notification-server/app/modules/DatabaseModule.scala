package modules

import javax.inject._
import play.api.inject._
import org.flywaydb.core.Flyway
import com.typesafe.config.Config

// Module to register FlywayRunner
class DatabaseModule
    extends SimpleModule(
      bind[FlywayRunner].toSelf.eagerly() // run on startup
    )

// Runner class
@Singleton
class FlywayRunner @Inject() (config: Config) {

  private def getStringOpt(path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path)) else None

  private val dbUrl: String =
    getStringOpt("flyway.url").getOrElse(
      config.getString("slick.dbs.default.db.url")
    )

  private val dbUser: String =
    getStringOpt("flyway.user").getOrElse(
      config.getString("slick.dbs.default.db.user")
    )

  private val dbPassword: String =
    getStringOpt("flyway.password").getOrElse(
      config.getString("slick.dbs.default.db.password")
    )

  private val locations: String =
    getStringOpt("flyway.locations").getOrElse("classpath:db/migration")

  // Build Flyway instance using DB config
  private val flyway: Flyway = Flyway
    .configure()
    .dataSource(dbUrl, dbUser, dbPassword)
    .locations(locations)
    .load()

  // Run migrations when class is initialized
  flyway.migrate()
}
