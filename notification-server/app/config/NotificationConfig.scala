package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class NotificationConfig @Inject() (
    config: Configuration
) {
  val endpoint: Option[String] =
    config.getOptional[String]("notification.endpoint")
}
