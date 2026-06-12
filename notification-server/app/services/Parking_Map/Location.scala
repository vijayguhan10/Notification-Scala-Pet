package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.db.Parking_Map.Location
import repositories.LocationRepository

// =========================================================================
// CUSTOM EXCEPTIONS (Self-contained in this file)
// =========================================================================
case class LocationNotFoundException(locationId: String) 
  extends Exception(s"Location with ID '$locationId' does not exist.")

case class InvalidLocationException(message: String) 
  extends Exception(message)


// =========================================================================
// SERVICE LAYER IMPLEMENTATION
// =========================================================================
@Singleton
class LocationService @Inject()(
    repository: LocationRepository
)(implicit ec: ExecutionContext) {

  def createLocation(location: Location): Future[Location] = {
    if (location.locationId.trim.isEmpty) {
      Future.failed(InvalidLocationException("Location ID cannot be empty."))
    } else if (location.locationName.trim.isEmpty) {
      Future.failed(InvalidLocationException("Location Name cannot be empty."))
    } else if (location.city.trim.isEmpty) {
      Future.failed(InvalidLocationException("City field cannot be empty."))
    } else {
      repository.insert(location)
    }
  }

  def getLocationById(locationId: String): Future[Location] = {
    repository.findById(locationId).map {
      case Some(loc) => loc
      case None      => throw LocationNotFoundException(locationId)
    }
  }

  def listLocations(cityOpt: Option[String], statusOpt: Option[String], limit: Int, offset: Int): Future[Seq[Location]] = {
    repository.list(cityOpt, statusOpt, limit, offset)
  }

  def updateLocationStatus(locationId: String, status: String): Future[Boolean] = {
    if (status.trim.isEmpty) {
      Future.failed(InvalidLocationException("Status cannot be empty."))
    } else {
      repository.updateStatus(locationId, status).map { rowsAffected =>
        if (rowsAffected == 0) throw LocationNotFoundException(locationId)
        else true
      }
    }
  }

  def updateLocation(locationId: String, location: Location): Future[Boolean] = {
    repository.update(locationId, location).map { rowsAffected =>
      if (rowsAffected == 0) throw LocationNotFoundException(locationId)
      else true
    }
  }

  def deleteLocation(locationId: String): Future[Boolean] = {
    repository.delete(locationId).map { rowsAffected =>
      if (rowsAffected == 0) throw LocationNotFoundException(locationId)
      else true
    }
  }
}