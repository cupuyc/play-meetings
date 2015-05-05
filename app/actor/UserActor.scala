package actor

import actor.utils.{ActorSubscribe, ServerMessage}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import play.api.libs.json._

class UserActor(var uid: String, roomRef: ActorRef, out: ActorRef) extends Actor with ActorLogging {

  override def preStart() = {
    roomRef ! ActorSubscribe(uid)
  }
  def receive = LoggingReceive {
    // resend from board to all
    case msg: ServerMessage =>
      out ! msg.toJson
    case js: JsValue if sender == roomRef =>
      out ! js
    case js: JsValue =>
      roomRef ! js
    case other =>
      log.error("UserActor unhandled: " + other)
  }
}

object UserActor {
  def props(uid: String, room: ActorRef)(out: ActorRef) = Props(new UserActor(uid, room, out))
}