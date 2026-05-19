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
    // Ensure the pool is closed on shutdown; failures are logged but do not
    // prevent shutdown. Closing the pool can surface harmless network/IO
    // errors which we intentionally swallow to avoid blocking shutdown.
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
      // Select the configured DB (non-zero means non-default). This mutates
      // the connection state so callers should not assume a default DB.
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
