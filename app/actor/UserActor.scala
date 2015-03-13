package actor

import actor.utils.{ServerMessage, ActorSubscribe}
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.event.LoggingReceive
import play.api.libs.json._
import akka.actor.ActorRef
import akka.actor.Props

class UserActor(var uid: String, boardRef: ActorRef, out: ActorRef) extends Actor with ActorLogging {

  //val outQueue = ListBuffer[JsValue]()

  override def preStart() = {
    boardRef ! ActorSubscribe(uid)
  }
  def receive = LoggingReceive {
    // resend from board to all
    case msg: ServerMessage =>
      out ! msg.toJson
    case js: JsValue if sender == boardRef =>
      out ! js
    case js: JsValue =>
      boardRef ! js
    case other =>
      log.error("UserActor unhandled: " + other)
  }
}

object UserActor {
  def props(uid: String, board: ActorRef)(out: ActorRef) = Props(new UserActor(uid, board, out))
}