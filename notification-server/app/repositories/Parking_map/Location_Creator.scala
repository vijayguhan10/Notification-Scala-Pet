package repositories

import tables.LocationTable
import models.db.Parking_Map.Location
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LocationRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val locations = TableQuery[LocationTable]

  // String Alphanumeric PK (Not AutoInc), so we return the input row upon success
  def insert(location: Location): Future[Location] = {
    run((locations += location).map(_ => location))
  }

  def findById(locationId: String): Future[Option[Location]] = {
    run(locations.filter(_.locationId === locationId).result.headOption)
  }

  def list(
      cityOpt: Option[String] = None,
      statusOpt: Option[String] = None,
      limit: Int = 50,
      offset: Int = 0
  ): Future[Seq[Location]] = {
    val base = locations.sortBy(_.locationName.asc)

    val withCity = cityOpt match {
      case Some(city) => base.filter(_.city === city)
      case None       => base
    }

    val withStatus = statusOpt match {
      case Some(status) => withCity.filter(_.status === status)
      case None         => withCity
    }

    val safeLimit = Math.max(1, Math.min(limit, 500))
    val safeOffset = Math.max(0, offset)

    run(withStatus.drop(safeOffset).take(safeLimit).result)
  }

  def updateStatus(locationId: String, status: String): Future[Int] = {
    run(locations.filter(_.locationId === locationId).map(_.status).update(status))
  }

  def update(locationId: String, location: Location): Future[Int] = {
    run(locations.filter(_.locationId === locationId).update(location))
  }

  def delete(locationId: String): Future[Int] = {
    run(locations.filter(_.locationId === locationId).delete)
  }
}