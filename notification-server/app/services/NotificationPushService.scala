
package services

import play.api.Logging
import play.api.libs.ws.WSClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class NotificationPushService @Inject()(
    ws: WSClient
)(implicit ec: ExecutionContext)
    extends Logging {

  def push(
      payload: String
  ): Unit = {

    logger.info(
      s"Pushing outbound notification=$payload"
    )

    // Example outbound API push
    // ws.url(endpoint).post(payload)
  }
}