package services

import models.UserActivityEvent

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.util.Random

//vector used for optimized datastructure
@Singleton
class EventGenerator @Inject() () {
  private val random = new Random()

  private val locations = Vector(
    "Phoenix Mall",
    "Forum Mall",
    "Lulu Mall",
    "City Center",
    "Airport"
  )

  def generate(): UserActivityEvent = {
    UserActivityEvent(
      userId = s"user_${random.between(1, 1000)}",
      parkingSearches = random.between(0, 25),
      slotViews = random.between(0, 80),
      bookingAttempts = random.between(0, 6),
      avgScrollDepth = random.between(0, 101),
      lastLocation = pick(locations),
      lastActivity = Instant.now().toString,
      sessionDuration = random.between(0, 1800)
    )
  }

  private def pick[A](items: Vector[A]): A = items(random.nextInt(items.size))
}
