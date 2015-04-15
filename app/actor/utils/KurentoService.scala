package actor.utils

import org.kurento.client.{WebRtcEndpoint, MediaPipeline, KurentoClient}

import scala.collection.mutable

object KurentoService {

  lazy val kurento: KurentoClient = KurentoClient.create("ws://localhost:8881/kurento")
  lazy val pipeline: MediaPipeline = kurento.createMediaPipeline()

  // incoming user broadcasts
  val incomingMedia: mutable.HashMap[String, WebRtcEndpoint] = new mutable.HashMap[String, WebRtcEndpoint]

  // outgoing user broadcasts per each user
  val outgoingMedia: mutable.HashMap[(String, String), WebRtcEndpoint] = new mutable.HashMap[(String, String), WebRtcEndpoint]

  def addBroadcast(userId: String, sdpOffer: String) : String = {
    println(s"KurentoService.addBroadcast request from ${userId}")

    // create in stream
    var incoming: WebRtcEndpoint = incomingMedia.get(userId).getOrElse(null)
    if (incoming == null) {
      incoming = new WebRtcEndpoint.Builder(pipeline).build
      incomingMedia.put(userId, incoming)
    }
    return incoming.processOffer(sdpOffer)
  }

  def addViewer(subscribeId: String, userId: String, sdpOffer: String) : String = {
    println(s"Subscribe request from ${userId} to $subscribeId")

    // create in stream
    var incoming: WebRtcEndpoint = incomingMedia.get(subscribeId).getOrElse(null)
    if (incoming == null) {
      incoming = new WebRtcEndpoint.Builder(pipeline).build
      incomingMedia.put(subscribeId, incoming)
    }

    // create out stream
    val outKey = (subscribeId, userId) // host, subscriber
    var outgoing: WebRtcEndpoint = outgoingMedia.get(outKey).getOrElse(null)
    if (outgoing == null) {
      outgoing = new WebRtcEndpoint.Builder(pipeline).build
      outgoingMedia.put(outKey, outgoing)
    }

    incoming.connect(outgoing)

    return outgoing.processOffer(sdpOffer)
  }

  def release(userId: String): Unit = {
    // clear kurento objects
    incomingMedia.get(userId) match {
      case Some(incoming) => incoming.release()
      case None => println("No incoming media to release " + userId)
    }
    for (((hostId, viewerId), outgoing) <- outgoingMedia if hostId == userId) {
      outgoing.release()
    }
  }

  def getStatus(): List[String] = {
    import scala.collection.JavaConverters._

    val childs = pipeline.getChilds().asScala
    val activeMedias = childs.map(media => media.getName)
    return activeMedias.toList
  }

}
