package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.db.Parking_Map.LocationType
import repositories.LocationTypeRepository

@Singleton
class LocationTypeService @Inject()(
    repository: LocationTypeRepository
)(implicit ec: ExecutionContext) {

  def createLocationType(locationType: LocationType): Future[LocationType] = {
    if (locationType.typeName.trim.isEmpty) {
      Future.failed(new IllegalArgumentException("Type name cannot be empty."))
    } else {
      repository.insert(locationType)
    }
  }

  def getLocationTypeById(typeId: Int): Future[Option[LocationType]] = {
    repository.findById(typeId)
  }

  def listLocationTypes(limit: Int, offset: Int): Future[Seq[LocationType]] = {
    repository.list(limit, offset)
  }

  def updateLocationTypeName(typeId: Int, typeName: String): Future[Boolean] = {
    if (typeName.trim.isEmpty) {
      Future.failed(new IllegalArgumentException("Updated type name cannot be empty."))
    } else {
      repository.updateName(typeId, typeName).map(_ > 0)
    }
  }

  def deleteLocationType(typeId: Int): Future[Boolean] = {
    repository.delete(typeId).map(_ > 0)
  }
}