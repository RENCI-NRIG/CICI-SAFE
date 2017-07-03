import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._

import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.jvmOptions
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{multiNodeHostsFileName, multiNodeHosts}


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
    val spray                 = "1.3.3"
    val apacheHttpClient      = "4.5.2"
    val apacheHttpAsyncClient = "4.1.2"
    //val apacheHttpClient = "4.3.6"
    val apacheValidator       = "1.5.1"
  }

  val buildSettings = Seq (
      organization   :=  orgName
    , scalaVersion   :=  V.scalac
    , version        :=  V.build
  )
}

object Dependencies {

  import BuildSettings._

  //====================== Akka libraries=============================//
  val akka = Seq(
      "com.typesafe.akka" %% "akka-remote"    % V.akka
    , "com.typesafe.akka" %% "akka-actor"     % V.akka
    , "com.typesafe.akka" %% "akka-contrib"   % V.akka
    , "com.typesafe.akka" %% "akka-slf4j"     % V.akka
    , "com.typesafe.akka" %% "akka-testkit"   % V.akka
  )

  //====================== Apache libraries===========================//
  // apache commons codec provides implementations of common encoders and
  // decoders such as Base64, Hex, and URLs
  val apache = Seq(
      "commons-codec"             % "commons-codec"       % V.apacheCodec // for base 64 url safe encoding and decoding
    , "org.apache.httpcomponents" % "httpclient"          % V.apacheHttpClient
    , "org.apache.httpcomponents" % "httpasyncclient"     % V.apacheHttpAsyncClient
    , "commons-validator"         % "commons-validator"   % V.apacheValidator
  )

  // support operations on IP address
  val net = Seq(
      "commons-net"        % "commons-net"       % "3.4"
    , "com.google.guava"   % "guava"             % "19.0"
  )

  val async = Seq(
      "org.scala-lang.modules" %% "scala-async"                % "0.9.1"
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
      "io.spray"          %% "spray-caching"    % V.spray
    , "com.google.guava"  % "guava"             % "19.0"

  )

  //====================== Config libraries===========================//
  // configuration library for jvm with a new a human-friendly JSON support which subsumes JSON
  val config = Seq(
      "com.typesafe"             % "config"                    % "1.2.1"
  )

  val console = Seq(
    // jline is a java library for handling console input
      "jline"                   % "jline"                      % "2.12"
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
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.1.0"
    , "ch.qos.logback"              % "logback-classic" % "1.1.2" % "runtime" // needs runtime for scala-logging to function
    //, "org.slf4j" % "slf4j-api" % "1.7.5"
    //, "org.slf4j" % "slf4j-simple" % "1.7.5"
    //, "org.clapper" %% "grizzled-slf4j" % "1.0.2"
  )

  val pickler = Seq(
      "org.scala-lang" %% "scala-pickling" % "0.9.0-SNAPSHOT"
      //"org.scala-lang" %% "scala-pickling" % "0.8.0"
    , "com.lihaoyi" %% "upickle" % "0.2.2"
    , "com.github.benhutchison" %% "prickle" % "1.0"
  )

  val scalac = Seq(
      "org.scala-lang"          % "scala-compiler"             % V.scalac // for runtime and parser combinators
  )

  //====================== Spray libraries=================================//
  val spray = Seq(
      "io.spray"          %% "spray-caching"    % V.spray
    , "io.spray"          %% "spray-can"        % V.spray
    , "io.spray"          %% "spray-client"     % V.spray
    , "io.spray"          %% "spray-json"       % "1.3.1"   //% "1.2.6"
    , "io.spray"          %% "spray-routing"    % V.spray
    , "io.spray"          %% "spray-testkit"    % V.spray
  )

  val timer = Seq(
      "com.github.nscala-time" %% "nscala-time"                % "1.2.0"  // a wrapper around joda time
  )

  val tester = Seq(
      "org.scalatest"          %% "scalatest"                  % "2.2.1" // a testing library
  )
  
  val multiJVM = Seq(
      "com.typesafe.akka" %% "akka-multi-node-testkit" % V.akka
    , "org.scalatest"     %% "scalatest"               % "2.2.1" % "test"
  )
}

object Resolvers {
  val commonResolvers = Seq(
      "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    , "spray repo"         at "http://repo.spray.io/"
    , "typesafe repo"      at "http://repo.typesafe.com/typesafe/releases/"
    , "bintray/non" at "http://dl.bintray.com/non/maven"
  )
}

object ApplicationBuild extends Build {

  import BuildSettings._
  import Dependencies._
  import Resolvers._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => "[" + Project.extract(s).currentProject.id + "@sbt]> " }
  }

  /* Adding tools.jar to sbt: http://stackoverflow.com/questions/12409847/how-to-add-tools-jar-as-a-dynamic-dependency-in-sbt-is-it-possible

   // adding the tools.jar to the unmanaged-jars seq
unmanagedJars in Compile ~= {uj => 
    Seq(Attributed.blank(file(System.getProperty("java.home").dropRight(3)+"lib/tools.jar"))) ++ uj
}

// exluding the tools.jar file from the build
excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
    cp filter {_.data.getName == "tools.jar"}
}
  */

  lazy val safeAkkaDeps: Seq[ModuleID] = Seq(
      akka
    , crypto
    , caching
    , http
    , logger
    , multiJVM
  ).flatten

  lazy val safeServerDeps: Seq[ModuleID] = Seq(
      akka
    , crypto
    , caching
    , http
    , logger
    , spray
    , multiJVM
//    , akkaHttp
  ).flatten


  lazy val hugeCollections = Seq(
      "net.openhft"          % "collections"                  % "3.2.1" // off heap caching library
    , "net.openhft"          % "lang"                         % "6.4.5" // off heap caching library
    , "net.openhft"          % "compiler"                     % "2.2.0" // off heap caching library
    , "net.openhft"          % "affinity"                     % "2.1.0"
    , "junit"                % "junit"                        % "4.10"
  )

  lazy val fetchDeps = Seq(
    "com.google.code.crawler-commons" % "crawler-commons"  % "0.2",
    "org.jsoup"                       % "jsoup"            % "1.7.2"
  )


  //lazy val safeBrowser: Seq[ModuleID] = Seq()

  lazy val demoDeps: Seq[ModuleID] = Seq(
      apache
    , crypto
    , logger
    , timer
    , tester
  ).flatten

  lazy val safeSetsDeps: Seq[ModuleID] = Seq(
      fetchDeps
    , logger
    , tester
  ).flatten

  lazy val safeCacheDeps: Seq[ModuleID] = Seq(
      caching
    , logger
    , hugeCollections
    , scalac
    , tester
  ).flatten

  lazy val safeRuntimeDeps: Seq[ModuleID] = Seq(
      config
    , logger
    , scalac
    , tester
  ).flatten

  lazy val safePickleDeps: Seq[ModuleID] = Seq(
      pickler
    , logger
  ).flatten

  lazy val safelogDeps: Seq[ModuleID] = Seq(
      net
    , caching
    , config
    , console
    , logger
    , tester
    , timer
  ).flatten

  lazy val safelangDeps: Seq[ModuleID] = Seq(
      apache
    , async
    , config
    , console
    , logger
    , tester
    , timer
    , caching  // guava cache
  ).flatten

  lazy val safeDeps: Seq[ModuleID] = Seq(
      akka
    , crypto
    , caching
    , http
    , logger
    , spray
  ).flatten

  lazy val commonOps = Seq(
      "-deprecation"
    , "-feature"
    , "-unchecked"
    //, "-print"
    //, "-optimize" // slow builds
    , "-encoding", "utf8"
    //, "-Djavax.net.debug=ssl"
    //, "-Djavax.net.debug=all"
    , "-target:jvm-1.7"
    //, "-Xlint" // turning off to disable "warning: Adapting argument list by creating a 2-tuple" Spray warnings
    //, "-Ylog-classpath"
    //, "-Yclosure-elim"
    //, "-Yinline"
    //, "-Ywarn-adapted-args"
    , "-Ywarn-dead-code"
  )

//  def getSourceDir = {
//    println("******************************************************************************")
//    println("multi-jvm base dir: ")
//    Seq(baseDirectory(_ / "multi-jvm/scala")).join.apply(dir => println(dir.toString)) 
//    //println("baseDirectory.value: " + baseDirectory.value / "multi-jvm/scala")
//    println("******************************************************************************")
//    Seq(baseDirectory(_ / "src/multi-jvm")).join
//    //Seq(baseDirectory.value / "multi-jvm/scala").join
//  }

  lazy val safeAkka = Project(
      id = "safe-akka"
    , base = file("safe-akka")
    , settings = buildSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
      , libraryDependencies ++= safeAkkaDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps

      // make sure that MultiJvm test are compiled by the default test compilation
      , compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)
      // change the "MultiJvm" identifier
      // multiJvmMarker in MultiJvm := "ClusterTest",
      , jvmOptions in MultiJvm := Seq("-Xmx256M")
      , unmanagedSourceDirectories in MultiJvm <<= Seq(baseDirectory(_ / "src/multi-jvm")).join  
      
      // disable parallel tests 
      , parallelExecution in Test := false
      // make sure that MultiJvm tests are executed by the default test target,
      // and combine the results from ordinary test and multi-jvm tests
      , executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
        case (testResults, multiNodeResults) =>
          val overall =
            if (testResults.overall.id < multiNodeResults.overall.id)
              multiNodeResults.overall
            else
              testResults.overall
          Tests.Output(overall,  
            testResults.events ++ multiNodeResults.events,
            testResults.summaries ++ multiNodeResults.summaries)
      }
    ) 
    , dependencies = Seq(safelang, safelog)
  ) configs (MultiJvm)

  /**
   * safeServer with multi-jvm support 
   */
  lazy val safeServer = Project(
      id = "safe-server"
    , base = file("safe-server")
    , settings = buildSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      //, javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseNUMA",  "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+UseParallelGC", "-XX:+DTraceMonitorProbes")
      //, javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseParallelGC")
      //, javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseConcMarkSweepGC")
      //, javaOptions in run   ++= Seq("-Xmx8G", "-XX:+HeapDumpOnOutOfMemoryError")
      , javaOptions in run   += "-Xmx8G"
      , incOptions           := incOptions.value.withNameHashing(true)
      //, mainClass in (Compile, run) := Some("safe.bench.geni.BootSafeHttpService") // default main to start when "run" cmd is issued from sbt repl
      , libraryDependencies ++= safeServerDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps

      // make sure that MultiJvm test are compiled by the default test compilation
      , compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)
      // change the "MultiJvm" identifier
      // multiJvmMarker in MultiJvm := "ClusterTest",
      , jvmOptions in MultiJvm := Seq("-Xmx256M")
      , unmanagedSourceDirectories in MultiJvm <<= Seq(baseDirectory(_ / "src/multi-jvm")).join

      , multiNodeHostsFileName in MultiJvm := baseDirectory(_ / "multi-node-hosts.txt").toString

      , multiNodeHosts in MultiJvm := getNodeHosts2 //Seq("root@localhost", "root@172.16.100.2")

      // disable parallel tests 
      , parallelExecution in Test := false
      // make sure that MultiJvm tests are executed by the default test target,
      // and combine the results from ordinary test and multi-jvm tests
      , executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
        case (testResults, multiNodeResults) =>
          val overall =
            if (testResults.overall.id < multiNodeResults.overall.id)
              multiNodeResults.overall
            else
              testResults.overall
          Tests.Output(overall,
            testResults.events ++ multiNodeResults.events,
            testResults.summaries ++ multiNodeResults.summaries)
      } 
    )
    , dependencies = Seq(safelang)
  ) configs (MultiJvm)

  def getNodeHosts2 = {
    //println("*****************************************")
    //println(Seq("root@10.103.0.10", "root@10.103.0.11"))
    //println("*****************************************")
    Seq("root@10.103.0.10", "root@10.103.0.11")
  }


  lazy val safeStyla = Project(
      id = "safe-styla"
    , base = file("safe-styla")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
      , libraryDependencies ++= safeServerDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps)
    , dependencies = Seq(safelog)
  )


  lazy val safeProgramming = Project(
      id = "safe-programming"
    , base = file("safe-programming")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , javaOptions in run   += "-Xmx8G"
      , incOptions           := incOptions.value.withNameHashing(true)
      //, mainClass in (Compile, run) := Some("safe.bench.geni.BootSafeHttpService") // default main to start when "run" cmd is issued from sbt repl
      , libraryDependencies ++= safeServerDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps)
    , dependencies = Seq(safelang)
  )


//
//  lazy val safeServer = Project(
//      id = "safe-server"
//    , base = file("safe-server")
//    , settings = buildSettings ++ Seq(
//        connectInput in run  := true   // send stdin to children
//      , fork                 := true
//      , incOptions           := incOptions.value.withNameHashing(true)
//      //, mainClass in (Compile, run) := Some("safe.bench.geni.BootSafeHttpService") // default main to start when "run" cmd is issued from sbt repl
//      , libraryDependencies ++= safeServerDeps
//      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
//      , resolvers            := commonResolvers
//      , scalacOptions       ++= commonOps)
//    , dependencies = Seq(safelang)
//  )
//

  lazy val safe = Project(
      id = appName
    , base = file(".")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
      //, mainClass in (Compile, run) := Some("safe.bench.geni.BootSafeHttpService") // default main to start when "run" cmd is issued from sbt repl
      , libraryDependencies ++= safeDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps)
    , dependencies = Seq(safelang, safelog, safeAkka)
  )

  lazy val safeCache = Project(
      id = "safe-cache"
    , base = file("safe-cache")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
      , libraryDependencies ++= safeCacheDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps
    )
  )

  lazy val safeRuntime = Project(
      id = "safe-runtime"
    , base = file("safe-runtime")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
      , libraryDependencies ++= safeRuntimeDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps
    )
  )

  lazy val safelang = Project(
      id = "safe-lang"
    , base = file("safe-lang")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , javaOptions in run   += "-Xmx8G"
      , incOptions           := incOptions.value.withNameHashing(true)
      , mainClass in (Compile, run) := Some("safe.safelang.Repl") // default main to start when "run" cmd is issued from sbt repl
      , libraryDependencies ++= safelangDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps)
    , dependencies = Seq(safeCache, safelog, safeRuntime, safeStyla)
  )


  lazy val safelog = Project(
      id = "safe-logic"
    , base = file("safe-logic")
    , settings = buildSettings ++ Seq(
        connectInput in run  := true   // send stdin to children
      , fork                 := true
      , incOptions           := incOptions.value.withNameHashing(true)
//      , mainClass in (Compile, run) := Some("safe.safelang.Repl") // default main to start when "run" cmd is issued from sbt repl
      , libraryDependencies ++= safeDeps
      , libraryDependencies ++= safelogDeps
      , outputStrategy       := Some(StdoutOutput) // send child output to stdout
      , resolvers            := commonResolvers
      , scalacOptions       ++= commonOps)
    , dependencies = Seq(safeCache)
  )
}
