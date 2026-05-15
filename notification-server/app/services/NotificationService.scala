package services

import models.NotificationMessage
import models.db.NotificationRow
import play.api.Logging
import play.api.libs.json.Json
import repositories.NotificationRepository

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationService @Inject() (
    repo: NotificationRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  import NotificationService._

  def recordPublishedFromPayload(payload: String): Future[NotificationRow] = {
    val parsed: Either[Throwable, NotificationMessage] =
      try Right(Json.parse(payload).as[NotificationMessage])
      catch {
        case t: Throwable => Left(t)
      }

    parsed match {
      case Right(msg) =>
        repo.create(
          notificationId = msg.notificationId,
          userId = msg.userId,
          eventType = msg.eventType,
          message = msg.message,
          status = Status.Published,
          createdAt = parseInstantOrNow(msg.createdAt)
        )

      case Left(t) =>
        logger.warn(
          s"Failed to parse NotificationMessage JSON; recording as ignored. payload=$payload",
          t
        )
        repo.create(
          notificationId = UUID.randomUUID().toString,
          userId = "unknown",
          eventType = "unknown",
          message = payload,
          status = Status.Ignored,
          createdAt = Instant.now()
        )
    }
  }

  def create(
      notificationId: String,
      userId: String,
      eventType: String,
      message: String,
      statusOpt: Option[String] = None
  ): Future[NotificationRow] = {

    val status =
      normalizeStatus(statusOpt.getOrElse(Status.Published))
        .getOrElse(Status.Published)

    repo.create(
      notificationId = notificationId,
      userId = userId,
      eventType = eventType,
      message = message,
      status = status
    )
  }

  def list(
      userIdOpt: Option[String],
      statusOpt: Option[String],
      limit: Int,
      offset: Int
  ): Future[Seq[NotificationRow]] = {
    val normalizedStatusOpt =
      statusOpt.flatMap(normalizeStatus)
    repo.list(userIdOpt, normalizedStatusOpt, limit, offset)
  }

  def get(id: Long): Future[Option[NotificationRow]] =
    repo.findById(id)

  def updateStatus(id: Long, newStatus: String): Future[Boolean] = {
    normalizeStatus(newStatus) match {
      case None =>
        Future.successful(false)
      case Some(status) =>
        repo.updateStatus(id, status).map(_ > 0)
    }
  }
}

object NotificationService {

  object Status {
    val Published = "published"
    val Ignored = "ignored"
    val Clicked = "clicked"

    val Allowed: Set[String] =
      Set(Published, Ignored, Clicked)
  }

  def normalizeStatus(raw: String): Option[String] = {
    val normalized = Option(raw).map(_.trim.toLowerCase).getOrElse("")
    if (Status.Allowed.contains(normalized)) Some(normalized)
    else None
  }

  def parseInstantOrNow(raw: String): Instant = {
    try Instant.parse(raw)
    catch {
      case _: Throwable => Instant.now()
    }
  }
}
