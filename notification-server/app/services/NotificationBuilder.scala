package services

import models._

import java.time.Instant
import java.util.UUID
import javax.inject.Singleton

@Singleton
class NotificationBuilder {

  def build(
      event: UserActivityEvent,
      intent: IntentScoreResult
  ): NotificationMessage = {

    // Event type drives EmailPublisher badge mapping.
    // We prefer priority (IMMEDIATE/HIGH/MEDIUM/LOW) over category.
    val normalizedLevel = {
      val p = intent.notification.priority.trim.toUpperCase
      p match {
        case "IMMEDIATE" => "immediate"
        case "HIGH"      => "high"
        case "MEDIUM"    => "medium"
        case "MODERATE"  => "medium"
        case "LOW"       => "low"
        case _           => intent.category.trim.toLowerCase
      }
    }

    NotificationMessage(
      notificationId = UUID.randomUUID().toString,

      userId = event.userId,

      eventType = s"INTENT_${normalizedLevel.toUpperCase}",

      message = intent.notification.message,

      createdAt = Instant.now().toString
    )
  }
}
