name := "EShop"

version := "1.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.17" % "test",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.17",
  "org.iq80.leveldb"            % "leveldb"          % "0.9",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "com.typesafe.akka" %% "akka-http"   % "10.1.5",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12", // or whatever the latest version is
  "org.scalatest" %% "scalatest" % "3.0.1" % "test")

// the library is available in Bintray repository
resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

// akka 2.5.x
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"

// akka 2.4.x
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.4.20.0"

