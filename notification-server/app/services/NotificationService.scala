package services

import models.NotificationMessage
import models.db.NotificationRow
import play.api.Logging
import play.api.libs.json.Json
import repositories.NotificationRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationService @Inject() (
    repo: NotificationRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  import NotificationService._

  def recordPublishedFromPayload(
      payload: String
  ): Future[NotificationRow] = {

    logger.info(
      s"Processing notification payload=$payload"
    )

    val parsed: Either[Throwable, NotificationMessage] =
      try {
        Right(
          Json.parse(payload).as[NotificationMessage]
        )
      } catch {
        case t: Throwable =>
          Left(t)
      }

    parsed match {

      case Right(msg) =>

        logger.info(
          s"""
             |Parsed notification:
             |notificationId=${msg.notificationId}
             |userId=${msg.userId}
             |eventType=${msg.eventType}
           """.stripMargin
        )

        val createdAt =
          parseInstantOrNow(msg.createdAt)

        repo
          .create(
            notificationId = msg.notificationId,
            userId = msg.userId,
            eventType = msg.eventType,
            message = msg.message,
            status = Status.Published,
            createdAt = createdAt
          )
          .map { insertedRow =>
            logger.info(
              s"""
                 |Notification persisted successfully:
                 |dbId=${insertedRow.id.getOrElse(-1)}
                 |notificationId=${insertedRow.notificationId}
               """.stripMargin
            )

            insertedRow
          }
          .recoverWith { case t =>

            logger.error(
              s"""
                 |Failed to persist notification:
                 |notificationId=${msg.notificationId}
                 |payload=$payload
               """.stripMargin,
              t
            )

            Future.failed(t)
          }

      case Left(t) =>

        logger.error(
          s"""
             |Invalid NotificationMessage JSON.
             |payload=$payload
           """.stripMargin,
          t
        )

        Future.failed(
          new IllegalArgumentException(
            "Invalid NotificationMessage JSON payload",
            t
          )
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
      normalizeStatus(
        statusOpt.getOrElse(Status.Published)
      ).getOrElse(Status.Published)

    logger.info(
      s"""
         |Creating notification manually:
         |notificationId=$notificationId
         |userId=$userId
         |status=$status
       """.stripMargin
    )

    repo
      .create(
        notificationId = notificationId,
        userId = userId,
        eventType = eventType,
        message = message,
        status = status
      )
      .map { row =>
        logger.info(
          s"""
             |Manual notification persisted:
             |dbId=${row.id.getOrElse(-1)}
             |notificationId=${row.notificationId}
           """.stripMargin
        )

        row
      }
      .recoverWith { case t =>

        logger.error(
          s"""
             |Failed manual notification creation:
             |notificationId=$notificationId
           """.stripMargin,
          t
        )

        Future.failed(t)
      }
  }

  def list(
      userIdOpt: Option[String],
      statusOpt: Option[String],
      limit: Int,
      offset: Int
  ): Future[Seq[NotificationRow]] = {

    val normalizedStatusOpt =
      statusOpt.flatMap(normalizeStatus)

    logger.info(
      s"""
         |Listing notifications:
         |userId=$userIdOpt
         |status=$normalizedStatusOpt
         |limit=$limit
         |offset=$offset
       """.stripMargin
    )

    repo.list(
      userIdOpt,
      normalizedStatusOpt,
      limit,
      offset
    )
  }

  def get(
      id: Long
  ): Future[Option[NotificationRow]] = {

    logger.info(
      s"Fetching notification id=$id"
    )

    repo.findById(id)
  }

  def updateStatus(
      id: Long,
      newStatus: String
  ): Future[Boolean] = {

    logger.info(
      s"""
         |Updating notification status:
         |id=$id
         |newStatus=$newStatus
       """.stripMargin
    )

    normalizeStatus(newStatus) match {

      case None =>

        logger.warn(
          s"Invalid notification status=$newStatus"
        )

        Future.successful(false)

      case Some(status) =>

        repo
          .updateStatus(id, status)
          .map { updatedRows =>
            val success =
              updatedRows > 0

            if (success) {
              logger.info(
                s"""
                   |Notification status updated:
                   |id=$id
                   |status=$status
                 """.stripMargin
              )
            } else {
              logger.warn(
                s"No notification found for id=$id"
              )
            }

            success
          }
          .recoverWith { case t =>

            logger.error(
              s"""
                 |Failed updating notification status:
                 |id=$id
                 |status=$status
               """.stripMargin,
              t
            )

            Future.failed(t)
          }
    }
  }
}

object NotificationService {

  object Status {

    val Published = "published"

    val Ignored = "ignored"

    val Clicked = "clicked"

    val Allowed: Set[String] =
      Set(
        Published,
        Ignored,
        Clicked
      )
  }

  def normalizeStatus(
      raw: String
  ): Option[String] = {

    // Business constraint: only a strict set of statuses is accepted —
    // normalize and validate to enforce domain rules.
    val normalized =
      Option(raw)
        .map(_.trim.toLowerCase)
        .getOrElse("")

    if (Status.Allowed.contains(normalized))
      Some(normalized)
    else
      None
  }

  def parseInstantOrNow(
      raw: String
  ): Instant = {

    try {
      Instant.parse(raw)
    } catch {

      case _: Throwable =>
        Instant.now()
    }
  }
}
