package controllers

import models.db.NotificationRow
import play.api.libs.json.{Format, JsString, Json, Reads, Writes}
import play.api.mvc.{BaseController, ControllerComponents}
import services.NotificationService

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationController @Inject() (
    val controllerComponents: ControllerComponents,
    notificationService: NotificationService
)(implicit ec: ExecutionContext)
    extends BaseController {

  private implicit val instantFormat: Format[Instant] =
    Format(
      Reads.StringReads.map(Instant.parse),
      Writes(i => JsString(i.toString))
    )

  private implicit val notificationRowFormat: Format[NotificationRow] =
    Json.format[NotificationRow]

  private case class CreateNotificationRequest(
      notificationId: Option[String],
      userId: String,
      eventType: String,
      message: String,
      status: Option[String]
  )
  private implicit val createNotificationRequestReads =
    Json.reads[CreateNotificationRequest]

  private case class UpdateStatusRequest(status: String)
  private implicit val updateStatusRequestReads =
    Json.reads[UpdateStatusRequest]

  def list() = Action.async { request =>
    val userIdOpt = request.getQueryString("userId")
    val statusOpt = request.getQueryString("status")
    val limit =
      request.getQueryString("limit").flatMap(_.toIntOption).getOrElse(50)
    val offset =
      request.getQueryString("offset").flatMap(_.toIntOption).getOrElse(0)

    notificationService
      .list(userIdOpt, statusOpt, limit, offset)
      .map(rows => Ok(Json.toJson(rows)))
  }

  def get(id: Long) = Action.async {
    notificationService.get(id).map {
      case Some(row) => Ok(Json.toJson(row))
      case None      => NotFound(Json.obj("status" -> "not_found", "id" -> id))
    }
  }

  def create() = Action.async(parse.json) { request =>
    request.body
      .validate[CreateNotificationRequest]
      .fold(
        errs =>
          Future.successful(
            BadRequest(
              Json.obj("status" -> "invalid_json", "errors" -> errs.toString)
            )
          ),
        body => {
          val notificationId =
            body.notificationId.getOrElse(UUID.randomUUID().toString)
          notificationService
            .create(
              notificationId = notificationId,
              userId = body.userId,
              eventType = body.eventType,
              message = body.message,
              statusOpt = body.status
            )
            .map(row => Created(Json.toJson(row)))
        }
      )
  }

  def updateStatus(id: Long) = Action.async(parse.json) { request =>
    request.body
      .validate[UpdateStatusRequest]
      .fold(
        errs =>
          Future.successful(
            BadRequest(
              Json.obj("status" -> "invalid_json", "errors" -> errs.toString)
            )
          ),
        body =>
          notificationService.updateStatus(id, body.status).map {
            case true =>
              Ok(
                Json.obj(
                  "status" -> "updated",
                  "id" -> id,
                  "newStatus" -> body.status
                )
              )
            case false =>
              BadRequest(
                Json.obj(
                  "status" -> "invalid_status_or_not_found",
                  "id" -> id,
                  "allowed" -> services.NotificationService.Status.Allowed.toSeq.sorted
                )
              )
          }
      )
  }
}
