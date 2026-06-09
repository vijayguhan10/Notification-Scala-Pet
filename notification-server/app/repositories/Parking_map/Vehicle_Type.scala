package repositories

import tables.VehicleTypeTable
import models.db.Parking_Map.VehicleType
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VehicleTypeRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val vehicleTypes = TableQuery[VehicleTypeTable]

  private val insertWithId = (vehicleTypes returning vehicleTypes.map(_.vehicleTypeId))
    .into { (row, id) => row.copy(vehicleTypeId = Some(id)) }

  def insert(vehicleType: VehicleType): Future[VehicleType] = {
    run(insertWithId += vehicleType)
  }

  def findById(vehicleTypeId: Int): Future[Option[VehicleType]] = {
    run(vehicleTypes.filter(_.vehicleTypeId === vehicleTypeId).result.headOption)
  }

  def list(): Future[Seq[VehicleType]] = {
    run(vehicleTypes.result)
  }

  def delete(vehicleTypeId: Int): Future[Int] = {
    run(vehicleTypes.filter(_.vehicleTypeId === vehicleTypeId).delete)
  }
}