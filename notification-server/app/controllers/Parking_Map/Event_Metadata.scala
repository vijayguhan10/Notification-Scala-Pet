package controllers.Parking_Map

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.libs.json._
import models.db.Parking_Map.EventMetadata
import services.EventMetadataService

@Singleton
class EventMetadataController @Inject()(
    val controllerComponents: ControllerComponents,
    eventService: EventMetadataService
)(implicit ec: ExecutionContext) extends BaseController {

  // Play JSON Macro automatically generates Reads/Writes for your case class.
  // Note: Default Play JSON builds support standard ISO-8601 strings for java.time.LocalDateTime.
  implicit val eventMetadataFormat: OFormat[EventMetadata] = Json.format[EventMetadata]

  /** POST /api/events
    */
  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[EventMetadata] match {
      case JsSuccess(event, _) =>
        eventService.createEvent(event)
          .map(createdEvent => Created(Json.toJson(createdEvent)))
          .recover {
            case ex: IllegalArgumentException => 
              BadRequest(Json.obj("status" -> "Error", "message" -> ex.getMessage))
            case ex: Throwable => 
              InternalServerError(Json.obj("status" -> "Error", "message" -> "An unexpected database error occurred."))
          }
      case JsError(errors) =>
        Future.successful(BadRequest(JsError.toJson(errors)))
    }
  }

  /** GET /api/events/:id
    */
  def getById(id: Int): Action[AnyContent] = Action.async { implicit request =>
    eventService.getEventById(id).map {
      case Some(event) => Ok(Json.toJson(event))
      case None        => NotFound(Json.obj("status" -> "Error", "message" -> s"Event with ID $id not found."))
    }
  }

  /** GET /api/events/active?locationId=XYZ
    */
  def getActiveByLocation(locationId: String): Action[AnyContent] = Action.async { implicit request =>
    if (locationId.trim.isEmpty) {
      Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> "locationId query parameter cannot be empty.")))
    } else {
      eventService.getActiveEventsByLocation(locationId).map { events =>
        Ok(Json.toJson(events))
      }
    }
  }

  /** GET /api/events?limit=50&offset=0
    */
  def list(limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    eventService.listEvents(limit, offset).map { events =>
      Ok(Json.toJson(events))
    }
  }

  /** DELETE /api/events/:id
    */
  def delete(id: Int): Action[AnyContent] = Action.async { implicit request =>
    eventService.deleteEvent(id).map { wasDeleted =>
      if (wasDeleted) {
        Ok(Json.obj("status" -> "Success", "message" -> s"Event $id successfully deleted."))
      } else {
        NotFound(Json.obj("status" -> "Error", "message" -> s"Event $id could not be found to delete."))
      }
    }
  }
}