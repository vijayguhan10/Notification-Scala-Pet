package models

import java.time.LocalDateTime
import play.api.libs.json.{Json, OFormat}

object Parking_Map {

  final case class LocationType(
      typeId: Option[Int] = None,
      typeName: String
  )

  object LocationType {
    implicit val format: OFormat[LocationType] =
      Json.format[LocationType]
  }

  final case class Location(
      locationId: String,
      typeId: Int,
      locationName: String,
      city: String,
      status: String
  )

  object Location {
    implicit val format: OFormat[Location] =
      Json.format[Location]
  }

  final case class EventMetadata(
      eventId: Option[Int] = None,
      locationId: String,
      startTime: LocalDateTime,
      endTime: LocalDateTime
  )

  object EventMetadata {
    implicit val format: OFormat[EventMetadata] =
      Json.format[EventMetadata]
  }

  final case class VehicleType(
      vehicleTypeId: Option[Int] = None,
      typeDisplayName: String
  )

  object VehicleType {
    implicit val format: OFormat[VehicleType] =
      Json.format[VehicleType]
  }

  final case class ReservationClass(
      classId: Option[Int] = None,
      className: String
  )

  object ReservationClass {
    implicit val format: OFormat[ReservationClass] =
      Json.format[ReservationClass]
  }

  final case class ParkingSlot(
      slotId: Option[Int] = None,
      locationId: String,
      displayCode: String,
      currentStatus: String,
      vehicleTypeId: Int,
      sensorId: String,
      reservationClassId: Int
  )

  object ParkingSlot {
    implicit val format: OFormat[ParkingSlot] =
      Json.format[ParkingSlot]
  }
}
