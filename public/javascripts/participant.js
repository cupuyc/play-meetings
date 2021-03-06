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

    var out = {};

    if (!this.isLocalUser){
        var container = document.createElement('div');
        var video = document.createElement('video');
        container.appendChild(video);
        var buttons = document.createElement('div');
        container.appendChild(buttons);
        var nameSpan = document.createElement('span');
        buttons.appendChild(nameSpan);
        document.getElementById('participants').appendChild(container);

        container.className = isPresentMainParticipant() ? PARTICIPANT_CLASS : PARTICIPANT_MAIN_CLASS;
        container.id = name;
        container.onclick = switchContainerClass;
        video.id = 'video-' + name;
        video.autoplay = true;
        video.controls = false;
        buttons.className = "btn-group video-control";
        nameSpan.className = "username";
    } else {
        video = document.getElementById("webcam-me");
        var container = video.parentNode;
        video.id = 'video-' + name;
        video.controls = false;
    }
    var stream;
    var rtcPeer;
    var isDescriptionFinished = false;
    var candidatesMap = {};


    // initialise a configuration for one stun server
    var servers = {
        iceServers: [
            {url: "stun:104.130.195.95"},
            {
                url: 'turn:numb.viagenie.ca',
                credential: 'muazkh',
                username: 'webrtc@live.com'
            },
            {
                url: 'turn:192.158.29.39:3478?transport=udp',
                credential: 'JZEOEt2V3Qb0y27GRntt2u2PAYA=',
                username: '28224511:1379330808'
            },
            {
                url: 'turn:192.158.29.39:3478?transport=tcp',
                credential: 'JZEOEt2V3Qb0y27GRntt2u2PAYA=',
                username: '28224511:1379330808'
            }
        ]
    };

    var optional = {
        optional: [
            {DtlsSrtpKeyAgreement: true},
            {googDscp: true},
            {andyetAssumeSetLocalSuccess: true},
            {andyetFirefoxMakesMeSad:500}
        ]
    };


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
            rtcPeer = new RTCPeerConnection(servers, optional);
            rtcPeer.onicecandidate = function(e) {
                console.log("onicecandidate " + e.candidate);
                if (e.candidate) {
                    sendFunction(userId, {method:"addIceCandidate", broadcastId:userId, candidate: e.candidate});
                }
            };
            rtcPeer.onaddstream = gotRemoteStream;
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

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);
        if (this.isLocalUser && this.stream) {
            video.src = "";
            video.id = "webcam-me";
            this.stream.stop();
        } else if (this.isLocalUser) {
            video.src = "";
            video.id = "webcam-me";
        } else {
            container.parentNode.removeChild(container);
        }
        try {
            for (var remoteUserId in out) {
                out[remoteUserId].close();
            }
            if (rtcPeer) {
                rtcPeer.close();
            }
        } catch (e) {
            console.error("Weird thing in rtc peer dispose ");
        }
    };

    this.requestCreateOffer = function(remoteUserId, value) {
        var pc = new RTCPeerConnection(servers, optional);
        out[remoteUserId] = pc;
        pc.addStream(this.stream);
        pc.onicecandidate = function(e) {
            console.log("onicecandidate " + e.candidate);
            if (e.candidate) {
                sendFunction(remoteUserId, {method:"addIceCandidate", broadcastId:userId, candidate: e.candidate});
            }
        };
        if ($.browser.mozilla) {
            pc.createOffer(
                function(desc) {
                    trace('Created offer ' + 'desc.sdp');
                    pc.setLocalDescription(desc, function() {}, onSetDescriptionError);
                    sendFunction(remoteUserId, {method:"respondCreateOffer", broadcastId:userId, desc:desc});
                    console.log("createOffer success");
                },
                onCreateSessionDescriptionError
            );
        } else {
            pc.onnegotiationneeded = function () {
                console.log("onnegotiationneeded");
                pc.createOffer(
                    function(desc) {
                        trace('Created offer ' + 'desc.sdp');
                        pc.setLocalDescription(desc, function() {});
                        sendFunction(remoteUserId, {method:"respondCreateOffer", broadcastId:userId, desc:desc});
                        console.log("createOffer success");
                    },
                    onCreateSessionDescriptionError
                );
            }
        }
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

        rtcPeer.setRemoteDescription(desc, function() {
            console.log('setRemoteDescription success');
            rtcPeer.createAnswer(
                function onCreateAnswerSuccess(desc) {
                    console.log('Created answer: ' + 'desc.sdp');
                    rtcPeer.setLocalDescription(desc,
                        function() {
                            console.log('Set local description from answer.');
                        },
                        onSetDescriptionError);
                    isDescriptionFinished = true;
                    sendFunction(userId, {method:"respondAnswer", broadcastId:userId, desc:desc});
                },
                onCreateSessionDescriptionError,
                sdpConstraints
            );
        }, onSetDescriptionError);

    }

    this.addIceCandidate = function(remoteUserId, value) {
        var candidate = new RTCIceCandidate(value.candidate);
        var connection = this.isLocalUser ? out[remoteUserId] : rtcPeer;
        var pendingCandidates = candidatesMap[remoteUserId] || [];
        if (!isDescriptionFinished) {
            candidatesMap[remoteUserId] = pendingCandidates;
            pendingCandidates.push(candidate);
            console.log("Ice candidate added to pendings");
        } else {
            for (var i=0;i<pendingCandidates.length;i++) {
                var pendingCandidate = pendingCandidates[i];
                connection.addIceCandidate(pendingCandidate);
            }
            connection.addIceCandidate(candidate);
            console.log("Ice candidate processed");
        }

        //if (this.isLocalUser) {
        //    var pc = out[remoteUserId];
        //    pc.addIceCandidate(candidate)
        //} else {
        //    rtcPeer.addIceCandidate(candidate);
        //}
    }

    this.respondAnswer = function(remoteUserId, value) {
        var desc = fixDesc(value.desc);
        var pc = out[remoteUserId];

        pc.setRemoteDescription(desc,
            function() {
                console.log("Set remote description from answer success")
            },
            onSetDescriptionError);
        isDescriptionFinished = true;
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
        trace("rtcPeer received remote stream");
    }

    function onCreateSessionDescriptionError(error) {
        trace("Failed to create session description: " + error.message);
    }

    function onSetDescriptionError(error) {
        trace("Failed to set description: " + error.message);
    }
}
