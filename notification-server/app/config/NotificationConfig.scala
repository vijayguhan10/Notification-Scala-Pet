package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class NotificationConfig @Inject() (
    config: Configuration
) {
  // Optional: use if/when you need to push notifications to an external endpoint.
  val endpoint: Option[String] =
    config.getOptional[String]("notification.endpoint")
}
