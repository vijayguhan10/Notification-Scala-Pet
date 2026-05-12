package repositories
import Tables.UserActivityEventTable
import models.Db.UserActivityEventRow
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext    
import javax.inject.{ Inject, Singleton }
@Singleton
class UserActivityEventRepository @Inject() 
(    protected val dbConfigProvider: play.api.db.slick.DatabaseConfigProvider
)(implicit ec: ExecutionContext)
{
    private val db=dbConfigProvider.get.db
    private val userActivityEvents = TableQuery[UserActivityEventTable]
    def insertBatch(events: Seq[UserActivityEventRow]): Future[Option[Int]] = {
        db.run(userActivityEvents ++= events)
    }
}