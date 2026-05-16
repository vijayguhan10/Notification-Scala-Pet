package Tables

import models.db.UserActivityEventRow
import slick.jdbc.PostgresProfile.api._
import java.time.Instant

class UserActivityEventTable(tag: Tag)
    extends Table[UserActivityEventRow](tag, "user_activity_events") {
  def userId = column[String]("user_id")
  def parkingSearches = column[Int]("parking_searches")
  def slotViews = column[Int]("slot_views")
  def bookingAttempts = column[Int]("booking_attempts")
  def avgScrollDepth = column[Int]("avg_scroll_depth")
  def lastLocation = column[String]("last_location")
  def lastActivity = column[Instant]("last_activity")
  def sessionDuration = column[Int]("session_duration")

  def * = (
    userId,
    parkingSearches,
    slotViews,
    bookingAttempts,
    avgScrollDepth,
    lastLocation,
    lastActivity,
    sessionDuration
  ) <> (UserActivityEventRow.tupled, UserActivityEventRow.unapply)
}
