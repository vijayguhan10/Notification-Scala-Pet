package models.db

import java.time.Instant

case class NotificationRow(
    id: Option[Long] = None,
    notificationId: String,
    userId: String,
    eventType: String,
    message: String,
    status: String,
    retryCount: Int = 0,
    createdAt: Instant = Instant.now()
)
