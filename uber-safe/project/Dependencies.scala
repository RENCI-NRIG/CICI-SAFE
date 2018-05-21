import sbt._
import sbt.Keys._

object BuildSettings {
  val appName          = "safe"
  val orgName          = "org.safeclouds"

  object V {
    val akka                  = "2.3.9"
    val apacheCodec           = "1.10"
    val build                 = "0.1-SNAPSHOT"
    val cryptoBC              = "1.51"
    val httpNing              = "1.8.7"
    val httpDispatch          = "0.11.1"
    val scalac                = "2.11.7"
    val libScala              = "2.11"
    val spray                 = "1.3.3"
    val apacheHttpClient      = "4.5.2"
    val apacheHttpAsyncClient = "4.1.2"
    //val apacheHttpClient = "4.3.6"
    val apacheValidator       = "1.5.1"
    //val apacheCassandra       = "3.11.1"
    val apacheCassandra       = "4.0-SNAPSHOT"
    val jersey                = "2.26"
    val jfiglet               = "0.0.8"
  }

  val buildSettings = Seq (
      organization   :=  orgName
    , scalaVersion   :=  V.scalac
    , version        :=  V.build
    // Turn off sbt server
    // https://github.com/sbt/sbt/pull/3922
    , SettingKey[Boolean]("autoStartServer", "") := false
  )
}

object Dependencies {

  import BuildSettings._

  //====================== Akka libraries=============================//
  val akka = Seq(
      "com.typesafe.akka"  % s"akka-remote_${V.libScala}"    % V.akka
    , "com.typesafe.akka"  % s"akka-actor_${V.libScala}"     % V.akka
    , "com.typesafe.akka"  % s"akka-contrib_${V.libScala}"   % V.akka
    , "com.typesafe.akka"  % s"akka-slf4j_${V.libScala}"     % V.akka
    , "com.typesafe.akka"  % s"akka-testkit_${V.libScala}"   % V.akka
  )

  //====================== Apache libraries===========================//
  // apache commons codec provides implementations of common encoders and
  // decoders such as Base64, Hex, and URLs
  val apache = Seq(
      "commons-codec"             % "commons-codec"         % V.apacheCodec // for base 64 url safe encoding and decoding
    , "org.apache.httpcomponents" % "httpclient"            % V.apacheHttpClient
    , "org.apache.httpcomponents" % "httpasyncclient"       % V.apacheHttpAsyncClient
    , "commons-validator"         % "commons-validator"     % V.apacheValidator
    //, "org.apache.cassandra"      % "cassandra-all"         % V.apacheCassandra
    , "org.apache.cassandra"      % "cassandra-all"         % V.apacheCassandra
  )

  // support operations on IP address
  val net = Seq(
      "commons-net"        % "commons-net"       % "3.4"
    , "com.google.guava"   % "guava"             % "19.0"
  )

  val async = Seq(
      "org.scala-lang.modules" % s"scala-async_${V.libScala}"                % "0.9.1"
  ) 

  //====================== Bouncy castle libraries=============================//
  val crypto = Seq(
    // The Bouncy Castle Java S/MIME APIs for handling S/MIME protocols. This jar
    // contains S/MIME APIs for JDK 1.5 to JDK 1.7. The APIs can be used in
    // conjunction with a JCE/JCA provider such as the one provided with the Bouncy
    // Castle Cryptography APIs. The JavaMail API and the Java activation framework
    // will also be needed.
      "org.bouncycastle"  % "bcmail-jdk15on"  % V.cryptoBC

    // The Bouncy Castle Java API for handling the OpenPGP protocol. This jar contains 
    // the OpenPGP API for JDK 1.5 to JDK 1.7. The APIs can be used in conjunction with 
    // a JCE/JCA provider such as the one provided with the Bouncy Castle Cryptography APIs.
    , "org.bouncycastle"  % "bcpg-jdk15on"   % V.cryptoBC

    // The Bouncy Castle Java APIs for CMS, PKCS, EAC, TSP, CMP, CRMF, OCSP, and
    // certificate generation. This jar contains APIs for JDK 1.5 to JDK 1.7. The
    // APIs can be used in conjunction with a JCE/JCA provider such as the one
    // provided with the Bouncy Castle Cryptography APIs.
    , "org.bouncycastle"  % "bcpkix-jdk15on"  % V.cryptoBC

    // The Bouncy Castle Crypto package is a Java implementation of cryptographic
    // algorithms. This jar contains JCE provider and lightweight API for the
    // Bouncy Castle Cryptography APIs for JDK 1.5 to JDK 1.7.
    , "org.bouncycastle"  % "bcprov-jdk15on"   % V.cryptoBC
  )

  val caching = Seq(
    // MapDB provides concurrent Maps, Sets and Queues backed by disk storage or off-heap memory.
     //"org.mapdb"                % "mapdb"                      % "1.0.6"
      "io.spray"          % s"spray-caching_${V.libScala}"     % V.spray
    , "com.google.guava"  % "guava"                            % "19.0"

  )

  //====================== Config libraries===========================//
  // configuration library for jvm with a new a human-friendly JSON support which subsumes JSON
  val configure = Seq(
      "com.typesafe"             % "config"                    % "1.2.1"
  )

  val screen = Seq(
    // jline is a java library for handling console input
      "jline"                   % "jline"                      % "2.12"
    , "com.github.lalyos"       % "jfiglet"                    % V.jfiglet

  )

  //====================== Http libraries===========================//
  // Async Http Client library purpose is to allow Java applications to easily
  // execute HTTP requests and asynchronously process the HTTP responses. The
  // library also supports the WebSocket Protocol.
  val http = Seq(
      "com.ning"                 % "async-http-client"         % V.httpNing
  )

  //====================== Logger =================================//
  val logger = Seq(
      "com.typesafe.scala-logging"  % s"scala-logging_${V.libScala}"   % "3.1.0"
    , "ch.qos.logback"              % "logback-classic" % "1.1.2" % "runtime" // needs runtime for scala-logging to function
    //, "org.slf4j" % "slf4j-api" % "1.7.5"
    //, "org.slf4j" % "slf4j-simple" % "1.7.5"
    //, "org.clapper" %% "grizzled-slf4j" % "1.0.2"
  )

  val pickler = Seq(
      "org.scala-lang"           % s"scala-pickling_${V.libScala}" % "0.9.0-SNAPSHOT"
      //"org.scala-lang" %% "scala-pickling" % "0.8.0"
    , "com.lihaoyi"              % s"upickle_${V.libScala}"        % "0.2.2"
    , "com.github.benhutchison"  % s"prickle_${V.libScala}"        % "1.0"
  )

  val scalac = Seq(
      "org.scala-lang"          % "scala-compiler"             % V.scalac // for runtime and parser combinators
  )

  //====================== Spray libraries=================================//
  val spray = Seq(
      "io.spray"          % s"spray-caching_${V.libScala}"    % V.spray
    , "io.spray"          % s"spray-can_${V.libScala}"        % V.spray
    , "io.spray"          % s"spray-client_${V.libScala}"     % V.spray
    , "io.spray"          % s"spray-json_${V.libScala}"       % "1.3.1"   //% "1.2.6"
    , "io.spray"          % s"spray-routing_${V.libScala}"    % V.spray
    , "io.spray"          % s"spray-testkit_${V.libScala}"    % V.spray
  )

  val jersey = Seq(
      "org.glassfish.jersey.core"        % "jersey-server"                     % V.jersey
    , "org.glassfish.jersey.core"        % "jersey-common"                     % V.jersey  
    , "org.glassfish.jersey.containers"  % "jersey-container-grizzly2-servlet" % V.jersey
    , "org.glassfish.jersey.inject"      % "jersey-hk2"                        % V.jersey
    , "org.glassfish.jersey.media"       % "jersey-media-json-jackson"         % V.jersey
  )

  val timer = Seq(
      "com.github.nscala-time"   % s"nscala-time_${V.libScala}"  % "1.2.0"  // a wrapper around joda time
  )

  val tester = Seq(
      "org.scalatest"            % s"scalatest_${V.libScala}"    % "2.2.1" // a testing library
  )
  
  val multiJVM = Seq(
      "com.typesafe.akka"  % s"akka-multi-node-testkit_${V.libScala}" % V.akka
    , "org.scalatest"      % s"scalatest_${V.libScala}"               % "2.2.1" % "test"
  )
}

object Resolvers {
  val commonResolvers = Seq(
      "sonatype snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots/"
    , "spray repo"             at "http://repo.spray.io/"
    , "typesafe repo"          at "http://repo.typesafe.com/typesafe/releases/"
    , "bintray/non"            at "http://dl.bintray.com/non/maven"
    , "local maven repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository" 
  )
}
