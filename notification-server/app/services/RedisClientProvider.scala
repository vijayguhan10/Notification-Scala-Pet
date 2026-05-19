package services

import config.RedisConfig
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class RedisClientProvider @Inject() (
    redisConfig: RedisConfig,
    lifecycle: ApplicationLifecycle
) extends Logging {

  private val poolConfig = new JedisPoolConfig()
  poolConfig.setMaxTotal(64)
  poolConfig.setMaxIdle(16)

  private val pool =
    new JedisPool(
      poolConfig,
      redisConfig.host,
      redisConfig.port
    )

  lifecycle.addStopHook { () =>
    try pool.close()
    catch {
      case t: Throwable =>
        logger.warn("Redis pool close failed", t)
    }
    Future.successful(())
  }

  def withJedis[A](f: Jedis => A): A = {
    val jedis = pool.getResource

    try {
      if (redisConfig.database != 0) {
        jedis.select(redisConfig.database)
      }

      f(jedis)
    } finally {
      try jedis.close()
      catch { case _: Throwable => () }
    }
  }
}
