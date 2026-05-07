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



  private val pages = Vector(
    "/parking/home",
    "/parking/search",
    "/parking/slot-selection",
    "/parking/booking",
    "/parking/payment",
    "/parking/receipt",
    "/account/login",
    "/account/profile"
  )

  private val devices = Vector("mobile", "desktop", "tablet")
  private val browsers = Vector("Chrome", "Safari", "Firefox", "Edge")
  private val locations = Vector("Chennai", "Bengaluru", "Hyderabad", "Mumbai", "Delhi")

  def generate(): UserActivityEvent = {
    val eventType = pick(eventTypes)
    val scrollDepth = if (eventType == "SCROLL") random.between(0, 101) else 0

    UserActivityEvent(
      eventId = s"evt_${UUID.randomUUID().toString.take(8)}",
      userId = s"user_${random.between(1, 100000)}",
      sessionId = s"sess_${random.between(1, 100000)}",
      eventType = eventType,
      page = pick(pages),
      timestamp = Instant.now().toString,
      device = pick(devices),
      browser = pick(browsers),
      scrollDepth = scrollDepth,
      location = pick(locations)
    )
  }

  private def pick[A](items: Vector[A]): A = items(random.nextInt(items.size))
}
