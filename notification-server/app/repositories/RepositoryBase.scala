package repositories

import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

abstract class RepositoryBase @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  protected val db =
    Database.forConfig(
      "slick.dbs.default.db",
      config.underlying
    )

  lifecycle.addStopHook { () =>
    try db.close()
    catch {
      case t: Throwable => logger.warn("DB close failed", t)
    }
    Future.successful(())
  }

  protected def run[R, E <: Effect](
      action: DBIOAction[R, NoStream, E]
  ): Future[R] =
    db.run(action)
}
