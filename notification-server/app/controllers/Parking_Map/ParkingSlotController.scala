package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.ParkingSlot
import services._

@Singleton
class ParkingSlotController @Inject() (
    val controllerComponents: ControllerComponents,
    service: ParkingSlotService
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val format: OFormat[ParkingSlot] = Json.format[ParkingSlot]

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ParkingSlot] match {
      case JsSuccess(slot, _) =>
        service.create(slot).map(res => Created(Json.toJson(res))).recover {
          case ex: InvalidParkingSlotException =>
            BadRequest(
              Json.obj("status" -> "Error", "message" -> ex.getMessage)
            )
          case ex: Throwable =>
            println("\n  [DB EXCEPTION IN CREATE PARKING SLOT]  ")
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
    service.getById(id).map(s => Ok(Json.toJson(s))).recover {
      case ex: ParkingSlotNotFoundException =>
        NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
      case ex: Throwable =>
        println(s"\n  [DB EXCEPTION IN SLOT GETBYID: $id]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
    }
  }

  def getBySensorId(sensorId: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.getBySensorId(sensorId).map(s => Ok(Json.toJson(s))).recover {
        case ex: ParkingSlotNotFoundException =>
          NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
        case ex: Throwable =>
          println(s"\n  [DB EXCEPTION IN SLOT GETBYSENSORID: $sensorId]  ")
          ex.printStackTrace()
          InternalServerError(
            Json.obj("status" -> "Error", "message" -> ex.getMessage)
          )
      }
  }

  def list(
      locationId: Option[String],
      status: Option[String],
      limit: Int,
      offset: Int
  ): Action[AnyContent] = Action.async { implicit request =>
    service
      .list(locationId, status, limit, offset)
      .map(list => Ok(Json.toJson(list)))
      .recover { case ex: Throwable =>
        println("\n  [DB EXCEPTION IN LIST PARKING SLOTS]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
      }
  }

  def updateStatus(id: Int): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      (request.body \ "currentStatus").validate[String] match {
        case JsSuccess(newStatus, _) =>
          service
            .updateStatus(id, newStatus)
            .map(_ =>
              Ok(
                Json.obj(
                  "status" -> "Success",
                  "message" -> "Slot status updated."
                )
              )
            )
            .recover {
              case ex: InvalidParkingSlotException =>
                BadRequest(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
              case ex: ParkingSlotNotFoundException =>
                NotFound(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
              case ex: Throwable =>
                println(s"\n  [DB EXCEPTION IN SLOT UPDATESTATUS: $id]  ")
                ex.printStackTrace()
                InternalServerError(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
            }
        case JsError(_) =>
          Future.successful(
            BadRequest(
              Json.obj(
                "status" -> "Error",
                "message" -> "Missing string field 'currentStatus'"
              )
            )
          )
      }
  }

  def update(id: Int): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      request.body.validate[ParkingSlot] match {
        case JsSuccess(slot, _) =>
          service
            .update(id, slot)
            .map(_ =>
              Ok(
                Json.obj(
                  "status" -> "Success",
                  "message" -> s"Slot $id fully updated."
                )
              )
            )
            .recover {
              case ex: ParkingSlotNotFoundException =>
                NotFound(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
              case ex: Throwable =>
                println(s"\n  [DB EXCEPTION IN FULL SLOT UPDATE: $id]  ")
                ex.printStackTrace()
                InternalServerError(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
            }
        case JsError(err) => Future.successful(BadRequest(JsError.toJson(err)))
      }
  }

  def delete(id: Int): Action[AnyContent] = Action.async { implicit request =>
    service
      .delete(id)
      .map(_ =>
        Ok(Json.obj("status" -> "Success", "message" -> s"Slot $id removed."))
      )
      .recover {
        case ex: ParkingSlotNotFoundException =>
          NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))
        case ex: Throwable =>
          println(s"\n  [DB EXCEPTION IN DELETE SLOT: $id]  ")
          ex.printStackTrace()
          InternalServerError(
            Json.obj("status" -> "Error", "message" -> ex.getMessage)
          )
      }
  }
}
