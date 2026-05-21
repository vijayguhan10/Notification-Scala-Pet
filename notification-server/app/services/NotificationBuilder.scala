package services

import models._

import java.time.Instant
import java.util.UUID
import javax.inject.Singleton

@Singleton
class NotificationBuilder {

  def build(
      event: UserActivityEvent,
      intentScore: Int,
      intentCategory: String
  ): NotificationMessage = {

    val realtimeMessage =
      generateRealtimeMessage(intentScore, event)

    NotificationMessage(
      notificationId = UUID.randomUUID().toString,

      userId = event.userId,

      eventType = s"INTENT_${intentCategory.toUpperCase}",

      message = realtimeMessage,

      createdAt = Instant.now().toString
    )
  }

  private def generateRealtimeMessage(
      score: Int,
      event: UserActivityEvent
  ): String = {

    
    score match {
     
      case s if s >= 0 && s < 30 =>
        s"Parking spaces available near ${event.location}. Park anytime."

      case s if s >= 30 && s < 60 =>
        s"Parking demand increasing near ${event.location}. Slots may fill soon."

      case s if s >= 60 && s < 80 =>
        s"Hurry! Very few parking slots left near ${event.location}."

      case _ =>
        s"Critical parking alert near ${event.location}. Last slots remaining. Reach immediately."
    }
  }
}