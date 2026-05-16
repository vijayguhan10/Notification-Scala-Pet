package models

import play.api.libs.json.{Json, OFormat}

final case class UserActivityEvent(
    eventId: String,
    userId: String,
    sessionId: String,
    eventType: String,
    page: String,
    timestamp: String,
    device: String,
    browser: String,
    scrollDepth: Int,
    location: String
)


object UserActivityEvent {
  implicit val format: OFormat[UserActivityEvent] =
    Json.format[UserActivityEvent]
}

