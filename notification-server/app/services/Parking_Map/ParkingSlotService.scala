package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.db.Parking_Map.ParkingSlot
import repositories.ParkingSlotRepository

// Custom Self-Contained Exceptions
case class ParkingSlotNotFoundException(msg: String) extends Exception(msg)
case class InvalidParkingSlotException(msg: String) extends Exception(msg)

@Singleton
class ParkingSlotService @Inject()(
    repository: ParkingSlotRepository
)(implicit ec: ExecutionContext) {

  def create(slot: ParkingSlot): Future[ParkingSlot] = {
    if (slot.locationId.trim.isEmpty) Future.failed(InvalidParkingSlotException("Location ID cannot be empty."))
    else if (slot.displayCode.trim.isEmpty) Future.failed(InvalidParkingSlotException("Display Code cannot be empty."))
    else if (slot.sensorId.trim.isEmpty) Future.failed(InvalidParkingSlotException("Sensor ID cannot be empty."))
    else repository.insert(slot)
  }

  def getById(id: Int): Future[ParkingSlot] = {
    repository.findById(id).map {
      case Some(s) => s
      case None    => throw ParkingSlotNotFoundException(s"Parking Slot with ID $id not found.")
    }
  }

  def getBySensorId(sensorId: String): Future[ParkingSlot] = {
    repository.findBySensorId(sensorId).map {
      case Some(s) => s
      case None    => throw ParkingSlotNotFoundException(s"Parking Slot tied to sensor '$sensorId' not found.")
    }
  }

  def list(locationIdOpt: Option[String], statusOpt: Option[String], limit: Int, offset: Int): Future[Seq[ParkingSlot]] = {
    repository.list(locationIdOpt, statusOpt, limit, offset)
  }

  def updateStatus(id: Int, status: String): Future[Boolean] = {
    if (status.trim.isEmpty) Future.failed(InvalidParkingSlotException("Status cannot be empty."))
    else {
      repository.updateStatus(id, status).map { rows =>
        if (rows == 0) throw ParkingSlotNotFoundException(s"Slot $id could not be found to update status.") else true
      }
    }
  }

  def update(id: Int, slot: ParkingSlot): Future[Boolean] = {
    repository.update(id, slot).map { rows =>
      if (rows == 0) throw ParkingSlotNotFoundException(s"Slot $id could not be found for modification.") else true
    }
  }

  def delete(id: Int): Future[Boolean] = {
    repository.delete(id).map { rows =>
      if (rows == 0) throw ParkingSlotNotFoundException(s"Slot $id not found to delete.") else true
    }
  }
}