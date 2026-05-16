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

  /** Borrows a Jedis connection from the pool, switches to the configured Redis
    * database (if not default DB 0), executes the given operation, and safely
    * returns the connection back to the pool.
    *
    * Usage: withJedis { jedis => jedis.set("key", "value") }
    */
  def withJedis[A](f: Jedis => A): A = {
    val jedis = pool.getResource

    try {
      // Redis connections start in DB 0 by default
      if (redisConfig.database != 0) {
        jedis.select(redisConfig.database)
      }

      f(jedis)
    } finally {
      // In pooled mode, close() returns connection to pool
      try jedis.close()
      catch { case _: Throwable => () }
    }
  }
}
