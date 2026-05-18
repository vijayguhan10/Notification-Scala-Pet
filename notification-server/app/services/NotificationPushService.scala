package services

import play.api.Logging
import play.api.libs.ws.WSClient
import play.api.libs.json.Json
import models.UserActivityEvent
import models.NotificationMessage
import services.EmailPublisher

import javax.inject.{Inject, Singleton}
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
    println(s"Pushing outbound notification=$payload");

    logger.info(
      s"Pushing outbound notification=$payload"
    )

    // Try to send an email notification BEFORE persisting so the email
    // is generated from the incoming payload and any failures can be DLQ'd
    // by the RabbitMQ consumer behavior.
    try {
      val json = Json.parse(payload)
      // Try parsing full UserActivityEvent first (when publisher sends raw event)
      if (json.asOpt[UserActivityEvent].isDefined) {
        val event = json.as[UserActivityEvent]
        EmailPublisher.sendEventEmail(event)
      } else if (json.asOpt[NotificationMessage].isDefined) {
        // Fallback: payload is a NotificationMessage (most common)
        val notif = json.as[NotificationMessage]
        EmailPublisher.sendNotificationEmail(notif)
      } else {
        logger.warn("EmailPublisher: unknown payload shape, skipping email")
      }
    } catch {
      case ex: Throwable =>
        logger.warn("EmailPublisher failed for outbound payload", ex)
    }

    // Persist notification before ACK so failures can DLQ the message.
    // Default status = published.
    // NOTE: This blocks the RabbitMQ consumer thread intentionally so
    // ACK/NACK is aligned with DB persistence.
    Await.result(
      notificationService.recordPublishedFromPayload(payload),
      5.seconds
    )

    // Example outbound API push
    // ws.url(endpoint).post(payload)
  }
}
