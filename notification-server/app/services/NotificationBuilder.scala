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

    NotificationMessage(
      notificationId = UUID.randomUUID().toString,

      userId = event.userId,

      eventType = s"INTENT_${intentCategory.toUpperCase}",

      message =
        s"Intent score=$intentScore (${intentCategory}) for user=${event.userId}",

      createdAt = Instant.now().toString
    )
  }
}
