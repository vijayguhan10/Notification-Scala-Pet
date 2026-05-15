
package services

import models._

import java.time.Instant
import java.util.UUID
import javax.inject.Singleton

@Singleton
class NotificationBuilder {

  def build(
      event: UserActivityEvent
  ): NotificationMessage = {

    NotificationMessage(
      notificationId =
        UUID.randomUUID().toString,

      userId = event.userId,

      eventType = event.eventType,

      message =
        s"User ${event.userId} performed ${event.eventType}",

      createdAt = Instant.now().toString
    )
  }
}