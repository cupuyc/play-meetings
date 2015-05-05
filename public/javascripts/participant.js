/*
* (C) Copyright 2014 Kurento (http://kurento.org/)
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Lesser General Public License
* (LGPL) version 2.1 which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/lgpl-2.1.html
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
*/

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
	this.name = name;
    this.sendFunction = sendFunction;
    this.isLocalUser = isLocalUser;

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
        if (this.isLocalUser) {
            getUserMedia({
                    audio: true,
                    video: true
                }, this.gotLocalStream.bind(this),
                function(e) {
                    alert('getUserMedia() error: ' + e.name);
                });
            attachMediaStream(video, stream);
        }
    }

    this.gotLocalStream = function(stream) {
        trace('Received local stream');
        // Call the polyfill wrapper to attach the media stream to this element.
        attachMediaStream(video, stream);
        this.stream = stream;
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
            this.rtcPeer.dispose(); // execution may stop
        } catch (e) {
            console.error("Wired thing in rtc peer dispose");
        }
    };
}
