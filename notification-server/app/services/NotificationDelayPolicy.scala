package services

import javax.inject.Singleton

@Singleton
class NotificationDelayPolicy {

  // Delay is computed from intent score category.
  // Higher intent -> lower delay.
  def delayMs(intentCategory: String): Long = {
    intentCategory match {
      case "low"       => 10L * 60L * 1000L
      case "moderate"  => 2L * 60L * 1000L
      case "high"      => 30L * 1000L
      case "immediate" => 0L
      case _           => 0L
    }
  }
}
