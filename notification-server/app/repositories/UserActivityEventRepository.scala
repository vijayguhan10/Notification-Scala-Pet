package repositories

import Tables.UserActivityEventTable
import models.db.UserActivityEventRow
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.SQLActionBuilder
import java.sql.Timestamp

@Singleton
class UserActivityEventRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val userActivityEvents =
    TableQuery[UserActivityEventTable]

  def insertBatch(
      events: Seq[UserActivityEventRow]
  ): Future[Option[Int]] = {
    run(userActivityEvents ++= events)
  }

  /** Returns event counts grouped by hour between start and end (inclusive).
    * Result rows are (hourIsoString, count) ordered by count desc.
    */
  def countByHour(start: Instant, end: Instant): Future[Seq[(String, Int)]] = {
    implicit val getResult: GetResult[(String, Int)] = GetResult { r =>
      val hour = r.nextString()
      val count = r.nextInt()
      (hour, count)
    }

    val startTs = Timestamp.from(start)
    val endTs = Timestamp.from(end)

    val q = sql"""
      SELECT to_char(date_trunc('hour', last_activity), 'YYYY-MM-DD"T"HH24:00:00') AS hour,
             count(*) AS cnt
      FROM user_activity_events
      WHERE last_activity >= $startTs AND last_activity <= $endTs
      GROUP BY hour
      ORDER BY cnt DESC
    """.as[(String, Int)]

    run(q)
  }
}
