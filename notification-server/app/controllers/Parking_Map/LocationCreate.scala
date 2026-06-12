package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.Location
import services._

@Singleton
class LocationController @Inject() (
    val controllerComponents: ControllerComponents,
    service: LocationService
)(implicit ec: ExecutionContext)
    extends BaseController {

  // Auto-generates standard JSON Reads/Writes for the Location case class
  implicit val locationFormat: OFormat[Location] = Json.format[Location]

  /** POST /api/locations
    */
  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[Location] match {
      case JsSuccess(location, _) =>
        service
          .createLocation(location)
          .map(created => Created(Json.toJson(created)))
          .recover {
            case ex: InvalidLocationException =>
              BadRequest(
                Json.obj("status" -> "Error", "message" -> ex.getMessage)
              )

            case ex: Throwable =>
              println("\n  [DATABASE EXCEPTION IN CREATE LOCATION]  ")
              ex.printStackTrace()
              println("-------------------------------------------\n")

              InternalServerError(
                Json.obj(
                  "status" -> "Error",
                  "message" -> "Database error occurred while adding location.",
                  "rawError" -> ex.getMessage
                )
              )
          }
      case JsError(errors) =>
        Future.successful(BadRequest(JsError.toJson(errors)))
    }
  }

  /** GET /api/locations/:id
    */
  def getById(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      service
        .getLocationById(id)
        .map(loc => Ok(Json.toJson(loc)))
        .recover {
          case ex: LocationNotFoundException =>
            NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))

          case ex: Throwable =>
            println(s"\n  [DATABASE EXCEPTION IN GET_BY_ID FOR ID: $id]  ")
            ex.printStackTrace()
            InternalServerError(
              Json.obj("status" -> "Error", "message" -> ex.getMessage)
            )
        }
  }

  /** GET /api/locations?city=NewYork&status=ACTIVE&limit=50&offset=0
    */
  def list(
      city: Option[String],
      status: Option[String],
      limit: Int,
      offset: Int
  ): Action[AnyContent] = Action.async { implicit request =>
    service
      .listLocations(city, status, limit, offset)
      .map(list => Ok(Json.toJson(list)))
      .recover { case ex: Throwable =>
        println("\n  [DATABASE EXCEPTION IN LIST LOCATIONS]  ")
        ex.printStackTrace()
        InternalServerError(
          Json.obj("status" -> "Error", "message" -> ex.getMessage)
        )
      }
  }

  /** PATCH /api/locations/:id/status
    */
  def updateStatus(id: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      (request.body \ "status").validate[String] match {
        case JsSuccess(newStatus, _) =>
          service
            .updateLocationStatus(id, newStatus)
            .map(_ =>
              Ok(
                Json.obj(
                  "status" -> "Success",
                  "message" -> "Location status updated successfully."
                )
              )
            )
            .recover {
              case ex: InvalidLocationException =>
                BadRequest(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )

              case ex: LocationNotFoundException =>
                NotFound(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )

              case ex: Throwable =>
                println(
                  s"\n  [DATABASE EXCEPTION IN UPDATE_STATUS FOR ID: $id]  "
                )
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
                "message" -> "Missing or invalid 'status' string field."
              )
            )
          )
      }
  }

  /** PUT /api/locations/:id
    */
  def update(id: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      request.body.validate[Location] match {
        case JsSuccess(updatedLocation, _) =>
          service
            .updateLocation(id, updatedLocation)
            .map(_ =>
              Ok(
                Json.obj(
                  "status" -> "Success",
                  "message" -> s"Location $id fully updated."
                )
              )
            )
            .recover {
              case ex: LocationNotFoundException =>
                NotFound(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )

              case ex: Throwable =>
                println(
                  s"\n  [DATABASE EXCEPTION IN FULL_UPDATE FOR ID: $id]  "
                )
                ex.printStackTrace()
                InternalServerError(
                  Json.obj("status" -> "Error", "message" -> ex.getMessage)
                )
            }
        case JsError(errors) =>
          Future.successful(BadRequest(JsError.toJson(errors)))
      }
  }

  /** DELETE /api/locations/:id
    */
  def delete(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      service
        .deleteLocation(id)
        .map(_ =>
          Ok(
            Json.obj(
              "status" -> "Success",
              "message" -> s"Location $id dropped successfully."
            )
          )
        )
        .recover {
          case ex: LocationNotFoundException =>
            NotFound(Json.obj("status" -> "Error", "message" -> ex.getMessage))

          case ex: Throwable =>
            println(s"\n  [DATABASE EXCEPTION IN DELETE FOR ID: $id]  ")
            ex.printStackTrace()
            InternalServerError(
              Json.obj(
                "status" -> "Error",
                "message" -> "Failed to delete target location record."
              )
            )
        }
  }
}
