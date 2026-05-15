package services

import play.api.Logging
import play.api.libs.ws.WSClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

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

    // Persist notification as soon as it is processed for outbound push.
    // Default status = published.
    notificationService
      .recordPublishedFromPayload(payload)
      .recover { case t =>
        logger.error("Failed to persist published notification", t)
      }

    // Example outbound API push
    // ws.url(endpoint).post(payload)
  }
}
