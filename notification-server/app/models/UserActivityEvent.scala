package models

import play.api.libs.json.{Json, OFormat}

final case class UserActivityEvent(
  userId: String,
  parkingSearches: Int,
  slotViews: Int,
  bookingAttempts: Int,
  avgScrollDepth: Int,
  location: String,
  lastActivity: String,
  sessionDuration: Int
)

object UserActivityEvent {
  implicit val format: OFormat[UserActivityEvent] =
    Json.format[UserActivityEvent]
}
