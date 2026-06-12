package repositories

import tables.EventMetadataTable
import models.db.Parking_Map.EventMetadata
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventMetadataRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val events = TableQuery[EventMetadataTable]

  private val insertWithId = (events returning events.map(_.eventId))
    .into { (row, id) => row.copy(eventId = Some(id)) }

  def insert(event: EventMetadata): Future[EventMetadata] = {
    run(insertWithId += event)
  }

  def findById(eventId: Int): Future[Option[EventMetadata]] = {
    run(events.filter(_.eventId === eventId).result.headOption)
  }

  def findActiveEventsByLocation(locationId: String, now: LocalDateTime): Future[Seq[EventMetadata]] = {
    run(events.filter(e => e.locationId === locationId && e.startTime <= now && e.endTime >= now).result)
  }

  def list(limit: Int = 50, offset: Int = 0): Future[Seq[EventMetadata]] = {
    val safeLimit = Math.max(1, Math.min(limit, 500))
    val safeOffset = Math.max(0, offset)
    run(events.sortBy(_.startTime.desc).drop(safeOffset).take(safeLimit).result)
  }

  def delete(eventId: Int): Future[Int] = {
    run(events.filter(_.eventId === eventId).delete)
  }
}