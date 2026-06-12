package repositories

import tables.ReservationClassTable
import models.db.Parking_Map.ReservationClass
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReservationClassRepository @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends RepositoryBase(config, lifecycle) {

  private val reservationClasses = TableQuery[ReservationClassTable]

  private val insertWithId = (reservationClasses returning reservationClasses.map(_.classId))
    .into { (row, id) => row.copy(classId = Some(id)) }

  def insert(reservationClass: ReservationClass): Future[ReservationClass] = {
    run(insertWithId += reservationClass)
  }

  def findById(classId: Int): Future[Option[ReservationClass]] = {
    run(reservationClasses.filter(_.classId === classId).result.headOption)
  }

  def list(): Future[Seq[ReservationClass]] = {
    run(reservationClasses.result)
  }

  def delete(classId: Int): Future[Int] = {
    run(reservationClasses.filter(_.classId === classId).delete)
  }
}