package repositories

import tables.LocationTypeTable
import models.db.Parking_Map.LocationType
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LocationTypeRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val locationTypes = TableQuery[LocationTypeTable]

  private val insertWithId = (locationTypes returning locationTypes.map(_.typeId))
    .into { (row, id) => row.copy(typeId = Some(id)) }

  def insert(locationType: LocationType): Future[LocationType] = {
    run(insertWithId += locationType)
  }

  def findById(typeId: Int): Future[Option[LocationType]] = {
    run(locationTypes.filter(_.typeId === typeId).result.headOption)
  }

  def list(limit: Int = 50, offset: Int = 0): Future[Seq[LocationType]] = {
    val safeLimit = Math.max(1, Math.min(limit, 500))
    val safeOffset = Math.max(0, offset)
    run(locationTypes.drop(safeOffset).take(safeLimit).result)
  }

  def updateName(typeId: Int, typeName: String): Future[Int] = {
    run(locationTypes.filter(_.typeId === typeId).map(_.typeName).update(typeName))
  }

  def delete(typeId: Int): Future[Int] = {
    run(locationTypes.filter(_.typeId === typeId).delete)
  }
}