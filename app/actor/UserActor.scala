package actor

import actor.utils.{ClientMessage, Converter, ActorSubscribe, ServerMessage}
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
      // parse
      Converter.toMessage(js) match {
        case Some(clientMessage) => roomRef ! (clientMessage, js)
        case _ => println("ERROR Can't parse message from client " + js.toString())
      }

    case other =>
      log.error("UserActor unhandled: " + other)
  }
}

object UserActor {
  def props(uid: String, room: ActorRef)(out: ActorRef) = Props(new UserActor(uid, room, out))
}