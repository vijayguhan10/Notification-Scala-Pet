
package models

import play.api.libs.json.{Json, OFormat}

case class NotificationMessage(
    notificationId: String,
    userId: String,
    eventType: String,
    message: String,
    createdAt: String
)

object NotificationMessage {

  implicit val format: OFormat[NotificationMessage] =
    Json.format[NotificationMessage]
}