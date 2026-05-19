package services

import config.RedisConfig
import models.UserActivityEvent
import play.api.Logging

import javax.inject.{Inject, Singleton}

final case class IntentScoreResult(
    score: Int,
    category: String
)

@Singleton
class IntentScoringEngine @Inject() (
    redis: RedisClientProvider,
    redisConfig: RedisConfig
) extends Logging {

  private final case class Signal(
      bitOffset: Int,
      weight: Int,
      active: UserActivityEvent => Boolean
  )

  private val signals: List[Signal] = List(
    Signal(
      bitOffset = 0,
      weight = 20,
      active = _.parkingSearches >= 5
    ),

    Signal(
      bitOffset = 1,
      weight = 15,
      active = _.slotViews >= 20
    ),

    Signal(
      bitOffset = 2,
      weight = 25,
      active = _.bookingAttempts >= 1
    ),

    Signal(
      bitOffset = 3,
      weight = 10,
      active = _.avgScrollDepth >= 70
    ),

    Signal(
      bitOffset = 4,
      weight = 15,
      active = e => Option(e.location).exists(_.trim.nonEmpty)
    ),

    Signal(
      bitOffset = 5,
      weight = 15,
      active = _.sessionDuration >= 600
    )
  )

  private def bitsKey(userId: String): String =
    s"intent:bits:$userId"

  private def scoreKey(userId: String): String =
    s"intent:score:$userId"

  def updateAndGet(event: UserActivityEvent): IntentScoreResult = {

    val userId = event.userId

    val score =
      redis.withJedis { jedis =>
        val keyBits = bitsKey(userId)

        val keyScore = scoreKey(userId)

        var delta = 0L

        signals.foreach { signal =>
          if (signal.active(event)) {

            val previousBit =
              jedis.setbit(keyBits, signal.bitOffset.toLong, true)

            if (!previousBit) {
              delta += signal.weight.toLong
            }
          }
        }

        val newScore: Long =
          if (delta != 0L) {

            jedis.incrBy(keyScore, delta)

          } else {

            val current = jedis.get(keyScore)

            if (current == null) 0L
            else current.toLongOption.getOrElse(0L)
          }

        jedis.expire(keyBits, redisConfig.intentTtlSeconds)
        jedis.expire(keyScore, redisConfig.intentTtlSeconds)

        newScore
      }

    IntentScoreResult(
      score = score.toInt,
      category = category(score.toInt)
    )
  }

  private def category(score: Int): String = {
    if (score < 30) "low"
    else if (score < 60) "moderate"
    else if (score < 80) "high"
    else "immediate"
  }
}
