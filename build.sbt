name := """playstudy"""

version := "1.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-unchecked", "-feature")

resolvers += "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

val akkaVersion = "2.3.9"

//  "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor"    % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"    % akkaVersion,
  "com.typesafe.akka" %% "akka-remote"   % akkaVersion,
  "com.typesafe.akka" %% "akka-agent"    % akkaVersion
)

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion

libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.4" % "compile"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.4" % "test"

libraryDependencies += "commons-io" % "commons-io" % "2.4" % "test"

//libraryDependencies += "org.kurento" % "kurento-client" % "5.1.0"

// Akka Persistence Storage Plugin
// libraryDependencies += "com.github.ironfish" %% "akka-persistence-mongo-casbah" % "0.7.5-SNAPSHOT"