package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.db.Parking_Map.ReservationClass
import repositories.ReservationClassRepository

// Custom Self-Contained Exceptions
case class ReservationClassNotFoundException(id: Int)
    extends Exception(s"Reservation Class with ID $id not found.")
case class InvalidReservationClassException(msg: String) extends Exception(msg)

@Singleton
class ReservationService @Inject() (
    repository: ReservationClassRepository
)(implicit ec: ExecutionContext) {

  def create(reservationClass: ReservationClass): Future[ReservationClass] = {
    if (reservationClass.className.trim.isEmpty) {
      Future.failed(
        InvalidReservationClassException("Class name cannot be empty.")
      )
    } else {
      repository.insert(reservationClass)
    }
  }

  def getById(id: Int): Future[ReservationClass] = {
    repository.findById(id).map {
      case Some(rc) => rc
      case None     => throw ReservationClassNotFoundException(id)
    }
  }

  def list(): Future[Seq[ReservationClass]] = repository.list()

  def delete(id: Int): Future[Boolean] = {
    repository.delete(id).map { rows =>
      if (rows == 0) throw ReservationClassNotFoundException(id) else true
    }
  }
}
