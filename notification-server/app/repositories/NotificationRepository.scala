package repositories

import Tables.NotificationTable
import models.db.NotificationRow
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val notifications =
    TableQuery[NotificationTable]

  private val insertWithId =
    (notifications returning notifications.map(_.id))
      .into { (row, id) => row.copy(id = Some(id)) }

  def insert(
      notification: NotificationRow
  ): Future[NotificationRow] = {
    run(insertWithId += notification)
  }

  def create(
      notificationId: String,
      userId: String,
      eventType: String,
      message: String,
      status: String,
      retryCount: Int = 0,
      createdAt: Instant = Instant.now()
  ): Future[NotificationRow] = {

    insert(
      NotificationRow(
        id = None,
        notificationId = notificationId,
        userId = userId,
        eventType = eventType,
        message = message,
        status = status,
        retryCount = retryCount,
        createdAt = createdAt
      )
    )
  }

  def findById(id: Long): Future[Option[NotificationRow]] = {
    run(
      notifications
        .filter(_.id === id)
        .result
        .headOption
    )
  }

  def findByNotificationId(
      notificationId: String
  ): Future[Option[NotificationRow]] = {
    run(
      notifications
        .filter(_.notificationId === notificationId)
        .result
        .headOption
    )
  }

  def list(
      userIdOpt: Option[String] = None,
      statusOpt: Option[String] = None,
      limit: Int = 50,
      offset: Int = 0
  ): Future[Seq[NotificationRow]] = {

    val base =
      notifications
        .sortBy(_.createdAt.desc)

    val withUser =
      userIdOpt match {
        case Some(userId) => base.filter(_.userId === userId)
        case None         => base
      }

    val withStatus =
      statusOpt match {
        case Some(status) => withUser.filter(_.status === status)
        case None         => withUser
      }

    val safeLimit = Math.max(1, Math.min(limit, 500))
    val safeOffset = Math.max(0, offset)

    run(withStatus.drop(safeOffset).take(safeLimit).result)
  }

  def updateStatus(id: Long, status: String): Future[Int] = {
    run(
      notifications
        .filter(_.id === id)
        .map(_.status)
        .update(status)
    )
  }

  def updateStatusByNotificationId(
      notificationId: String,
      status: String
  ): Future[Int] = {
    run(
      notifications
        .filter(_.notificationId === notificationId)
        .map(_.status)
        .update(status)
    )
  }
}
