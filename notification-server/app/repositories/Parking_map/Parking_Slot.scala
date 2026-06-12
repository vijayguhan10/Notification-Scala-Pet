package repositories

import tables.ParkingSlotTable
import models.db.Parking_Map.ParkingSlot
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ParkingSlotRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val parkingSlots = TableQuery[ParkingSlotTable]

  private val insertWithId = (parkingSlots returning parkingSlots.map(_.slotId))
    .into { (row, id) => row.copy(slotId = Some(id)) }

  def insert(parkingSlot: ParkingSlot): Future[ParkingSlot] = {
    run(insertWithId += parkingSlot)
  }

  def findById(slotId: Int): Future[Option[ParkingSlot]] = {
    run(parkingSlots.filter(_.slotId === slotId).result.headOption)
  }

  def findBySensorId(sensorId: String): Future[Option[ParkingSlot]] = {
    run(parkingSlots.filter(_.sensorId === sensorId).result.headOption)
  }

  def list(
      locationIdOpt: Option[String] = None,
      statusOpt: Option[String] = None,
      limit: Int = 50,
      offset: Int = 0
  ): Future[Seq[ParkingSlot]] = {
    val base = parkingSlots.sortBy(_.displayCode.asc)

    val withLocation = locationIdOpt match {
      case Some(locId) => base.filter(_.locationId === locId)
      case None        => base
    }

    val withStatus = statusOpt match {
      case Some(status) => withLocation.filter(_.currentStatus === status)
      case None         => withLocation
    }

    val safeLimit = Math.max(1, Math.min(limit, 500))
    val safeOffset = Math.max(0, offset)

    run(withStatus.drop(safeOffset).take(safeLimit).result)
  }

  def updateStatus(slotId: Int, currentStatus: String): Future[Int] = {
    run(parkingSlots.filter(_.slotId === slotId).map(_.currentStatus).update(currentStatus))
  }

  def update(slotId: Int, slot: ParkingSlot): Future[Int] = {
    run(parkingSlots.filter(_.slotId === slotId).update(slot))
  }

  def delete(slotId: Int): Future[Int] = {
    run(parkingSlots.filter(_.slotId === slotId).delete)
  }
}