package models.db
import java.time.Instant
case class UserActivityEventRow(
    userId: String,
    parkingSearches: Int,
    slotViews: Int,
    bookingAttempts: Int,
    avgScrollDepth: Int,
    location: String,
    lastActivity: Instant,
    sessionDuration: Int
)
