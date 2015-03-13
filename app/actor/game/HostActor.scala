package actor.game

import actor.utils._
import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import play.api.Logger
import play.api.libs.json.{JsString, JsValue}
import play.libs.Akka

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


case class Evt(data: JsValue)

/**
 *
 * @param events - chat messages
 */
case class HostActorState(events: List[JsValue] = Nil) {

  def updated(evt: Evt): HostActorState = copy(evt.data :: events)
  def resetChat():HostActorState = new HostActorState(Nil)
  def size: Int = events.length
  override def toString: String = events.reverse.toString

}

class HostActor(val roomName:String = "Default") extends Actor with ActorLogging {

  var users = mutable.HashMap[ActorRef, UserInfo]()

  var statusInterval:Option[Cancellable] = None;

  def logStatus = {
    println("recoveryFinished" );
  }

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

  var state = HostActorState()

  def persistHanler(event: Evt): Unit = {
    // do nothing when event persisted
    //Logger.debug("Saved " + event.data.toString())
  }

  def numEvents =
    state.size

  val receiveRecover: Receive = {
    case event: Evt =>
      (event.data \ "messageType") match {
//        case JsString("chatClear") =>
//          state = state.resetChat()
        case _ =>
          state = state.updated(event)
      }
    case SnapshotOffer(_, snapshot: HostActorState) =>
      state = snapshot
  }

  def recoveryCompleted(): Unit = {
    Logger.info("Recovery complete " + roomName)
  }

  def broadcastStatus() = {
    val allUsers = users.values.map(user => user.name).mkString(", ")
    for ((userAct, user) <- users) {
      userAct ! new StatusMessage("You are " + user.uid + " " + user.name + ". In lobby.", s"Users: $allUsers");
    }
  }

  def broadcastAll(value: JsValue, doPersist: Boolean = false) = {
    if (doPersist) {
      state = state.updated(Evt(value))
    }
    for ((userAct, user) <- users) {
      userAct ! value
    }
  }

  def receive = LoggingReceive {
    case RecoveryCompleted => recoveryCompleted()
    case "print" => println(state)

    case js: JsValue =>
      users foreach { user => user._1 ! js}

      users.get(sender) match {
        case Some(senderUser) =>
          (js \ "messageType") match {
            case JsString("change") =>
              senderUser.name = (js \ "name").as[String];
              broadcastStatus()
            case JsString("move") =>
              broadcastAll(js)
            case JsString("trace") =>
              broadcastAll(js)
            case JsString("command") =>
              (js \ "data") match {
                case JsString("clear") =>
                  broadcastAll(new ChatClear().toJson, true)
                  // TODO delete
                  // deleteMessages(0L, true)
                  state = state.resetChat()
                case _ =>
                  broadcastAll(new ChatMessage(senderUser.name, (js \ "data").as[String]).toJson, true)
              }
            case _ => println("ERROR Undefined messageType in " + js.toString())
          }
        case _ => println("ERROR Message from none")
      }


    // ##### USER CONNECTED #####
    case ActorSubscribe(uid, name) =>
      // sender is joined user actorRef
      val joinUser = new UserInfo(uid, sender);
      users.put(sender, joinUser)

      context watch sender

      // #1 send who user is
      sender ! new ConnectedMessage(uid)

      // #2 send all alive users
      for ((userAct, user) <- users) {
        sender ! new UserMessage(user.uid, user.name)
        userAct ! new UserMessage(joinUser.uid, joinUser.name)
      }

      // #3 send all chat messages
      for (event <- state.events.reverse) {
        sender ! event
      }

      broadcastStatus()

    // ##### USER DISCONNECTED #####
    case Terminated(actorRef) =>
      self ! ActorLeave(actorRef)

    case ActorLeave(actorRef) =>
      users.remove(actorRef) match {
        case Some(leaveUser) =>
          for ((userAct, user) <- users) {
            userAct ! new UserDisconnectMessage(leaveUser.uid)
          }
        case _ =>
      }
      broadcastStatus()

    case AdminStatus =>
      sender ! AdminStatusReply(roomName, users.values.map(userInfo => "(" + userInfo.uid + ")" + userInfo.name), state.size)
  }
}

class UserInfo(var uid: String,
    var actorRef: ActorRef,
    var name: String = "New User")
