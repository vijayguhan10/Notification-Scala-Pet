package services

import config.RedisConfig
import models.UserActivityEvent
import play.api.Logging

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import javax.inject.{Inject, Singleton}

final case class DynamicNotification(
    templateId: String,
    message: String,
    priority: String
)

final case class IntentScoreResult(
    score: Int,
    category: String,
    notification: Option[DynamicNotification]
)

@Singleton
class IntentScoringEngine @Inject() (
    redis: RedisClientProvider,
    redisConfig: RedisConfig
) extends Logging {

  // Field identifiers used inside the Redis hash
  private val FieldSearches = "searches"
  private val FieldViews = "views"
  private val FieldBookings = "bookings"

  // Weights assigned to each interaction type
  private val WeightSearch = 10
  private val WeightView = 15
  private val WeightBooking = 35

  private def metricsKey(userId: String): String =
    s"intent:metrics:$userId"

  def updateAndGet(event: UserActivityEvent): IntentScoreResult = {

    val userId = event.userId
    val key = metricsKey(userId)

    redis.withJedis { jedis =>
      try {
        // Atomically increment metrics based on event payload. The event
        // fields represent counts for this user interaction event; we keep a
        // rolling aggregate within the TTL window.
        if (event.parkingSearches > 0) {
          jedis.hincrBy(key, FieldSearches, event.parkingSearches.toLong)
        }

        if (event.slotViews > 0) {
          jedis.hincrBy(key, FieldViews, event.slotViews.toLong)
        }

        if (event.bookingAttempts > 0) {
          jedis.hincrBy(key, FieldBookings, event.bookingAttempts.toLong)
        }

        jedis.expire(key, redisConfig.intentTtlSeconds)

        val rawMetrics =
          Option(jedis.hgetAll(key))
            .map(_.asScala.toMap)
            .getOrElse(Map.empty)

        val searches =
          rawMetrics.get(FieldSearches).flatMap(_.toIntOption).getOrElse(0)

        val views =
          rawMetrics.get(FieldViews).flatMap(_.toIntOption).getOrElse(0)

        val bookings =
          rawMetrics.get(FieldBookings).flatMap(_.toIntOption).getOrElse(0)

        val calculatedScore =
          (searches * WeightSearch) +
            (views * WeightView) +
            (bookings * WeightBooking)

        val contextualNotification =
          generateContextualNotification(searches, views, bookings)

        IntentScoreResult(
          score = calculatedScore,
          category = category(calculatedScore),
          notification = contextualNotification
        )
      } catch {
        case NonFatal(e) =>
          logger.error(
            s"Failed to execute dynamic intent scoring for userId=$userId",
            e
          )
          IntentScoreResult(
            score = 0,
            category = "low",
            notification = None
          )
      }
    }
  }

  private def generateContextualNotification(
      searches: Int,
      views: Int,
      bookings: Int
  ): Option[DynamicNotification] = {

    (searches, views, bookings) match {

      // High friction: tried booking but didn't complete.
      case (_, _, b) if b >= 1 =>
        Some(
          DynamicNotification(
            templateId = "friction_rescue_checkout",
            message =
              "Did something go wrong? We held your reservation option. Tap to finish booking using 1-Click Pay!",
            priority = "IMMEDIATE"
          )
        )

      // High comparison behavior.
      case (s, v, 0) if s >= 3 && v >= 3 =>
        Some(
          DynamicNotification(
            templateId = "comparison_price_nudge",
            message =
              "Still weighing choices? This garage is $2 cheaper than other spaces nearby. Secure it before arriving!",
            priority = "HIGH"
          )
        )

      // Explorer: searching repeatedly, no deep views yet.
      case (s, 0, 0) if s >= 5 =>
        Some(
          DynamicNotification(
            templateId = "explorer_inventory_alert",
            message =
              "Parking is highly limited in this sector. Here are 2 real-time available garages nearby with clear spaces.",
            priority = "MEDIUM"
          )
        )

      case _ => None
    }
  }

  private def category(score: Int): String = {
    if (score < 30) "low"
    else if (score < 60) "medium"
    else if (score < 90) "high"
    else "immediate"
  }
}
