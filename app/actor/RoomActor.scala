package actor

import java.io.{PrintWriter, StringWriter}

import actor.utils._
import akka.actor._
import akka.event.LoggingReceive
import play.api.Logger
import play.api.libs.json._

import scala.collection.mutable


class RoomActor(val roomName: String = "Default") extends Actor with ActorLogging {

  /**
   * Users pending verification. Wait for client to send "join" message.
   */
  var pendingUsers = mutable.HashMap[ActorRef, String]()

  /**
   * Users approved to be part of meeting.
   */
  var users = mutable.HashMap[ActorRef, UserInfo]()

  /**
   * Room state is key-value storage. Each chat message or broadcast activity is represented in this map.
   */
  var roomState = mutable.HashMap[String, JsValue]()

  override def preStart() = {
    println("HostActor start " + roomName + " " + self)
  }

  override def postStop() = {
    Logger.info("HostActor stop " + roomName)
  }

  def broadcastStatus() = {
    val allUsers = users.values.map(user => user.name).mkString(", ")
    for ((userAct, user) <- users) {
      userAct ! new StatusMessage("You are " + user.id + " " + user.name + ". In lobby.", s"Users: $allUsers");
    }
  }

  def broadcastAll(value: JsValue, doPersist: Boolean = false) = {
    for ((userAct, user) <- users) {
      userAct ! value
    }
  }

  def broadcastMessage(message: ServerMessage) = {
    broadcastAll(message.toJson)
  }

  def changeUserName(user: UserInfo, name: String) = {
    user.name = name
    broadcastMessage(new ChangeBracketMessage(Prefix.USER, user.id, user.toJson))
  }

  def getUserActor(userId: String): Option[ActorRef] = {
    for ((userAct, user) <- users) {
      if (user.id == userId) {
        return Some(userAct)
      }
    }
    return None
  }

  def receive = LoggingReceive {

    case "print" => println(roomState)

    case (cm : ClientMessage, js: JsValue) =>
      try {
        users.get(sender) match {
          case Some(senderUser) =>
            cm match {
              case Ping(s) => // ping to keep Heroku web socket active

              case ChangeName(name) => changeUserName(senderUser, name)

              case ChangeProperty(key, value) =>
                if (value == JsNull) {
                  roomState.remove(key)
                } else {
                  roomState.put(key, value)
                }
                broadcastAll(js)

              case SendTo(toUserId, fromUserId, value, truename) =>
                getUserActor(toUserId) match {
                  case Some(actorRef) => actorRef ! js
                  case None => println("ERROR Cant find send to user " + js.toString())
                }

              case ClearChat(s) =>
                broadcastAll(new ChatClear().toJson, true)

              //            case SendChat(data) =>
              //              broadcastAll(new ChatMessage(senderUser.name, getStringValue(js, "data")).toJson, true)
            }

          case None =>
            cm match {
              case JoinMe(name) =>
                pendingUsers.get(sender) match {
                  case Some(uid) =>
                    pendingUsers.remove(sender())
                    doUserJoin(uid, name)
                  case _ => println("ERROR:No pending user found")
                }

              case Ping(s) =>

              case _ => println("ERROR Message from undefined user or not joined yet")
            }
        }
      } catch {
        // TODO handle error in more Akka way
        case e: Exception =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          println(sw.toString)
      }

    // ##### USER CONNECTED #####
    case ActorSubscribe(uid, name) =>
      println("User connected " + uid)
      pendingUsers.put(sender, uid)


    // ##### USER DISCONNECTED #####
    case Terminated(actorRef) =>
      self ! ActorLeave(actorRef)

    case ActorLeave(actorRef) =>
      doUserLeave(actorRef)


    case AdminStatus =>
      sender ! AdminStatusReply(
        roomName,
        users.values.map(userInfo => "(" + userInfo.id + ")" + userInfo.name),
        users.size)
  }

  def doUserJoin(uid: String, name: String): Unit = {
    println("User joined " + uid)
    // sender is joined user actorRef
    val joinUser = new UserInfo(uid, sender, name);

    context watch sender

    // #1 send who user is and server time
    sender ! new ConnectedMessage(uid, 0)
    sender ! new ChangeBracketMessage(Prefix.USER, joinUser.id, joinUser.toJson)

    // #2 notify all alive users and send users data to connected user
    for ((userAct, user) <- users) {
      sender ! new ChangeBracketMessage(Prefix.USER, user.id, user.toJson)
      userAct ! new ChangeBracketMessage(Prefix.USER, joinUser.id, joinUser.toJson)
    }
    users.put(sender, joinUser)

    // #3 send whole room state to connected user
    for ((key, value) <- roomState) {
      sender ! new ChangeMessage(key, value)
    }
  }

  def doUserLeave(actorRef: ActorRef): Unit = {
    pendingUsers.remove(actorRef)
    users.remove(actorRef) match {
      case Some(removeUser) =>
        // send leave message to all
        val hasBroadcast = roomState.remove(Prefix.BROADCAST + "." + removeUser.id).isDefined
        for ((userAct, user) <- users) {
          if (hasBroadcast) {
            userAct ! new ChangeBracketMessage(Prefix.BROADCAST, removeUser.id, null)
          }
          userAct ! new ChangeBracketMessage(Prefix.USER, removeUser.id, null)
        }

      case _ => println("Error: leave user wasn't found " + actorRef)
    }
  }
}

class UserInfo(var id: String,
               var actorRef: ActorRef,
               var name: String = "New User") {
  def toJson = Json.obj("name" -> name, "id" -> id)
}
