package services

import jakarta.mail._
import jakarta.mail.internet._
import models.{NotificationMessage, UserActivityEvent}
import play.api.Logging

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.Instant
import java.util.Properties

object EmailPublisher extends Logging {

  private val Username = "vijayguhan10@gmail.com"

  // Google App Password
  private val Password = "vihg qlmm ghxm bnyf"

  private val Recipient = "vijayguhan10@gmail.com"

  // ============================================================
  // USER ACTIVITY EMAIL
  // ============================================================

  def sendEventEmail(event: UserActivityEvent): Unit = {

    val html =
      s"""
<!doctype html>
<html>

<head>

  <meta charset="utf-8" />

  <meta name="viewport"
        content="width=device-width, initial-scale=1" />

  <style>

    body {
      margin: 0;
      padding: 0;
      background: #f4f7fb;
      font-family: Inter, Arial, sans-serif;
      color: #0f172a;
    }

    .container {
      width: 100%;
      padding: 40px 0;
    }

    .card {
      max-width: 680px;
      margin: auto;
      background: #ffffff;
      border-radius: 18px;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      box-shadow: 0 10px 35px rgba(15, 23, 42, 0.08);
    }

    .header {
      background: linear-gradient(135deg, #0f172a, #1e293b);
      padding: 32px;
      color: white;
    }

    .header h1 {
      margin: 0;
      font-size: 24px;
      font-weight: 700;
    }

    .header p {
      margin-top: 8px;
      color: #cbd5e1;
      font-size: 14px;
    }

    .content {
      padding: 32px;
    }

    .grid {
      display: grid;
      grid-template-columns: 180px 1fr;
      row-gap: 16px;
      column-gap: 16px;
    }

    .label {
      color: #64748b;
      font-weight: 600;
      font-size: 14px;
    }

    .value {
      color: #0f172a;
      font-size: 14px;
    }

    .footer {
      border-top: 1px solid #e2e8f0;
      padding: 20px 32px;
      background: #fafcff;
      color: #94a3b8;
      font-size: 12px;
    }

  </style>

</head>

<body>

  <div class="container">

    <div class="card">

      <div class="header">

        <h1>User Activity Analytics</h1>

        <p>
          Real-time parking platform engagement summary
        </p>

      </div>

      <div class="content">

        <div class="grid">

          <div class="label">User ID</div>
          <div class="value">${event.userId}</div>

          <div class="label">Parking Searches</div>
          <div class="value">${event.parkingSearches}</div>

          <div class="label">Slot Views</div>
          <div class="value">${event.slotViews}</div>

          <div class="label">Booking Attempts</div>
          <div class="value">${event.bookingAttempts}</div>

          <div class="label">Average Scroll Depth</div>
          <div class="value">${event.avgScrollDepth}%</div>

          <div class="label">Location</div>
          <div class="value">${
            if (
              event.location != null &&
              event.location.nonEmpty
            ) event.location
            else "Unavailable"
          }</div>

          <div class="label">Last Activity</div>
          <div class="value">${event.lastActivity}</div>

        </div>

      </div>

      <div class="footer">

        Generated automatically by the Smart Parking Intelligence Platform.

      </div>

    </div>

  </div>

</body>

</html>
"""

    val subject = s"User Activity Report - ${event.userId}"

    sendHtmlEmail(subject, html, event.userId)
  }

  // ============================================================
  // NOTIFICATION EMAIL
  // ============================================================

  def sendNotificationEmail(notif: NotificationMessage): Unit = {

    val badgeColor =
      notif.eventType match {
        case "INTENT_IMMEDIATE" => "#dc2626"
        case "INTENT_HIGH"      => "#ea580c"
        case "INTENT_MEDIUM"    => "#2563eb"
        case "INTENT_LOW"       => "#64748b"
        case _                  => "#0f172a"
      }

    val badgeText =
      notif.eventType match {
        case "INTENT_IMMEDIATE" => "Immediate Attention"
        case "INTENT_HIGH"      => "High Demand Area"
        case "INTENT_MEDIUM"    => "Popular Parking Area"
        case "INTENT_LOW"       => "Parking Update"
        case _                  => "Parking Alert"
      }

    val html =
      s"""
<!doctype html>
<html>

<head>

  <meta charset="utf-8" />

  <meta name="viewport"
        content="width=device-width, initial-scale=1" />

  <style>

    body {
      margin: 0;
      padding: 0;
      background: #f4f7fb;
      font-family: Inter, Arial, sans-serif;
      color: #0f172a;
    }

    .container {
      width: 100%;
      padding: 40px 0;
    }

    .card {
      max-width: 640px;
      margin: auto;
      background: #ffffff;
      border-radius: 18px;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      box-shadow: 0 10px 35px rgba(15, 23, 42, 0.08);
    }

    .header {
      background: linear-gradient(135deg, #0f172a, #1e293b);
      padding: 32px;
      color: white;
    }

    .header h1 {
      margin: 0;
      font-size: 24px;
      font-weight: 700;
    }

    .header p {
      margin-top: 8px;
      color: #cbd5e1;
      font-size: 14px;
      line-height: 1.6;
    }

    .content {
      padding: 32px;
    }

    .badge {
      display: inline-block;
      background: $badgeColor;
      color: white;
      padding: 8px 16px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.4px;
      margin-bottom: 24px;
    }

    .message-box {
      background: #f8fafc;
      border-left: 4px solid $badgeColor;
      border-radius: 12px;
      padding: 22px;
    }

    .message-box h2 {
      margin: 0 0 10px 0;
      font-size: 16px;
      color: #0f172a;
    }

    .message-box p {
      margin: 0;
      font-size: 15px;
      color: #334155;
      line-height: 1.8;
    }

    .footer {
      border-top: 1px solid #e2e8f0;
      padding: 20px 32px;
      background: #fafcff;
      color: #94a3b8;
      font-size: 12px;
      line-height: 1.6;
    }

  </style>

</head>

<body>

  <div class="container">

    <div class="card">

      <div class="header">

        <h1>Parking Availability Alert</h1>

        <p>
          Smart parking system detected important parking availability
          updates near your preferred locations.
        </p>

      </div>

      <div class="content">

        <div class="badge">
          $badgeText
        </div>

        <div class="message-box">

          <h2>Notification</h2>

          <p>
            ${notif.message}
          </p>

        </div>

      </div>

      <div class="footer">

        This is an automated real-time parking notification generated by
        the Parking Intelligence Platform.

      </div>

    </div>

  </div>

</body>

</html>
"""

    val subject = "Parking Availability Alert"

    sendHtmlEmail(subject, html, notif.userId)
  }

  // ============================================================
  // COMMON SMTP SEND LOGIC
  // ============================================================

  private def sendHtmlEmail(
      subject: String,
      html: String,
      userId: String
  ): Unit = {

    try {

      val props = new Properties()

      props.put("mail.smtp.auth", "true")
      props.put("mail.smtp.starttls.enable", "true")
      props.put("mail.smtp.host", "smtp.gmail.com")
      props.put("mail.smtp.port", "587")

      val session = Session.getInstance(
        props,
        new Authenticator() {

          override protected def getPasswordAuthentication
              : PasswordAuthentication =
            new PasswordAuthentication(
              Username,
              Password
            )
        }
      )

      val message = new MimeMessage(session)

      message.setFrom(
        new InternetAddress(Username)
      )

      message.setRecipients(
        Message.RecipientType.TO,
        Recipient
      )

      message.setReplyTo(
        Array(new InternetAddress(Username))
      )

      message.setSubject(subject)

      message.setContent(
        html,
        "text/html; charset=utf-8"
      )

      Transport.send(message)

      logger.info(
        s"EmailPublisher: email successfully sent " +
          s"for user $userId"
      )

    } catch {

      case ex: Throwable =>

        logger.warn(
          "EmailPublisher: send failed, " +
            "falling back to file output",
          ex
        )

        try {

          val dir = Paths.get(
            "logs",
            "emails"
          )

          if (!Files.exists(dir)) {
            Files.createDirectories(dir)
          }

          val filename =
            s"${Instant.now().toEpochMilli}_$userId.html"

          val path =
            dir.resolve(filename)

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