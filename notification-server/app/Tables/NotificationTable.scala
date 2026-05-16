package Tables

import models.db.NotificationRow
import slick.jdbc.PostgresProfile.api._

import java.time.Instant

class NotificationTable(tag: Tag)
    extends Table[NotificationRow](tag, "notifications") {

  def id =
    column[Long]("id", O.PrimaryKey, O.AutoInc)

  def notificationId =
    column[String]("notification_id")

  def userId =
    column [String]("user_id")

  def eventType =
    column[String]("event_type")

  def message =
    column[String]("message")

  def status =
    column[String]("status")

  def retryCount =
    column[Int]("retry_count")

  def createdAt =
    column[Instant]("created_at")

  def * = (
    id.?,
    notificationId,
    userId,
    eventType,
    message,
    status,
    retryCount,
    createdAt
  ) <> (NotificationRow.tupled, NotificationRow.unapply)
}
