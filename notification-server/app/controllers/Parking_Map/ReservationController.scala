package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.ReservationClass
import services._

@Singleton
class ReservationClassController @Inject() (
    val controllerComponents: ControllerComponents,
    service: ReservationService
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val format: OFormat[ReservationClass] = Json.format[ReservationClass]

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ReservationClass] match {
      case JsSuccess(rc, _) =>
        service.create(rc).map(res => Created(Json.toJson(res))).recover {
          case ex: InvalidReservationClassException =>
            BadRequest(
              Json.obj("status" -> "Error", "message" -> ex.getMessage)
            )
          case ex: Throwable =>
            println("\n  [DB EXCEPTION IN CREATE RESERVATION CLASS]  ")
            ex.printStackTrace()
            InternalServerError(
              Json.obj(
                "status" -> "Error",
                "message" -> "Database error.",
                "rawError" -> ex.getMessage
              )
            )
        }
      case JsError(err) => Future.successful(BadRequest(JsError.toJson(err)))
    }
  }

  def getById(id: Int): Action[AnyContent] = Action.async { implicit request =>
    service.getById(id).map(rc => Ok(Json.toJson(rc))).recover {
      case ex: ReservationClassNotFoundException =>
        NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
      case ex: Throwable =>
        println(s"\n  [DB EXCEPTION IN RESERVATION CLASS GETBYID: $id]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
    }
  }

  def list(): Action[AnyContent] = Action.async { implicit request =>
    service.list().map(list => Ok(Json.toJson(list))).recover {
      case ex: Throwable =>
        println("\n  [DB EXCEPTION IN LIST RESERVATION CLASSES]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
    }
  }

  def delete(id: Int): Action[AnyContent] = Action.async { implicit request =>
    service
      .delete(id)
      .map(_ =>
        Ok(
          Json.obj(
            "status" -> "Success",
            "message" -> s"Reservation class $id dropped."
          )
        )
      )
      .recover {
        case ex: ReservationClassNotFoundException =>
          NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
        case ex: Throwable =>
          println(s"\n  [DB EXCEPTION IN DELETE RESERVATION CLASS: $id]  ")
          ex.printStackTrace()
          InternalServerError(
            Json.obj("status" -> "Error", "message" -> ex.getMessage)
          )
      }
  }
}
