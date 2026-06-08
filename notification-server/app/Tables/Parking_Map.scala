package tables

import models.db.Parking_Map._
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime

// 1. LOCATION TYPES TABLE
class LocationTypeTable(tag: Tag) 
  extends Table[LocationType](tag, "location_types") {

  def typeId   = column[Int]("type_id", O.PrimaryKey, O.AutoInc)
  def typeName = column[String]("type_name")

  def * = (typeId.?, typeName) <> (LocationType.tupled, LocationType.unapply)
}

// 2. LOCATIONS TABLE
class LocationTable(tag: Tag) 
  extends Table[Location](tag, "locations") {

  def locationId   = column[String]("location_id", O.PrimaryKey) // Alphanumeric, not AutoInc
  def typeId       = column[Int]("type_id")
  def locationName = column[String]("location_name")
  def city         = column[String]("city")
  def status       = column[String]("status")

  def * = (locationId, typeId, locationName, city, status) <> (Location.tupled, Location.unapply)

  // Foreign Key Relationship
  def locationType = foreignKey("fk_locations_type", typeId, TableQuery[LocationTypeTable])(_.typeId)
}

// 3. EVENT METADATA TABLE
class EventMetadataTable(tag: Tag) 
  extends Table[EventMetadata](tag, "event_metadata") {

  def eventId    = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
  def locationId = column[String]("location_id")
  def startTime  = column[LocalDateTime]("start_time")
  def endTime    = column[LocalDateTime]("end_time")

  def * = (eventId.?, locationId, startTime, endTime) <> (EventMetadata.tupled, EventMetadata.unapply)

  // Foreign Key Relationship
  def location = foreignKey("fk_event_metadata_location", locationId, TableQuery[LocationTable])(_.locationId)
}

// 4. VEHICLE TYPES TABLE
class VehicleTypeTable(tag: Tag) 
  extends Table[VehicleType](tag, "vehicle_types") {

  def vehicleTypeId   = column[Int]("vehicle_type_id", O.PrimaryKey, O.AutoInc)
  def typeDisplayName = column[String]("type_display_name")

  def * = (vehicleTypeId.?, typeDisplayName) <> (VehicleType.tupled, VehicleType.unapply)
}

// 5. RESERVATION CLASSES TABLE
class ReservationClassTable(tag: Tag) 
  extends Table[ReservationClass](tag, "reservation_classes") {

  def classId   = column[Int]("class_id", O.PrimaryKey, O.AutoInc)
  def className = column[String]("class_name")

  def * = (classId.?, className) <> (ReservationClass.tupled, ReservationClass.unapply)
}

// 6. PARKING SLOTS TABLE
class ParkingSlotTable(tag: Tag) 
  extends Table[ParkingSlot](tag, "parking_slots") {

  def slotId             = column[Int]("slot_id", O.PrimaryKey, O.AutoInc)
  def locationId         = column[String]("location_id")
  def displayCode        = column[String]("display_code")
  def currentStatus      = column[String]("current_status")
  def vehicleTypeId      = column[Int]("vehicle_type_id")
  def sensorId           = column[String]("sensor_id")
  def reservationClassId = column[Int]("reservation_class_id")

  def * = (
    slotId.?, 
    locationId, 
    displayCode, 
    currentStatus, 
    vehicleTypeId, 
    sensorId, 
    reservationClassId
  ) <> (ParkingSlot.tupled, ParkingSlot.unapply)

  // Foreign Key Relationships
  def location         = foreignKey("fk_slots_location", locationId, TableQuery[LocationTable])(_.locationId)
  def vehicleType      = foreignKey("fk_slots_vehicle_type", vehicleTypeId, TableQuery[VehicleTypeTable])(_.vehicleTypeId)
  def reservationClass = foreignKey("fk_slots_reservation_class", reservationClassId, TableQuery[ReservationClassTable])(_.classId)
}