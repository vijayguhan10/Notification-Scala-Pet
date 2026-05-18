package services

import javax.inject.{Inject, Singleton}

import models.NotificationMessage
import models.UserActivityEvent

import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class NotificationPushService @Inject() (
    ws: WSClient,
    notificationService: NotificationService
)(implicit ec: ExecutionContext)
    extends Logging {

  def push(
      payload: String
  ): Unit = {

    logger.info(
      s"NotificationPushService: processing payload=$payload"
    )

    Await.result(
      notificationService.recordPublishedFromPayload(payload),
      5.seconds
    )

    logger.info(
      "NotificationPushService: notification persisted successfully"
    )

    try {

      val json = Json.parse(payload)

      if (json.asOpt[UserActivityEvent].isDefined) {

        val event =
          json.as[UserActivityEvent]

        EmailPublisher.sendEventEmail(event)

        logger.info(
          s"NotificationPushService: activity email sent " +
            s"for user=${event.userId}"
        )

      } else if (
        json.asOpt[NotificationMessage].isDefined
      ) {

        val notif =
          json.as[NotificationMessage]

        EmailPublisher.sendNotificationEmail(notif)

        logger.info(
          s"NotificationPushService: notification email sent " +
            s"for user=${notif.userId}"
        )

      } else {

        logger.warn(
          "NotificationPushService: unknown payload shape, " +
            "skipping email"
        )
      }

    } catch {

      case ex: Throwable =>

        logger.error(
          "NotificationPushService: email delivery failed",
          ex
        )
    }
  }
}