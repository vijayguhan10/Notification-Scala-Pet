package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class RedisConfig @Inject() (
    config: Configuration
) {

  private val redis =
    config.get[Configuration]("redis")

  val host: String =
    redis.get[String]("host")

  val port: Int =
    redis.get[Int]("port")

  val database: Int =
    redis.getOptional[Int]("database").getOrElse(0)

  // Store the raw behavioral state snapshot
  val stateTtlSeconds: Int =
    redis.getOptional[Int]("stateTtlSeconds").getOrElse(86400)

  // Store intent bits + score
  val intentTtlSeconds: Int =
    redis.getOptional[Int]("intentTtlSeconds").getOrElse(86400)
}
