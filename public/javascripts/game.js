(function(){

  function removeError () {
    $('#error').fadeOut(500);
  }
  function setError (message) {
    $('#error').empty().append($('<span class="error" />').text(message)).fadeIn(500);
  }

  if (!window.WebSocket) {
    if (window.MozWebSocket)
      window.WebSocket = window.MozWebSocket
  }

  if (!window.WebSocket) {
    setError("WebSocket is not supported by your browser.");
    return;
  }

  var insideIframe = (window.parent != window);
  var isMobile = /ipad|iphone|android/i.test(navigator.userAgent);

  // STATES
  // Key value store of data
  var presenseMap = {}; // temp state: users, broadcasts
  var stateMap = {};    // persisted state: shared content, chat

  var pid;
  var pname;

  var onSocketMessage;

  // every player positions
  var players = {};

  var viewport = document.getElementById('viewport');

  // Init pname
  function queryPname() {
    var n = prompt("What is your name?");
    if (n) {
      localStorage.setItem("pname", n);
    }
    return n || pname;
  }
  pname = localStorage.getItem("pname");
  if (!insideIframe && !pname) {
     pname = queryPname();
  }
  if (!pname) {
    pname = "User " + Math.floor(100*Math.random());
  }

  function sendCommand(action) {
    var commandData = $("#commandInput").val();
    var data = action || commandData;
    if (data) {
      $("#commandInput").val( "" );
      send({ messageType: "command", data: data });
    }
  }

  $('#pname').text(pname).click(function(e) {
    e.preventDefault();
    pname = queryPname();
    send({ messageType: 'change', name: pname });
    $('#pname').text(pname);
  });

  $('#commandSendButton').on('click', function (e) {
    sendCommand();
  });
  $('#commandClearButton').on('click', function (e) {
    sendCommand("clear");
  });
  $('#commandPlayButton').on('click', function (e) {
    sendCommand("play");
  });
  $('#commandTileButton').on('click', function (e) {
    sendCommand("tile");
  });

  $('#commandForm').submit(function(event){
    // prevent default browser behaviour
    event.preventDefault();
    sendCommand();
  });

  // WebSocket
  var socket;
  var connected = false;

  var reconnectInterval = 1000;
  var reconnection = false;

  function tryConnectAgain () {
    reconnection = true;
    setError("WebSocket connection is down. reconnecting...");
    setTimeout(function() {
      reconnectInterval *= (1.5+0.2*Math.random());
      connect();
    }, reconnectInterval);
  }

  function connect () {
    try {
      socket = new WebSocket("ws://"+location.host+"/stream/default");
      socket.onmessage = onSocketMessage;
      socket.onopen = function(evt) {
        if (reconnection) {
          window.location = window.location; // Reloading the page to reset states
          return;
        }
        connected = true;
        console.log("websocket on open")
        send({ messageType: 'change', name: pname });
      };
      socket.onclose = function(evt) { 
        connected = false;
        tryConnectAgain();
      };
      socket.onerror = function(evt) { console.error("error", evt); };
    }
    catch (e) {
      setError("WebSocket connection failed.");
    }
  }

  function send (o) {
    if (!connected) return;
    socket.send(JSON.stringify(o));
  }


(function(){

  onSocketMessage = function (e) {
    var m = JSON.parse(e.data);
    console.log("onSocketMessage " + m.messageType + " " + e.data)
    var player = players[m.pid];
    if (player === undefined) {
      players[m.pid] = m;
    } else if (m.messageType=="youAre") {
      pid = m.pid;
      //$("#pid").html("Id: " + pid);
    } else if (m.messageType == "chatClear") {
      $("#chatArea").html("");
    } else if (m.messageType == "chatMessage") {
      var chatArea = $("#chatArea");
      chatArea.html(chatArea.html() + "<p><strong>" + m.name + ": </strong>" + m.message + "</p>")
      chatArea.get(0).scrollTop = chatArea.get(0).scrollHeight;
      console.log("Append child " + m.message)
    } else if (m.messageType == "status") {
      $("#localTextArea").html( m.local );
      $("#allTextArea").html( m.all );
    } else if (m.messageType == "disconnect") {
      delete players[m.pid];
    }
  }
}());

  connect();
}());
