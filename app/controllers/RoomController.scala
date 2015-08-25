package controllers

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import actor.{RoomActor, UserActor}
import actor.utils.{AdminStatus, AdminStatusReply}
import akka.actor.{ActorRef, Actor, Props}
import akka.pattern.ask
import play.libs.Akka
import scala.collection.mutable
import scala.concurrent.Future

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

class UserRequest[A](val user: AuthUser, request: Request[A]) extends WrappedRequest[A](request)

object UserAction extends ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {

  val UID = "uid"
  var counter = new AtomicInteger(0)

  def transform[A](request: Request[A]) = Future.successful {
    val uid = request.session.get(UID).getOrElse(counter.getAndIncrement().toString)
    new UserRequest(AuthUser(uid), request)
  }
}

case class AuthUser(uid: String)

class UserAuthRequest[A](val user: AuthUser, request: Request[A]) extends WrappedRequest[A](request) {

}


object RoomController extends Controller {

  val DEFAULT_ROOM_NAME = "default"

  val roomActorRefs = new mutable.HashMap[String, ActorRef]()

  def index() = UserAction { implicit request =>
    // UID is not used right now
    val uid: String = request.user.uid

    Ok(views.html.room(uid)).withSession(request.session + (UserAction.UID -> uid))
  }

  def joinRoom(room: String = DEFAULT_ROOM_NAME) = UserAction { implicit request =>
    val uid: String = request.user.uid

    Logger.debug("User visited room:" + room)
    Ok(views.html.room(uid)).withSession(request.session + (UserAction.UID -> uid))
  }

  def logout = Action { implicit request =>
    Redirect("/").withNewSession
  }

  def admin = Action.async {
    import akka.util.Timeout
    import scala.concurrent.duration._
    implicit val timeout = Timeout(5 seconds)

    (getRoomActor(DEFAULT_ROOM_NAME) ? AdminStatus).map {
      case response: AdminStatusReply =>
        Ok(views.html.admin(
          List[String](
            "Room: " + response.name,
            "Users count: " + response.users.size,
            "Users: " + response.users.mkString(","),
            "Messages count: " + response.chatSize
          )
        )
      )
    }
  }

  def stream(room: String = DEFAULT_ROOM_NAME) = WebSocket.tryAcceptWithActor[JsValue, JsValue] { implicit request =>
    val uid = UUID.randomUUID().toString().substring(0, 4)

    Future.successful(Right(UserActor.props(uid, getRoomActor(room))))
  }

  /* HELPERS */
  def getRoomActor(room: String) = {
    val roomActor = roomActorRefs.get(room).getOrElse(createRoomActor(room))
    roomActorRefs.put(room, roomActor)
    roomActor
  }

  def createRoomActor(room: String) = Akka.system().actorOf(Props(new RoomActor(room)), name = room)
}


