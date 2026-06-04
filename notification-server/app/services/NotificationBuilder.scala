package services

import models._

import java.time.Instant
import java.util.UUID
import javax.inject.Singleton

@Singleton
class NotificationBuilder {

  private def normalizeLevel(level: String): String = {
    level.trim.toLowerCase match {
      case "moderate" => "medium"
      case other      => other
    }
  }

  def build(
      event: UserActivityEvent,
      intentScore: Int,
      intentCategory: String,
      dynamicNotificationOpt: Option[DynamicNotification] = None
  ): NotificationMessage = {

    val levelForEventType =
      dynamicNotificationOpt
        .map(_.priority)
        .getOrElse(intentCategory)

    val normalizedLevel =
      normalizeLevel(levelForEventType)

    val realtimeMessage =
      dynamicNotificationOpt
        .map(_.message)
        .getOrElse(generateRealtimeMessage(normalizedLevel, event))

    NotificationMessage(
      notificationId = UUID.randomUUID().toString,

      userId = event.userId,

      eventType = s"INTENT_${normalizedLevel.toUpperCase}",

      message = realtimeMessage,

      createdAt = Instant.now().toString
    )
  }

  private def generateRealtimeMessage(
      intentLevel: String,
      event: UserActivityEvent
  ): String = {
    intentLevel match {

      case "low" =>
        s"Parking spaces available near ${event.location}. Park anytime."

      case "medium" =>
        s"Parking demand increasing near ${event.location}. Slots may fill soon."

      case "high" =>
        s"Hurry! Very few parking slots left near ${event.location}."

      case "immediate" =>
        s"Critical parking alert near ${event.location}. Last slots remaining. Reach immediately."

      case _ =>
        s"Critical parking alert near ${event.location}. Last slots remaining. Reach immediately."
    }
  }
}
