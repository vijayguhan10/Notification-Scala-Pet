package models.db

import java.time.LocalDateTime

object Parking_Map {

  final case class LocationType(
      typeId: Option[Int] = None,
      typeName: String
  )

  final case class Location(
      locationId: String,
      typeId: Int,
      locationName: String,
      city: String,
      status: String
  )

  final case class EventMetadata(
      eventId: Option[Int] = None,
      locationId: String,
      startTime: LocalDateTime,
      endTime: LocalDateTime
  )

  final case class VehicleType(
      vehicleTypeId: Option[Int] = None,
      typeDisplayName: String
  )
  //case class Reservation

  final case class ReservationClass(
      classId: Option[Int] = None,
      className: String
  )

  final case class ParkingSlot(
      slotId: Option[Int] = None,
      locationId: String,
      displayCode: String,
      currentStatus: String,
      vehicleTypeId: Int,
      sensorId: String,
      reservationClassId: Int
  )
}
