package services

import models.UserActivityEvent
import play.api.Logging

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.Instant

object EmailPublisher extends Logging {

  /** Compose a simple, neat HTML email from the event and attempt to send it
    * via the system `sendmail` binary. If sending fails, write the HTML to
    * `logs/emails/` as a fallback so the output can be inspected.
    */
  def sendEventEmail(event: UserActivityEvent): Unit = {

    val html =
      s"""<!doctype html>
      |<html>
      |<head>
      |  <meta charset=\"utf-8\" />
      |  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />
      |  <style>
      |    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial; background:#f6f9fc; color:#0f1724; margin:0; padding:20px }
      |    .card { background:white; border-radius:8px; box-shadow:0 2px 6px rgba(16,24,40,0.08); max-width:720px; margin:20px auto; padding:20px }
      |    h1 { font-size:18px; margin:0 0 8px 0 }
      |    p { margin:6px 0; color:#334155 }
      |    .meta { font-size:13px; color:#64748b }
      |    .kv { display:flex; gap:8px; margin:6px 0 }
      |    .label { color:#94a3b8; min-width:140px }
      |  </style>
      |</head>
      |<body>
      |  <div class=\"card\">
      |    <h1>Notification — User Activity</h1>
      |    <p class=\"meta\">Event captured: ${Instant.now().toString}</p>
      |    <div class=\"kv\"><div class=\"label\">User ID</div><div>${event.userId}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Parking searches</div><div>${event.parkingSearches}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Slot views</div><div>${event.slotViews}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Booking attempts</div><div>${event.bookingAttempts}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Avg scroll depth</div><div>${event.avgScrollDepth}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Last location</div><div>${event.lastLocation
          .getOrElse("-")}</div></div>
      |    <div class=\"kv\"><div class=\"label\">Last activity</div><div>${event.lastActivity}</div></div>
      |    <div style=\"margin-top:12px;color:#475569;font-size:13px\">Raw payload:</div>
      |    <pre style=\"white-space:pre-wrap;background:#f1f5f9;padding:10px;border-radius:6px;margin-top:8px;color:#0b1726\">${event.toString}</pre>
      |  </div>
      |</body>
      |</html>""".stripMargin

    val recipient = "notifications@example.com"
    val subject = s"Notification for user ${event.userId}"

    try {
      // attempt to use system sendmail if available
      val sendmailPath = "/usr/sbin/sendmail"
      val pb = new ProcessBuilder(sendmailPath, "-t")
      val proc = pb.start()

      val out = new java.io.BufferedWriter(
        new java.io.OutputStreamWriter(proc.getOutputStream, "UTF-8")
      )
      out.write(s"To: $recipient\n")
      out.write(s"Subject: $subject\n")
      out.write("MIME-Version: 1.0\n")
      out.write("Content-Type: text/html; charset=\"utf-8\"\n\n")
      out.write(html)
      out.flush()
      out.close()

      proc.waitFor()

      logger.info(
        s"EmailPublisher: attempted send to $recipient for user ${event.userId}"
      )

    } catch {
      case ex: Throwable =>
        logger.warn(
          "EmailPublisher: send failed, falling back to file output",
          ex
        )

        try {
          val dir = Paths.get("logs", "emails")
          if (!Files.exists(dir)) Files.createDirectories(dir)
          val filename = s"${Instant.now().toEpochMilli}_${event.userId}.html"
          Files.write(
            dir.resolve(filename),
            html.getBytes("UTF-8"),
            StandardOpenOption.CREATE
          )
          logger.info(
            s"EmailPublisher: fallback wrote email HTML to ${dir.resolve(filename)}"
          )
        } catch {
          case ex2: Throwable =>
            logger.error("EmailPublisher: fallback write failed", ex2)
        }
    }
  }
}
