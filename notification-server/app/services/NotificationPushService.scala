package services

import play.api.Logging
import play.api.libs.ws.WSClient

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
