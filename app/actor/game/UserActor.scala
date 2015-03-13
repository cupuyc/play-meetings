package actor.game

import actor.utils.{ActorJoinGame, ActorSubscribe, ServerMessage}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import play.api.libs.json._

class UserActor(var uid: String, boardRef: ActorRef, out: ActorRef) extends Actor with ActorLogging {

  //val outQueue = ListBuffer[JsValue]()
  var gameOption: Option[ActorRef] = None

  override def preStart() = {
    boardRef ! ActorSubscribe(uid)
  }

  def receive = LoggingReceive {
    case ActorJoinGame(gameOption, name) =>
      this.gameOption = gameOption
      gameOption match {
        case Some(ref) =>
          ref ! ActorSubscribe(uid, name)
        case None =>
          boardRef ! ActorSubscribe(uid, name)
      }

    // resend from board to all
    case msg: ServerMessage =>
      out ! msg.toJson
    case js: JsValue if sender == boardRef =>
      out ! js
    case js: JsValue if gameOption.contains(sender) =>
      out ! js
    case js: JsValue =>
      gameOption match {
        case Some(gameRef) =>
          gameRef ! js
        case None =>
          boardRef ! js
      }
    case other =>
      log.error("UserActor unhandled: " + other)
  }
}

object UserActor {
  def props(uid: String, board: ActorRef)(out: ActorRef) = Props(new UserActor(uid, board, out))
}