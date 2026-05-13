package repositories

import Tables.UserActivityEventTable
import models.db.UserActivityEventRow
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserActivityEventRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends Logging {

  private val db =
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

  private val userActivityEvents =
    TableQuery[UserActivityEventTable]

  def insertBatch(
      events: Seq[UserActivityEventRow]
  ): Future[Option[Int]] = {
    db.run(userActivityEvents ++= events)
  }
}
