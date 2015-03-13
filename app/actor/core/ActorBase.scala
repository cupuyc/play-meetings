package actor.core

import akka.actor.Actor

abstract class ActorBase extends Actor {

  def reportState = {
    val _this = this
    synchronized {
//      val msg = "%s Received request to report state with %d items in mailbox".format(
//        _this, mailboxSize)
//      log.info(msg)
    }
    //Actor.actor { _this ! ReportState }
  }
}
