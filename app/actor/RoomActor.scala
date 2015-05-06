package actor

import actor.utils._
import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import play.api.Logger
import play.api.libs.json._
import play.libs.Akka

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class RoomActor(val roomName: String = "Default") extends Actor with ActorLogging {


  var users = mutable.HashMap[ActorRef, UserInfo]()
  var pendingUsers = mutable.HashMap[ActorRef, String]()

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

  def getStringValue(js: JsValue, prop: String) = {
    (js \ prop) match {
      case JsString(value) => value
      case _ => ""
    }
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

    case js: JsValue =>
      try {
        //users foreach { user => user._1 ! js}
        val messageTuple = (getStringValue(js, "messageType"), getStringValue(js, "data"))

        users.get(sender) match {
          case Some(senderUser) =>
            messageTuple match {
              case ("ping", _) =>

              // user may change his name
              case ("changeName", _) =>
                val name = (js \ "name").as[String]
                changeUserName(senderUser, name)

              // user change state
              case ("change", _) =>
                val key = (js \ "key").as[String]
                val value = js \ "value"
                if (value == JsNull) {
                  roomState.remove(key)
                } else {
                  roomState.put(key, value)
                }
                broadcastAll(js)

              case ("sendTo", _) =>
                val toUserId = (js \ "toUserId").as[String]
                getUserActor(toUserId) match {
                  case Some(actorRef) => actorRef ! js
                  case None => println("ERROR Cant find send to user " + js.toString())
                }

              case ("command", "clear") =>
                  broadcastAll(new ChatClear().toJson, true)
                // TODO delete
                // deleteMessages(0L, true)
              case ("command", _) =>
                  broadcastAll(new ChatMessage(senderUser.name, (js \ "data").as[String]).toJson, true)

              case _ => println("ERROR Undefined messageType in " + js.toString())
            }
          case _ =>
            messageTuple match {
              // user send initial info during connect
              // name for now
              case ("join", _) =>
                pendingUsers.get(sender) match {
                  case Some(uid) =>
                    val name = (js \ "name").as[String]
                    pendingUsers.remove(sender())
                    doUserJoin(uid, name)
                  case _ => println("ERROR:No pending user found")
                }
              case ("ping", _) =>

              case _ =>
                println("ERROR Message from none")
          }
        }

      } catch {
        case e: Exception =>
          println("Error " + e.getStackTrace().toString)
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
      val onlineUsers = users.values.map(userInfo => "(" + userInfo.id + ")" + userInfo.name)

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
