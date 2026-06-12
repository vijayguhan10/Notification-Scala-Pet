package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.db.Parking_Map.VehicleType
import repositories.VehicleTypeRepository

// Custom Self-Contained Exceptions
case class VehicleTypeNotFoundException(id: Int) extends Exception(s"Vehicle Type with ID $id not found.")
case class InvalidVehicleTypeException(msg: String) extends Exception(msg)

@Singleton
class VehicleTypeService @Inject()(
    repository: VehicleTypeRepository
)(implicit ec: ExecutionContext) {

  def create(vehicleType: VehicleType): Future[VehicleType] = {
    if (vehicleType.typeDisplayName.trim.isEmpty) {
      Future.failed(InvalidVehicleTypeException("Display name cannot be empty."))
    } else {
      repository.insert(vehicleType)
    }
  }

  def getById(id: Int): Future[VehicleType] = {
    repository.findById(id).map {
      case Some(vt) => vt
      case None     => throw VehicleTypeNotFoundException(id)
    }
  }

  def list(): Future[Seq[VehicleType]] = repository.list()

  def delete(id: Int): Future[Boolean] = {
    repository.delete(id).map { rows =>
      if (rows == 0) throw VehicleTypeNotFoundException(id) else true
    }
  }
}