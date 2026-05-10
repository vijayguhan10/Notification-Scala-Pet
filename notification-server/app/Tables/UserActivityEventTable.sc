package Tables

import models.Db.UserActivityEventRow
import slick.jdbc.PostgresProfile.api._
import java.time.Instant

class UserActivityEventTable(tag: Tag)
    extends Table[UserActivityEventRow](tag, "user_activity_events") {
  def eventId = column[String]("event_id", O.PrimaryKey)
  def userId = column[String]("user_id")
  def sessionId = column[String]("session_id")
  def eventType = column[String]("event_type")
  def page = column[String]("page")
  def timestamp = column[Instant]("timestamp")
  def device = column[String]("device")
  def browser = column[String]("browser")
  def scrollDepth = column[Int]("scroll_depth")
  def location = column[String]("location")

  def * = (
    eventId,
    userId,
    sessionId,
    eventType,
    page,
    timestamp,
    device,
    browser,
    scrollDepth,
    location
  ) <> (UserActivityEventRow.tupled, UserActivityEventRow.unapply)
}
