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

@Singleton
class EventStreamController @Inject() (
    val controllerComponents: ControllerComponents,
    manager: EventStreamManager
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

  /** WebSocket endpoint: connecting starts publishing; disconnect stops. Send
    * "stop" to terminate from the client side.
    */
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
}
