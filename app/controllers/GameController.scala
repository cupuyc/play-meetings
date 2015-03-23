package controllers

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import actor.game.HostActor
import actor.game.UserActor
import actor.utils.{AdminStatus, AdminStatusReply}
import akka.actor.{ActorRef, Actor, Props}
import play.libs.Akka
import scala.collection.mutable
import scala.concurrent.Promise
import scala.concurrent.Future

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.WebSocket
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

object GameController extends Controller {

  lazy val defaultHostActorRef = Akka.system().actorOf(Props(new HostActor("Lobby")), name = "GameHost")

  val hostActorRefs = new mutable.HashMap[String, ActorRef]()

  val UID = "uid"
  var counter = new AtomicInteger(0)

  def index() = Action { implicit request =>
    val uid: String = request.session.get(UID) match {
      case Some(value) => value
      case _ => counter.getAndIncrement().toString
    }
    Logger.debug("Joined uid " + uid.toString)
    Ok(views.html.game(uid)).withSession(request.session + (UID -> uid))
  }

  def logout = Action { implicit request =>
    Redirect("/").withNewSession
  }

  def admin = Action.async {
    implicit request =>
      val p = Promise[AdminStatusReply]
      val replyTo = Akka.system().actorOf(Props(new Actor {
        def receive = {
          case reply: AdminStatusReply =>
            p.success(reply)
            context.stop(self)
        }
      }))
      defaultHostActorRef.tell(msg = AdminStatus, sender = replyTo)
      //transforming the actor response to Play result
      p.future.map(
        response => {
          Ok(views.html.admin(
            List[String](
              "Host: " + response.name,
              "Users count: " + response.users.size,
              "Users: " + response.users.mkString(","),
              "Messages count: " + response.chatSize
            )
          ))
        }
      )
  }

  def stream(room: String = "default") = WebSocket.tryAcceptWithActor[JsValue, JsValue] { implicit request =>
    val uid = UUID.randomUUID().toString().substring(0, 4)
    Future.successful(Right(UserActor.props(uid, defaultHostActorRef)))
//    Future.successful(request.session.get(UID) match {
//      case None => Left(Forbidden)
//      case Some(uid) =>
//        Right(UserActor.props(uid, defaultHostActorRef))
//    })
  }
}


