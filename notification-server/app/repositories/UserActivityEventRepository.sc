package repositories
import Tables.UserActivityEventTable
import models.Db.UserActivityEventRow
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext    
import javax.inject.{ Inject, Singleton }