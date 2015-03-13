package actor.utils

import java.math.BigDecimal

import actor.utils.ServerMessage
import akka.actor.ActorRef
import play.api.libs.json._

import scala.collection.immutable.HashMap


case class ActorMessage(uuid: String, s: String)
case class ActorSubscribe(uid: String, name: String = "")
case class ActorLeave(actorRef: ActorRef)
case class ActorReset()
case class ActorJoinGame(gameRef: Option[ActorRef], name: String)

case class AdminStatus()
case class AdminStatusReply(name: String, users: Iterable[String], chatSize: Int)


abstract class ServerMessage {

  var messageType: String

  def toJson = {
    var result = new JsObject(Seq())
    for (field <- this.getClass.getDeclaredFields) yield {
      field.setAccessible(true)
      val value = field.get(this).asInstanceOf[Any]
      val jsValue:JsValue = value match {
        case n: Integer => new JsNumber(new BigDecimal(n))
        case s: String => JsString(s)
        case b: Boolean => JsBoolean(b)
        case _ => new JsUndefined("Unknown obj")
      }
      result = result + (field.getName -> jsValue )
    }
    result
  }
}

class ConnectedMessage(var pid: String, activeGame: String = "") extends ServerMessage {
  var messageType: String = "youAre"
}

class PainterMessage(var pid : String,
  var name: String,
  var size: Number,
  var color: String) extends ServerMessage {
  var messageType: String = "painter"
}

class UserMessage(var pid : String, var name: String) extends ServerMessage {
  var messageType: String = "painter"
}

class UserDisconnectMessage(var pid : String) extends ServerMessage {
  var messageType: String = "disconnected"
}

class ChatMessage(var name : String, var message: String) extends ServerMessage {
  var messageType: String = "chatMessage"
}

class UserCommand(var data: String) extends ServerMessage {
  var messageType: String = "command"
}

class ChatClear() extends ServerMessage {
  var messageType: String = "chatClear"
}


class UserConnectedMessage(var pid : String,
                    var name: String) extends ServerMessage {
  var messageType: String = "connected"
}

class StatusMessage(val local : String, val all: String) extends ServerMessage {
  var messageType: String = "status"
}


object Converter {

  implicit def messageToString(m: ServerMessage) = m.toJson

//  implicit def stringToMessage(s: String) = Serve
}
