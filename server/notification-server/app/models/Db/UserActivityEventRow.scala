package models.Db
import java.time.Instant
case class UserActivityEventRow(
    eventId: String,
    userId: String,
    sessionId: String,
    eventType: String,
)