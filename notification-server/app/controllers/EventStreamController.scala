package controllers

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{BaseController, ControllerComponents, WebSocket}
import services.EventStreamManager

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.Future
import java.time.{Instant, Duration => JDuration, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import repositories.UserActivityEventRepository
import play.api.libs.json.JsObject

@Singleton
class EventStreamController @Inject() (
    val controllerComponents: ControllerComponents,
    manager: EventStreamManager,
    userRepo: UserActivityEventRepository
)(implicit ec: ExecutionContext, mat: Materializer)
    extends BaseController {

  def start() = Action { implicit request =>
    val bodyJson: Option[JsValue] = request.body.asJson

    val ratePerSecond =
      bodyJson.flatMap(js => (js \ "ratePerSecond").asOpt[Int])
    val topic = bodyJson.flatMap(js => (js \ "topic").asOpt[String])
    val batchEveryMillis =
      bodyJson.flatMap(js => (js \ "batchEveryMillis").asOpt[Int])

    val session = manager.start(
      ratePerSecondOpt = ratePerSecond,
      topicOpt = topic,
      batchEveryMillisOpt = batchEveryMillis
    )

    Created(
      Json.obj(
        "status" -> "started",
        "stream" -> Json.toJson(session),
        "stop" -> routes.EventStreamController.stop(session.streamId).url,
        "ws" -> routes.EventStreamController.ws().webSocketURL(request.secure)
      )
    )
  }

  def stop(streamId: String) = Action {
    val stopped = manager.stop(streamId)
    if (stopped) Ok(Json.obj("status" -> "stopped", "streamId" -> streamId))
    else NotFound(Json.obj("status" -> "not_found", "streamId" -> streamId))
  }

  def status(streamId: String) = Action {
    manager.status(streamId) match {
      case Some(s) => Ok(Json.toJson(s))
      case None    =>
        NotFound(Json.obj("status" -> "not_found", "streamId" -> streamId))
    }
  }

  def ws() = WebSocket.accept[String, String] { request =>
    val running = new AtomicBoolean(true)

    val rate = request.getQueryString("ratePerSecond").flatMap(_.toIntOption)
    val topic = request.getQueryString("topic")
    val batchEveryMillis =
      request.getQueryString("batchEveryMillis").flatMap(_.toIntOption)

    val session = manager.start(
      ratePerSecondOpt = rate,
      topicOpt = topic,
      batchEveryMillisOpt = batchEveryMillis
    )

    val sink: Sink[String, _] = Sink.foreach { msg =>
      if (msg.trim.equalsIgnoreCase("stop")) {
        running.set(false)
        manager.stop(session.streamId)
      }
    }

    val source: Source[String, _] =
      Source
        .tick(0.seconds, 1.second, ())
        .takeWhile(_ => running.get())
        .map { _ =>
          val published =
            manager.status(session.streamId).map(_.published).getOrElse(0L)
          Json
            .obj(
              "type" -> "heartbeat",
              "streamId" -> session.streamId,
              "topic" -> session.topic,
              "ratePerSecond" -> session.ratePerSecond,
              "published" -> published
            )
            .toString()
        }

    Flow.fromSinkAndSourceCoupled(sink, source).watchTermination() {
      (_, done) =>
        done.onComplete { _ =>
          running.set(false)
          manager.stop(session.streamId)
        }
    }
  }

  /** Traffic analytics endpoint: returns event counts grouped by hour for the
    * given time range (query params: `start` and `end` as ISO instants). If not
    * provided, defaults to last 24 hours.
    */
  def trafficAnalytics() = Action.async { implicit request =>
    val qs = request.queryString
    val now = Instant.now()
    val start = request
      .getQueryString("start")
      .flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      .getOrElse(now.minus(1, ChronoUnit.DAYS))
    val end = request
      .getQueryString("end")
      .flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      .getOrElse(now)

    userRepo.countByHour(start, end).map { rows =>
      // rows: Seq[(hourIsoString, count)] where hourIsoString is like 2026-05-19T03:00:00
      val parseFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
      val outFmt = DateTimeFormatter.ofPattern("MMM d, EEEE h:mm a")

      def dayPart(hourOfDay: Int): String =
        if (hourOfDay >= 5 && hourOfDay < 12) "Morning"
        else if (hourOfDay >= 12 && hourOfDay < 17) "Afternoon"
        else if (hourOfDay >= 17 && hourOfDay < 21) "Evening"
        else "Night"

      val enriched = rows.map { case (hourIso, cnt) =>
        val ldt = try LocalDateTime.parse(hourIso, parseFmt) catch { case _: Throwable => LocalDateTime.ofInstant(Instant.now(), java.time.ZoneOffset.UTC) }
        val label = ldt.format(outFmt)
        Json.obj(
          "hourIso" -> hourIso,
          "label" -> label,
          "dayPart" -> dayPart(ldt.getHour),
          "count" -> cnt
        )
      }

      val total = rows.map(_._2).sum
      val avg = if (rows.nonEmpty) total.toDouble / rows.size else 0.0

      val peak = enriched.headOption

      Ok(
        Json.obj(
          "start" -> start.toString,
          "end" -> end.toString,
          "totalEvents" -> total,
          "averagePerHour" -> BigDecimal(avg).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          "peakHour" -> peak,
          "hours" -> enriched
        )
      )
    }
  }
}
