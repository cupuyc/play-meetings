package actor.utils

import java.math.BigDecimal

import akka.actor.ActorRef
import play.api.libs.json._

object Prefix {
  var USER = "user"
  var CHAT = "chat"
}


case class ActorMessage(uuid: String, s: String)
case class ActorSubscribe(uid: String, name: String = "")
case class ActorLeave(actorRef: ActorRef)

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
        case js: JsValue => js
        case _ => new JsUndefined("Unknown obj")
      }
      result = result + (field.getName -> jsValue )
    }
    result
  }
}

class ConnectedMessage(var pid: String, serverTime: Int) extends ServerMessage {
  var messageType: String = "youAre"
}

class ChangeBracketMessage(val bracket: String, val id : String, var value: JsValue) extends ServerMessage {
  var key = bracket + "." + id
  var messageType: String = "change"
}

class ChangeMessage(val key: String, var value: JsValue) extends ServerMessage {
  var messageType: String = "change"
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

class SdpAnswerMessage(var id : String, var sdpAnswer: String) extends ServerMessage {
  var messageType: String = "sdpAnswerMessage"
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
