Play Meetings
==================

This is Play Framework application with Scala| Akka | WebSockets.
Nowadays all web conferences web software is built with Adobe Flash or desktop app (Adobe Connect, Gotomeeting). This is attempt to check js abilities to achieve the same.
Past RTMP calls + Flash Media Server Video Streaming replaced with WebSockets + WebRTC p2p video.

### Features
 - Users presence
 - Chat
 - Multi-user Video Chat with WebRTC (Firefox and Chrome only)

Setup
-----

1. [Download Typesafe Activator](http://typesafe.com/platform/getstarted) (or copy it over from a USB)
2. Extract the zip and run the `activator` or `activator.bat` script from a non-interactive shell
3. Your browser should open to the Activator UI: [http://localhost:8888](http://localhost:8888)


### Possible future steps
 - Add akka persistence to keep room state after room actor restart;
 - Add user list;
 - Add more meetings features: image sharing, screen sharing, collaborative drawing;
 - Add monitoring page.
