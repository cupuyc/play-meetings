package actor.game

import actor.utils._
import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import org.kurento.client.{MediaPipeline, KurentoClient, WebRtcEndpoint}
import play.api.Logger
import play.api.libs.json.{Json, JsObject, JsString, JsValue}
import play.libs.Akka

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class HostActor(val roomName: String = "Default") extends Actor with ActorLogging {

  val kurento: KurentoClient = KurentoClient.create("ws://192.168.1.153:8881/kurento")
  val pipeline: MediaPipeline = kurento.createMediaPipeline()

  // incoming user broadcasts
  val incomingMedia: mutable.HashMap[String, WebRtcEndpoint] = new mutable.HashMap[String, WebRtcEndpoint]

  // outgoing user broadcasts per each user
  val outgoingMedia: mutable.HashMap[(String, String), WebRtcEndpoint] = new mutable.HashMap[(String, String), WebRtcEndpoint]

  var users = mutable.HashMap[ActorRef, UserInfo]()
  var pendingUsers = mutable.HashMap[ActorRef, String]()

  var roomState = mutable.HashMap[String, JsValue]()

  var statusInterval: Option[Cancellable] = None;

  override def preStart() = {
    println("HostActor start " + roomName + " " + self)
    //statusInterval = Some(Akka.system().scheduler.schedule(0 seconds, 0.1 seconds)(logStatus))
  }


  override def postStop() = {
    Logger.info("HostActor stop " + roomName)
    statusInterval match {
      case Some(i) if !i.isCancelled => i.cancel()
      case _ => ""
    }
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

  def doUserBroadcast(user: UserInfo, js: JsValue) = {
    println(s"Broadcast request from ${user.id}")
    val sdpOffer = (js \ "sdpOffer").as[String]

    // create in stream
    var incoming: WebRtcEndpoint = incomingMedia.get(user.id).getOrElse(null)
    if (incoming == null) {
      incoming = new WebRtcEndpoint.Builder(pipeline).build
      incomingMedia.put(user.id, incoming)
    }

    sender !  new SdpAnswerMessage(user.id, incoming.processOffer(sdpOffer))
  }


  def doUserSubscribe(user: UserInfo, js: JsValue) = {
    val subscribeId = (js \ "subscribeId").as[String]
    println(s"Subscribe request from ${user.id} to $subscribeId")
    val sdpOffer = (js \ "sdpOffer").as[String]

    // create in stream
    var incoming: WebRtcEndpoint = incomingMedia.get(subscribeId).getOrElse(null)
    if (incoming == null) {
      incoming = new WebRtcEndpoint.Builder(pipeline).build
      incomingMedia.put(subscribeId, incoming)
    }

    // create out stream
    val outKey = (subscribeId, user.id) // host, subscriber
    var outgoing: WebRtcEndpoint = outgoingMedia.get(outKey).getOrElse(null)
    if (outgoing == null) {
      outgoing = new WebRtcEndpoint.Builder(pipeline).build
      outgoingMedia.put(outKey, outgoing)
    }

    incoming.connect(outgoing)

    sender !  new SdpAnswerMessage(subscribeId, outgoing.processOffer(sdpOffer))
  }

  def receive = LoggingReceive {

    case "print" => println(roomState)

    case js: JsValue =>
//      try {
        //users foreach { user => user._1 ! js}
        val messageTuple = (getStringValue(js, "messageType"), getStringValue(js, "data"))

        users.get(sender) match {
          case Some(senderUser) =>
            messageTuple match {
              // user may change his name
              case ("changeName", _) =>
                val name = (js \ "name").as[String]
                changeUserName(senderUser, name)

              // user change state
              case ("change", _) =>
                val key = (js \ "key").as[String]
                val value = js \ "value"

                roomState.put(key, value)
                broadcastAll(js)

              case ("command", "broadcast") =>
                doUserBroadcast(senderUser, js)

              case ("command", "subscribe") =>
                doUserSubscribe(senderUser, js)

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
                    doJoinUser(uid, name)
                  case _ => println("ERROR:No pending user found")
                }

              case _ =>
                println("ERROR Message from none")
          }
        }

//      } catch {
//        case e: Exception =>
//          println("Error " + e.getStackTrace().toString)
//      }

    // ##### USER CONNECTED #####
    case ActorSubscribe(uid, name) =>
      pendingUsers.put(sender, uid)


    // ##### USER DISCONNECTED #####
    case Terminated(actorRef) =>
      self ! ActorLeave(actorRef)

    case ActorLeave(actorRef) =>
      pendingUsers.remove(actorRef)
      users.remove(actorRef) match {
        case Some(leaveUser) =>
          for ((userAct, user) <- users) {
            userAct ! new ChangeBracketMessage(Prefix.USER, leaveUser.id, null)
          }
        case _ => println("Error: leave user wasn't found " + actorRef)
      }


    case AdminStatus =>
      sender ! AdminStatusReply(roomName, users.values.map(userInfo => "(" + userInfo.id + ")" + userInfo.name), 0)
  }

  def doJoinUser(uid: String, name: String): Unit = {
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
}

class UserInfo(var id: String,
               var actorRef: ActorRef,
               var name: String = "New User") {
  def toJson = Json.obj("name" -> name, "id" -> id)
}
