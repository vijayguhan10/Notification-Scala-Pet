package models.db
import java.time.Instant
case class UserActivityEventRow(
    eventId: String,
    userId: String,
    sessionId: String,
    eventType: String,
    page: String,
    timestamp: Instant,
    device: String,
    browser: String,
    scrollDepth: Int,
    location: String
)
