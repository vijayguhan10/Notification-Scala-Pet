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
    notification: DynamicNotification
)

object IntentScoringEngine {

  private def safeLocation(event: UserActivityEvent): String =
    Option(event.location).map(_.trim).filter(_.nonEmpty).getOrElse("your area")

  def defaultNotification(
      intentLevel: String,
      event: UserActivityEvent
  ): DynamicNotification = {

    val loc = safeLocation(event)

    intentLevel match {
      case "low" =>
        DynamicNotification(
          templateId = "default_low",
          message = s"Parking spaces available near $loc. Park anytime.",
          priority = "LOW"
        )

      case "medium" =>
        DynamicNotification(
          templateId = "default_medium",
          message =
            s"Parking demand increasing near $loc. Slots may fill soon.",
          priority = "MEDIUM"
        )

      case "high" =>
        DynamicNotification(
          templateId = "default_high",
          message = s"Hurry! Very few parking slots left near $loc.",
          priority = "HIGH"
        )

      case "immediate" =>
        DynamicNotification(
          templateId = "default_immediate",
          message =
            s"Critical parking alert near $loc. Last slots remaining. Reach immediately.",
          priority = "IMMEDIATE"
        )

      case other =>
        DynamicNotification(
          templateId = "default_unknown",
          message = s"Parking update near $loc.",
          priority = other.trim.toUpperCase
        )
    }
  }
}

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

        val intentLevel =
          category(calculatedScore)

        val notification =
          contextualNotification
            .getOrElse(
              IntentScoringEngine.defaultNotification(intentLevel, event)
            )

        IntentScoreResult(
          score = calculatedScore,
          category = intentLevel,
          notification = notification
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
            notification = IntentScoringEngine.defaultNotification("low", event)
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
