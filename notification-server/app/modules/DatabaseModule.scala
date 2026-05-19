package modules

import org.flywaydb.core.Flyway
import play.api.{Configuration, Environment, Logging, Mode}
import play.api.inject._

import java.io.File

import javax.inject._

class DatabaseModule
    extends SimpleModule(
      bind[FlywayRunner].toSelf.eagerly() // run on startup
    )

@Singleton
class FlywayRunner @Inject() (
    config: Configuration,
    environment: Environment
) extends Logging {

  private val dbUrl: String =
    config
      .getOptional[String]("flyway.url")
      .getOrElse(config.get[String]("slick.dbs.default.db.url"))

  private val dbUser: String =
    config
      .getOptional[String]("flyway.user")
      .getOrElse(config.get[String]("slick.dbs.default.db.user"))

  private val dbPassword: String =
    config
      .getOptional[String]("flyway.password")
      .getOrElse(config.get[String]("slick.dbs.default.db.password"))

  private val locations: Seq[String] =
    config
      .getOptional[Seq[String]]("flyway.locations")
      .getOrElse(Seq("classpath:db/migration"))

  private val cleanOnStart: Boolean =
    config
      .getOptional[Boolean]("flyway.cleanOnStart")
      .getOrElse(false)

  private def resolveLocation(location: String): String = {
    val prefix = "filesystem:"
    if (!location.startsWith(prefix)) return location

    val rawPath = location.stripPrefix(prefix)
    val file = new File(rawPath)
    val resolved =
      if (file.isAbsolute) file
      else new File(environment.rootPath, rawPath)

    prefix + resolved.getAbsolutePath
  }

  private val resolvedLocations = locations.map(resolveLocation)

  logger.info(
    s"[startup] Flyway migrate (url=$dbUrl, locations=${resolvedLocations.mkString(",")})"
  )
  println(
    s"[startup] Flyway migrate (locations=${resolvedLocations.mkString(",")})"
  )

  private val flywayConfig = {
    val base =
      Flyway
        .configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .locations(resolvedLocations: _*)

    if (cleanOnStart && environment.mode == Mode.Dev) base.cleanDisabled(false)
    else base
  }

  private val flyway = flywayConfig.load()

  if (cleanOnStart) {
    if (environment.mode == Mode.Dev) {
      logger.warn(
        "[startup] Flyway cleanOnStart=true (DEV ONLY) — cleaning schema before migrate"
      )
      println(
        "[startup] Flyway cleanOnStart=true (DEV ONLY) — cleaning schema before migrate"
      )
      flyway.clean()
    } else {
      logger.warn(
        "[startup] Flyway cleanOnStart=true ignored (not in DEV mode)"
      )
      println("[startup] Flyway cleanOnStart=true ignored (not in DEV mode)")
    }
  }

  private val result = flyway.migrate()

  logger.info(
    s"[startup] Flyway done (migrationsExecuted=${result.migrationsExecuted})"
  )
  println(
    s"[startup] Flyway done (migrationsExecuted=${result.migrationsExecuted})"
  )
}
