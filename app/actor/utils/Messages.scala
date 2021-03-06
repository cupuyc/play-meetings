package actor.utils

import java.math.BigDecimal

import akka.actor.ActorRef
import play.api.libs.json._

import scala.collection.parallel.mutable

object Prefix {
  var USER = "user"
  var CHAT = "chat"
  var BROADCAST = "broadcast"
}


case class ActorMessage(uuid: String, s: String)
case class ActorSubscribe(uid: String, name: String = "")
case class ActorLeave(actorRef: ActorRef)

case class AdminStatus()
case class AdminStatusReply(name: String, users: Iterable[String], chatSize: Int)


abstract sealed class ServerMessage {

  var messageType: String

  def toJson = {
    var result = new JsObject(Map())
    for (field <- this.getClass.getDeclaredFields) yield {
      field.setAccessible(true)
      val value = field.get(this).asInstanceOf[Any]
      val jsValue:JsValue = value match {
        case n: Integer => new JsNumber(new BigDecimal(n))
        case s: String => JsString(s)
        case b: Boolean => JsBoolean(b)
        case js: JsValue => js
        case null => JsNull
        case _ =>
          JsString("Unknown obj")
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



abstract sealed class ClientMessage {

}

// case class should not be empty
case class JoinMe(name: String) extends ClientMessage
case class Ping(fromUserId: String) extends ClientMessage
case class ChangeName(name: String) extends ClientMessage
case class ChangeProperty(key: String, value: JsValue) extends ClientMessage
case class SendTo(toUserId: String, fromUserId: String, value: JsValue, truename: String) extends ClientMessage
case class ClearChat(fromUserId: String) extends  ClientMessage


object Converter {

  val incomingMessageMap = scala.collection.mutable.Map[String, Reads[ClientMessage]]()

  registerClientMessage("join", Json.reads[JoinMe])
  registerClientMessage("ping", Json.reads[Ping])
  registerClientMessage("changeName", Json.reads[ChangeName])
  registerClientMessage("change", Json.reads[ChangeProperty])
  registerClientMessage("sendTo", Json.reads[SendTo])
  registerClientMessage("clearChat", Json.reads[ClearChat])

  def registerClientMessage(key: String, reads: Reads[_]) {
    incomingMessageMap.put(key, reads.asInstanceOf[Reads[ClientMessage]])
  }

  def toJson(m: ServerMessage) = m.toJson

  def toMessage(js: JsValue): Option[ClientMessage] = {
    val messageTuple = (getStringValue(js, "messageType"), getStringValue(js, "data"))
    messageTuple match {
      case (messageType, _) =>
        incomingMessageMap.get(messageType) match {
          case Some(reads) =>
            js.validate(reads).asOpt
          case None => None
        }
      case _ => None
    }
  }

  def getStringValue(js: JsValue, prop: String) = {
    (js \ prop).getOrElse(JsString("")) match {
      case JsString(value) => value
      case _ => ""
    }
  }
}


