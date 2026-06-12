package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime
import models.db.Parking_Map.EventMetadata
import repositories.EventMetadataRepository

@Singleton
class EventMetadataService @Inject()(
    eventRepository: EventMetadataRepository
)(implicit ec: ExecutionContext) {

  /** Enforces business rules before allowing an insertion into the DB.
    */
  def createEvent(event: EventMetadata): Future[EventMetadata] = {
    if (event.startTime.isAfter(event.endTime)) {
      Future.failed(new IllegalArgumentException("Invalid Timeline: Event start time must occur before its end time."))
    } else {
      eventRepository.insert(event)
    }
  }

  def getEventById(eventId: Int): Future[Option[EventMetadata]] = {
    eventRepository.findById(eventId)
  }

  /** Automatically captures the current system time to query the repository layer.
    */
  def getActiveEventsByLocation(locationId: String): Future[Seq[EventMetadata]] = {
    val now = LocalDateTime.now()
    eventRepository.findActiveEventsByLocation(locationId, now)
  }

  def listEvents(limit: Int, offset: Int): Future[Seq[EventMetadata]] = {
    eventRepository.list(limit, offset)
  }

  def deleteEvent(eventId: Int): Future[Boolean] = {
    eventRepository.delete(eventId).map(_ > 0)
  }
}