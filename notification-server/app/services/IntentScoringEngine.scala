package services

import config.RedisConfig
import models.UserActivityEvent
import play.api.Logging

import javax.inject.{Inject, Singleton}

/**
 * Final scoring response returned to callers.
 *
 * @param score
 *   Current accumulated intent score for the user.
 *
 * @param category
 *   Human-readable intent classification derived from score thresholds.
 */
final case class IntentScoreResult(
    score: Int,
    category: String
)

@Singleton
class IntentScoringEngine @Inject() (
    redis: RedisClientProvider,
    redisConfig: RedisConfig
) extends Logging {

  /**
   * Represents a single behavioral scoring rule.
   *
   * @param bitOffset
   *   Bitmap position used to track whether this signal
   *   has already contributed to the user's score.
   *
   * @param weight
   *   Score added when this signal becomes active
   *   for the first time.
   *
   * @param active
   *   Predicate that evaluates whether the signal
   *   is active for a given user activity event.
   */
  private final case class Signal(
      bitOffset: Int,
      weight: Int,
      active: UserActivityEvent => Boolean
  )

  /**
   * Incremental behavioral scoring model.
   *
   * Each signal maps to a single Redis bitmap bit.
   *
   * Bitmap semantics:
   *
   *   0 -> signal not rewarded yet
   *   1 -> signal already rewarded
   *
   * When a signal transitions from:
   *
   *   0 -> 1
   *
   * its weight is added exactly once to the user's score.
   *
   * This prevents repeated events from inflating scores.
   */
  private val signals: List[Signal] = List(

    // User searched parking frequently
    Signal(
      bitOffset = 0,
      weight = 20,
      active = _.parkingSearches >= 5
    ),

    // User viewed many parking slots
    Signal(
      bitOffset = 1,
      weight = 15,
      active = _.slotViews >= 20
    ),

    // User attempted booking
    Signal(
      bitOffset = 2,
      weight = 25,
      active = _.bookingAttempts >= 1
    ),

    // User deeply engaged with content
    Signal(
      bitOffset = 3,
      weight = 10,
      active = _.avgScrollDepth >= 70
    ),

    // User allowed location access
    Signal(
      bitOffset = 4,
      weight = 15,
      active = e => Option(e.lastLocation).exists(_.trim.nonEmpty)
    ),

    // User spent meaningful session time
    Signal(
      bitOffset = 5,
      weight = 15,
      active = _.sessionDuration >= 600
    )
  )

  /**
   * Redis bitmap key storing rewarded signal bits.
   *
   * Example:
   *
   *   intent:bits:user_10233
   *
   * Bitmap tracks which behavioral milestones
   * have already contributed to the score.
   */
  private def bitsKey(userId: String): String =
    s"intent:bits:$userId"

  /**
   * Redis score key storing accumulated intent score.
   *
   * Example:
   *
   *   intent:score:user_10233
   */
  private def scoreKey(userId: String): String =
    s"intent:score:$userId"

  /**
   * Processes a user activity event and incrementally updates
   * the user's intent score.
   *
   * Flow:
   *
   *   1. Evaluate all behavioral signals
   *   2. Use Redis bitmap to detect first-time signal activation
   *   3. Increment score only for newly activated signals
   *   4. Refresh TTL for both bitmap + score keys
   *   5. Return latest score + category
   *
   * Redis data model:
   *
   *   intent:bits:<userId>  -> bitmap of rewarded signals
   *   intent:score:<userId> -> accumulated score
   *
   * Bitmap prevents duplicate scoring from repeated events.
   */
  def updateAndGet(event: UserActivityEvent): IntentScoreResult = {

    // Current user being evaluated
    val userId = event.userId

    /**
     * Execute scoring logic inside a safely managed
     * Redis connection borrowed from the Jedis pool.
     *
     * withJedis internally:
     *
     *   1. borrows Redis connection
     *   2. executes block
     *   3. safely returns connection to pool
     */
    val score =
      redis.withJedis { jedis =>

        // Redis bitmap key
        val keyBits = bitsKey(userId)

        // Redis accumulated score key
        val keyScore = scoreKey(userId)

        /**
         * Tracks newly earned score for this event only.
         *
         * Example:
         *
         *   +20 for searches
         *   +15 for views
         *
         * Final delta = 35
         */
        var delta = 0L

        /**
         * Evaluate every behavioral signal.
         */
        signals.foreach { signal =>

          /**
           * Check whether the current behavioral rule
           * becomes active for this event.
           */
          if (signal.active(event)) {

            /**
             * Redis SETBIT operation:
             *
             *   1. sets current bit to 1
             *   2. returns previous bit value atomically
             *
             * Example:
             *
             *   SETBIT intent:bits:user1 0 1
             *
             * If previous bit:
             *
             *   false -> signal never rewarded before
             *   true  -> signal already rewarded earlier
             */
            val previousBit =
              jedis.setbit(keyBits, signal.bitOffset.toLong, true)

            /**
             * Only reward score if signal becomes active
             * for the FIRST time.
             *
             * Prevents repeated events from inflating score.
             */
            if (!previousBit) {
              delta += signal.weight.toLong
            }
          }
        }

        /**
         * Update accumulated score only if
         * newly activated signals were detected.
         */
        val newScore: Long =
          if (delta != 0L) {

            /**
             * Atomic Redis increment.
             *
             * Example:
             *
             *   current score = 35
             *   delta = 25
             *
             *   new score = 60
             */
            jedis.incrBy(keyScore, delta)

          } else {

            /**
             * No new signals activated.
             *
             * Simply return existing score.
             */
            val current = jedis.get(keyScore)

            if (current == null) 0L
            else current.toLongOption.getOrElse(0L)
          }

        /**
         * Refresh TTL for behavioral state.
         *
         * Purpose:
         *
         *   - remove stale intent automatically
         *   - implement time-decayed intent scoring
         *   - keep only recent behavioral relevance
         */
        jedis.expire(keyBits, redisConfig.intentTtlSeconds)
        jedis.expire(keyScore, redisConfig.intentTtlSeconds)

        // Return latest score
        newScore
      }

    /**
     * Build final response with:
     *
     *   - numeric score
     *   - intent category
     */
    IntentScoreResult(
      score = score.toInt,
      category = category(score.toInt)
    )
  }

  /**
   * Maps numeric score into business-friendly
   * intent classification.
   *
   * Score ranges:
   *
   *   < 30  -> low
   *   < 60  -> moderate
   *   < 80  -> high
   *   >= 80 -> immediate
   */
  private def category(score: Int): String = {
    if (score < 30) "low"
    else if (score < 60) "moderate"
    else if (score < 80) "high"
    else "immediate"
  }
}