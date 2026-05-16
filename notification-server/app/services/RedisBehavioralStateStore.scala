package services

import config.RedisConfig
import models.UserActivityEvent
import play.api.Logging
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}

@Singleton
class RedisBehavioralStateStore @Inject() (
    redis: RedisClientProvider,
    redisConfig: RedisConfig
) extends Logging {

  private def stateKey(userId: String): String =
    s"behavioral:state:$userId"

  def store(event: UserActivityEvent): Unit = {
    val payload = Json.toJson(event).toString()
    redis.withJedis { jedis =>
      jedis.setex(
        stateKey(event.userId),
        redisConfig.stateTtlSeconds,
        payload
      )
    }
  }
}
