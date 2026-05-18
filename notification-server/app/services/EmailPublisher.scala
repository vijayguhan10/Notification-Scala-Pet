package services

import models.UserActivityEvent
import play.api.Logging

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.Instant
import java.util.Properties

import jakarta.mail._
import jakarta.mail.internet._

object EmailPublisher extends Logging {

  // Use environment variables in production
  private val Username = "vijayguhan10@gmail.com"

  // Google App Password (NOT normal Gmail password)
  private val Password = "vihg qlmm ghxm bnyf"

  private val Recipient = "vijayguhan10@gmail.com"

  def sendEventEmail(event: UserActivityEvent): Unit = {

    val html =
      s"""<!doctype html>
         |<html>
         |<head>
         |  <meta charset="utf-8" />
         |  <meta name="viewport" content="width=device-width,initial-scale=1" />
         |  <style>
         |    body {
         |      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI',
         |      Roboto, 'Helvetica Neue', Arial;
         |      background:#f6f9fc;
         |      color:#0f1724;
         |      margin:0;
         |      padding:20px;
         |    }
         |
         |    .card {
         |      background:white;
         |      border-radius:8px;
         |      box-shadow:0 2px 6px rgba(16,24,40,0.08);
         |      max-width:720px;
         |      margin:20px auto;
         |      padding:20px;
         |    }
         |
         |    h1 {
         |      font-size:18px;
         |      margin:0 0 8px 0;
         |    }
         |
         |    p {
         |      margin:6px 0;
         |      color:#334155;
         |    }
         |
         |    .meta {
         |      font-size:13px;
         |      color:#64748b;
         |    }
         |
         |    .kv {
         |      display:flex;
         |      gap:8px;
         |      margin:6px 0;
         |    }
         |
         |    .label {
         |      color:#94a3b8;
         |      min-width:140px;
         |      font-weight:600;
         |    }
         |
         |    pre {
         |      white-space:pre-wrap;
         |      background:#f1f5f9;
         |      padding:10px;
         |      border-radius:6px;
         |      margin-top:8px;
         |      color:#0b1726;
         |      overflow-x:auto;
         |    }
         |  </style>
         |</head>
         |
         |<body>
         |  <div class="card">
         |
         |    <h1>Notification — User Activity</h1>
         |
         |    <p class="meta">
         |      Event captured: ${Instant.now()}
         |    </p>
         |
         |    <div class="kv">
         |      <div class="label">User ID</div>
         |      <div>${event.userId}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Parking searches</div>
         |      <div>${event.parkingSearches}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Slot views</div>
         |      <div>${event.slotViews}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Booking attempts</div>
         |      <div>${event.bookingAttempts}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Avg scroll depth</div>
         |      <div>${event.avgScrollDepth}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Last location</div>
         |      <div>${if (
          event.location != null &&
          event.location.nonEmpty
        ) event.location
        else "-"}</div>
         |    </div>
         |
         |    <div class="kv">
         |      <div class="label">Last activity</div>
         |      <div>${event.lastActivity}</div>
         |    </div>
         |
         |    <div style="margin-top:12px;color:#475569;font-size:13px">
         |      Raw payload:
         |    </div>
         |
         |    <pre>${event.toString}</pre>
         |
         |  </div>
         |</body>
         |</html>
         |""".stripMargin

    val subject = s"Notification for user ${event.userId}"

    try {

      // SMTP configuration
      val props = new Properties()

      props.put("mail.smtp.auth", "true")
      props.put("mail.smtp.starttls.enable", "true")
      props.put("mail.smtp.host", "smtp.gmail.com")
      props.put("mail.smtp.port", "587")

      // Optional debug logs
    //   props.put("mail.debug", "true")

      // Authenticated SMTP session
      val session = Session.getInstance(
        props,
        new Authenticator() {
          override protected def getPasswordAuthentication
              : PasswordAuthentication =
            new PasswordAuthentication(Username, Password)
        }
      )

      // Create email message
      val message = new MimeMessage(session)

      message.setFrom(new InternetAddress(Username))

      // use String overload to avoid array type mismatch
      message.setRecipients(Message.RecipientType.TO, Recipient)

      message.setReplyTo(
        Array(new InternetAddress(Username))
      )

      message.setSubject(subject)

      message.setContent(html, "text/html; charset=utf-8")

      // Send email
      Transport.send(message)

      logger.info(
        s"EmailPublisher: email successfully sent to $Recipient " +
          s"for user ${event.userId}"
      )

    } catch {

      case ex: Throwable =>

        logger.warn(
          "EmailPublisher: send failed, falling back to file output",
          ex
        )

        // fallback: save HTML locally
        try {

          val dir = Paths.get("logs", "emails")

          if (!Files.exists(dir)) {
            Files.createDirectories(dir)
          }

          val filename =
            s"${Instant.now().toEpochMilli}_${event.userId}.html"

          val path = dir.resolve(filename)

          Files.write(
            path,
            html.getBytes("UTF-8"),
            StandardOpenOption.CREATE
          )

          logger.info(
            s"EmailPublisher: fallback wrote email HTML to $path"
          )

        } catch {

          case ex2: Throwable =>
            logger.error(
              "EmailPublisher: fallback write failed",
              ex2
            )
        }
    }
  }
}
