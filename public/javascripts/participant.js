const PARTICIPANT_MAIN_CLASS = 'participant main';
const PARTICIPANT_CLASS = 'participant';

/**
 * Creates a video element for a new participant
 *
 * @param {String} name - the name of the new participant, to be used as tag
 *                        name of the video element.
 *                        The tag of the new element will be 'video<name>'
 * @return
 */
function Participant(name, sendFunction, isLocalUser) {
    var userId = name;
	this.name = name;
    this.sendFunction = sendFunction;
    this.isLocalUser = isLocalUser;

    var servers = null;
    var out = {};

	var container = document.createElement('div');
	container.className = isPresentMainParticipant() ? PARTICIPANT_CLASS : PARTICIPANT_MAIN_CLASS;
	container.id = name;
	var span = document.createElement('span');
    var video = document.createElement('video');
    video.setAttribute("style","width:200px");

    var stream;
	var rtcPeer;

    container.appendChild(video);
    container.appendChild(span);
    container.onclick = switchContainerClass;

	document.getElementById('participants').appendChild(container);

	span.appendChild(document.createTextNode(name));
    video.id = 'video-' + name;
    video.autoplay = true;

	video.controls = false;

    this.start = function(handler) {
        this.handler = handler;
        console.log("Participant:start " + userId + " isLocal:" + this.isLocalUser);
        if (this.isLocalUser) {
            getUserMedia({
                    audio: true,
                    video: true
                }, this.gotLocalStream.bind(this),
                function(e) {
                    alert('getUserMedia() error: ' + e.name);
                });
        } else {
            this.sendFunction(userId, {method:"requestCreateOffer", data:null, broadcastId:userId});
        }
    }

    this.gotLocalStream = function(stream) {
        trace('Received local stream');
        // Call the polyfill wrapper to attach the media stream to this element.
        attachMediaStream(video, stream);
        this.stream = stream;
        var videoTracks = stream.getVideoTracks();
        var audioTracks = stream.getAudioTracks();
        if (videoTracks.length > 0) {
            trace('Using video device: ' + videoTracks[0].label);
        }
        if (audioTracks.length > 0) {
            trace('Using audio device: ' + audioTracks[0].label);
        }
        this.handler();
    }

	function switchContainerClass() {
		if (container.className === PARTICIPANT_CLASS) {
			var elements = Array.prototype.slice.call(document.getElementsByClassName(PARTICIPANT_MAIN_CLASS));
			elements.forEach(function(item) {
					item.className = PARTICIPANT_CLASS;
				});

				container.className = PARTICIPANT_MAIN_CLASS;
			} else {
			container.className = PARTICIPANT_CLASS;
		}
	}

	function isPresentMainParticipant() {
		return ((document.getElementsByClassName(PARTICIPANT_MAIN_CLASS)).length != 0);
	}

    this.getOfferForViewer = function() {
        var servers = null;
        pc1 = new RTCPeerConnection(servers);
        trace('Created local peer connection object pc1');
        pc1.onicecandidate = function(e) {
            onIceCandidate(pc1, e);
        };
        pc1.oniceconnectionstatechange = function(e) {
            onIceStateChange(pc1, e);
        };
        pc1.addStream(stream);
        trace('Added local stream to pc1');

        trace('pc1 createOffer start');
        pc1.createOffer(onCreateOfferSuccess, onCreateSessionDescriptionError);
    }


    function onIceCandidate(pc, event) {
        if (event.candidate) {
            getOtherPc(pc).addIceCandidate(new RTCIceCandidate(event.candidate),
                function() {
                    onAddIceCandidateSuccess(pc);
                },
                function(err) {
                    onAddIceCandidateError(pc, err);
                }
            );
            trace(getName(pc) + ' ICE candidate: \n' + event.candidate.candidate);
        }
    }

    function onAddIceCandidateSuccess(pc) {
        trace(getName(pc) + ' addIceCandidate success');
    }

    function onAddIceCandidateError(pc, error) {
        trace(getName(pc) + ' failed to add ICE Candidate: ' + error.toString());
    }

    function onIceStateChange(pc, event) {
        if (pc) {
            trace(getName(pc) + ' ICE state: ' + pc.iceConnectionState);
            console.log('ICE state change event: ', event);
        }
    }

	this.offerToReceiveVideo = function(offerSdp, wp){
		console.log('Invoking SDP offer callback function');
        this.sendFunction(this.name, offerSdp);

        //console.log('offerSdp ' + JSON.stringify(offerSdp));
        //var msg =  { id : "receiveVideoFrom",
			//	sender : name,
			//	sdpOffer : offerSdp
			//};

		//sendMessage(msg);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);
		container.parentNode.removeChild(container);

        if (this.isLocalUser && this.stream) {
            this.stream.stop();
        }
        try {
            rtcPeer.dispose(); // execution may stop
        } catch (e) {
            console.error("Wired thing in rtc peer dispose");
        }
    };

    this.requestCreateOffer = function(remoteUserId, value) {
        var pc = new RTCPeerConnection(servers);
        out[remoteUserId] = pc;
        pc.addStream(this.stream);
        pc.createOffer(
            function(desc) {
                trace('Created offer\n' + 'desc.sdp');
                pc.setLocalDescription(desc, function() {});
                sendFunction(remoteUserId, {method:"respondCreateOffer", broadcastId:userId, desc:desc});
                console.log("createOffer success");
            },
            onCreateSessionDescriptionError
        );
        console.log("requestCreateOffer");
    }

    this.respondCreateOffer = function(remoteUserId, value) {
        var desc = fixDesc(value.desc);

        var sdpConstraints = {
            'mandatory': {
                'OfferToReceiveAudio': true,
                'OfferToReceiveVideo': true
            }
        };

        rtcPeer = new RTCPeerConnection(servers);
        rtcPeer.onaddstream = gotRemoteStream;
        rtcPeer.setRemoteDescription(desc, function() {});
        rtcPeer.createAnswer(
            function onCreateAnswerSuccess(desc) {
                trace('Created answer:\n' + 'desc.sdp');
                rtcPeer.setLocalDescription(desc, function() {});
                sendFunction(userId, {method:"respondAnswer", broadcastId:userId, desc:desc});
            },
            onCreateSessionDescriptionError,
            sdpConstraints
        );
    }

    this.respondAnswer = function(remoteUserId, value) {
        var desc = fixDesc(value.desc);
        var pc = out[remoteUserId];

        pc.setRemoteDescription(desc, function() {});
        console.log("respondAnswer")
    }

    // keep correct object type after serialization
    function fixDesc(value) {
        var result = new RTCSessionDescription();
        result.sdp = value.sdp;
        result.type = value.type;
        return result;
    }

    function gotRemoteStream(e) {
        // Call the polyfill wrapper to attach the media stream to this element.
        attachMediaStream(video, e.stream);
        trace('rtcPeer received remote stream');
    }

    function onCreateSessionDescriptionError(error) {
        trace('Failed to create session description: ' + error.toString());
    }
}
