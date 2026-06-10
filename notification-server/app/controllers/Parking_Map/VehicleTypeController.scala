package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.VehicleType
import services._

@Singleton
class VehicleTypeController @Inject() (
    val controllerComponents: ControllerComponents,
    service: VehicleTypeService
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val format: OFormat[VehicleType] = Json.format[VehicleType]

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[VehicleType] match {
      case JsSuccess(vt, _) =>
        service.create(vt).map(res => Created(Json.toJson(res))).recover {
          case ex: InvalidVehicleTypeException =>
            BadRequest(
              Json.obj("status" -> "Error", "message" -> ex.getMessage)
            )
          case ex: Throwable =>
            println("\n  [DB EXCEPTION IN CREATE VEHICLE TYPE]  ")
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
    service.getById(id).map(vt => Ok(Json.toJson(vt))).recover {
      case ex: VehicleTypeNotFoundException =>
        NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
      case ex: Throwable =>
        println(s"\n  [DB EXCEPTION IN VEHICLE TYPE GETBYID: $id]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
    }
  }

  def list(): Action[AnyContent] = Action.async { implicit request =>
    service.list().map(list => Ok(Json.toJson(list))).recover {
      case ex: Throwable =>
        println("\n  [DB EXCEPTION IN LIST VEHICLE TYPES]  ")
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
            "message" -> s"Vehicle type $id dropped."
          )
        )
      )
      .recover {
        case ex: VehicleTypeNotFoundException =>
          NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
        case ex: Throwable =>
          println(s"\n  [DB EXCEPTION IN DELETE VEHICLE TYPE: $id]  ")
          ex.printStackTrace()
          InternalServerError(
            Json.obj("status" -> "Error", "message" -> ex.getMessage)
          )
      }
  }
}
