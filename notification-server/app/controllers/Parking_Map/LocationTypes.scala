package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.LocationType
import services.LocationTypeService

@Singleton
class LocationTypeController @Inject()(
    val controllerComponents: ControllerComponents,
    service: LocationTypeService
)(implicit ec: ExecutionContext) extends BaseController {

  // Auto-generates standard JSON Reads/Writes for the case class
  implicit val locationTypeFormat: OFormat[LocationType] = Json.format[LocationType]

  /** POST /api/location-types
    */
  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[LocationType] match {
      case JsSuccess(locationType, _) =>
        service.createLocationType(locationType)
          .map(created => Created(Json.toJson(created)))
          .recover {
            case ex: IllegalArgumentException => 
              BadRequest(Json.obj("status" -> "Error", "message" -> ex.getMessage))
            case ex: Throwable => 
              // 👇 THIS WILL PRINT THE EXACT DATABASE ERROR IN YOUR TERMINAL
              println("\n❌ [CRITICAL DATABASE ERROR IN CREATE LOCATION TYPE] ❌")
              ex.printStackTrace() 
              println("----------------------------------------------------\n")
              
              InternalServerError(Json.obj(
                "status" -> "Error", 
                "message" -> "Database error occurred.",
                "rawError" -> ex.getMessage // 👈 Shows the real reason in Postman!
              ))
          }
      case JsError(errors) =>
        Future.successful(BadRequest(JsError.toJson(errors)))
    }
  }

  /** GET /api/location-types/:id
    */
  def getById(id: Int): Action[AnyContent] = Action.async { implicit request =>
    service.getLocationTypeById(id).map {
      case Some(locType) => Ok(Json.toJson(locType))
      case None          => NotFound(Json.obj("status" -> "Error", "message" -> s"Location Type with ID $id not found."))
    }.recover {
      case ex: Throwable =>
        println(s"\n❌ [DATABASE ERROR IN GET_BY_ID for ID: $id] ❌")
        ex.printStackTrace()
        InternalServerError(Json.obj("status" -> "Error", "message" -> ex.getMessage))
    }
  }

  /** GET /api/location-types?limit=50&offset=0
    */
  def list(limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    service.listLocationTypes(limit, offset).map { list =>
      Ok(Json.toJson(list))
    }.recover {
      case ex: Throwable =>
        println("\n❌ [DATABASE ERROR IN LIST LOCATION TYPES] ❌")
        ex.printStackTrace()
        InternalServerError(Json.obj("status" -> "Error", "message" -> ex.getMessage))
    }
  }

  /** PATCH /api/location-types/:id/name
    */
  def updateName(id: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.body \ "typeName").validate[String] match {
      case JsSuccess(newName, _) =>
        service.updateLocationTypeName(id, newName).map { wasUpdated =>
          if (wasUpdated) Ok(Json.obj("status" -> "Success", "message" -> "Name updated successfully."))
          else NotFound(Json.obj("status" -> "Error", "message" -> s"Location Type $id not found."))
        }.recover {
          case ex: IllegalArgumentException => 
            BadRequest(Json.obj("status" -> "Error", "message" -> ex.getMessage))
          case ex: Throwable =>
            println(s"\n❌ [DATABASE ERROR IN UPDATE NAME for ID: $id] ❌")
            ex.printStackTrace()
            InternalServerError(Json.obj("status" -> "Error", "message" -> ex.getMessage))
        }
      case JsError(_) =>
        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> "Missing or invalid 'typeName' string field.")))
    }
  }

  /** DELETE /api/location-types/:id
    */
  def delete(id: Int): Action[AnyContent] = Action.async { implicit request =>
    service.deleteLocationType(id).map { wasDeleted =>
      if (wasDeleted) Ok(Json.obj("status" -> "Success", "message" -> s"Location Type $id deleted."))
      else NotFound(Json.obj("status" -> "Error", "message" -> s"Location Type $id not found."))
    }.recover {
      case ex: Throwable =>
        println(s"\n❌ [DATABASE ERROR IN DELETE for ID: $id] ❌")
        ex.printStackTrace()
        InternalServerError(Json.obj("status" -> "Error", "message" -> ex.getMessage))
    }
  }
}