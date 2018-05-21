import sbt.Keys.{connectInput => sbtConnectInput}

import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.jvmOptions
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{multiNodeHostsFileName, multiNodeHosts}

import BuildSettings._
import Dependencies._
import Resolvers._

def safeProject(id: String) = Project(id, base = file(id))
  .settings(
    buildSettings,
    shellPrompt := { s => "[" + Project.extract(s).currentProject.id + "@sbt]> " }
  )

lazy val safeDeps: Seq[ModuleID] = Seq(
  akka,
  crypto,
  caching,
  http,
  logger,
  spray
).flatten

lazy val safeAkkaDeps: Seq[ModuleID] = Seq(
  akka,
  crypto,
  caching,
  http,
  logger,
  multiJVM
).flatten

lazy val safeServerDeps: Seq[ModuleID] = Seq(
  akka,
  crypto,
  caching,
  http,
  logger,
  spray,
  jersey,
  multiJVM
//  akkaHttp
).flatten

lazy val safeStylaDeps = safeServerDeps
lazy val safeProgrammingDeps = safeServerDeps

lazy val hugeCollections = Seq(
  "net.openhft"          % "collections"                  % "3.2.1", // off heap caching library
  "net.openhft"          % "lang"                         % "6.4.5", // off heap caching library
  "net.openhft"          % "compiler"                     % "2.2.0", // off heap caching library
  "net.openhft"          % "affinity"                     % "2.1.0",
  "junit"                % "junit"                        % "4.10"
)

lazy val fetchDeps = Seq(
  "com.google.code.crawler-commons" % "crawler-commons"  % "0.2",
  "org.jsoup"                       % "jsoup"            % "1.7.2"
)

//lazy val safeBrowser: Seq[ModuleID] = Seq()

lazy val demoDeps: Seq[ModuleID] = Seq(
  apache,
  crypto,
  logger,
  timer,
  tester
).flatten

lazy val safeSetsDeps: Seq[ModuleID] = Seq(
  fetchDeps,
  logger,
  tester
).flatten

lazy val safeCacheDeps: Seq[ModuleID] = Seq(
  caching,
  logger,
  hugeCollections,
  scalac,
  tester
).flatten

lazy val safeRuntimeDeps: Seq[ModuleID] = Seq(
  configure,
  logger,
  scalac,
  tester
).flatten

lazy val safePickleDeps: Seq[ModuleID] = Seq(
  pickler,
  logger
).flatten

lazy val safelogDeps: Seq[ModuleID] = Seq(
  net,
  caching,
  configure,
  screen,
  logger,
  tester,
  timer
).flatten

lazy val safelangDeps: Seq[ModuleID] = Seq(
  apache,
  async,
  configure,
  screen,
  logger,
  tester,
  timer,
  caching,  // guava cache
  jersey
).flatten

lazy val commonOps = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  //"-print",
  // "-optimize", // slow builds
  "-encoding", "utf8",
  //"-Djavax.net.debug=ssl",
  //"-Djavax.net.debug=all",
  "-target:jvm-1.7",
  // turning off to disable "warning: Adapting argument list by creating a 2-tuple" Spray warnings
  //"-Xlint", 
  //"-Ylog-classpath",
  //"-Yclosure-elim",
  //"-Yinline",
  //"-Ywarn-adapted-args",
  "-Ywarn-dead-code"
)

lazy val safe = (project in file("."))
  .aggregate(safeCache, safelog)
  .settings(
    buildSettings,
    sbtConnectInput in run := true,
    fork                   := true,
    // withNameHashing is deprecated in sbt 1.x:
    // it's always on (http://www.scala-sbt.org/1.x/docs/sbt-1.0-Release-Notes.html)
    // incOptions           := incOptions.value.withNameHashing(true),
    // default main to start when "run" cmd is issued from sbt repl
    // mainClass in (Compile, run) := Some("safe.server.BootService"), 
    libraryDependencies    ++= safeDeps,
    outputStrategy         := Some(StdoutOutput), // send child output to stdout
    resolvers              := commonResolvers,
    scalacOptions          ++= commonOps,
    shellPrompt            := { s => "[" + Project.extract(s).currentProject.id + "@sbt]> " }
  )
//).dependsOn(safelang, safelog, safeAkka)


//  def getSourceDir = {
//    println("******************************************************************************")
//    println("multi-jvm base dir: ")
//    Seq(baseDirectory(_ / "multi-jvm/scala")).join.apply(dir => println(dir.toString)) 
//    //println("baseDirectory.value: " + baseDirectory.value / "multi-jvm/scala")
//    println("******************************************************************************")
//    Seq(baseDirectory(_ / "src/multi-jvm")).join
//    //Seq(baseDirectory.value / "multi-jvm/scala").join
//  }
//

/**
 * safeServer with multi-jvm support 
 */
lazy val safeServer = safeProject("safe-server")
  .settings(
    SbtMultiJvm.multiJvmSettings,
    sbtConnectInput in run  := true,   // send stdin to children
    fork                    := true,
    // javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseNUMA",  "-XX:+UseCondCardMark", "-XX:-UseBiasedLocking", "-XX:+UseParallelGC", "-XX:+DTraceMonitorProbes"),
    // javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseParallelGC"),
    // javaOptions in run   ++= Seq("-Xmx8G", "-XX:+UseConcMarkSweepGC"),
    // javaOptions in run   ++= Seq("-Xmx8G", "-XX:+HeapDumpOnOutOfMemoryError"),
    javaOptions in run      += "-Xmx8G",
    // default main to start when "run" cmd is issued from sbt repl
    // mainClass in (Compile, run) := Some("safe.server.BootService"),
    libraryDependencies     ++= safeServerDeps,
    outputStrategy          := Some(StdoutOutput), // send child output to stdout
    resolvers               := commonResolvers,
    scalacOptions           ++= commonOps,

    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm := { (compile in MultiJvm) triggeredBy (compile in Test) }.value,
    // change the "MultiJvm" identifier
    // multiJvmMarker in MultiJvm := "ClusterTest",
    jvmOptions in MultiJvm := Seq("-Xmx256M"),
    unmanagedSourceDirectories in MultiJvm := { Seq(baseDirectory(_ / "src/multi-jvm")).join }.value,

    multiNodeHostsFileName in MultiJvm := baseDirectory(_ / "multi-node-hosts.txt").toString,

    multiNodeHosts in MultiJvm := getTwoHosts, //Seq("root@localhost", "root@172.16.100.2")

    // disable parallel tests 
    parallelExecution in Test := false,
    // This step can be omitted according to this multi-jvm testing document:
    // https://doc.akka.io/docs/akka/current/multi-jvm-testing.html?language=scala#multi-jvm-testing
    //
    // Make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    //executeTests in Test := { (executeTests in Test, executeTests in MultiJvm) map {
    //  case (testResults, multiNodeResults) =>
    //    val overall =
    //      if (testResults.overall.id < multiNodeResults.overall.id)
    //        multiNodeResults.overall
    //      else
    //        testResults.overall
    //    Tests.Output(overall,
    //      testResults.events ++ multiNodeResults.events,
    //      testResults.summaries ++ multiNodeResults.summaries)
    //  }
    //}.value 
  ).dependsOn(safelang).configs(MultiJvm)


def getTwoHosts = {
  //println("*****************************************")
  //println(Seq("root@10.103.0.10", "root@10.103.0.11"))
  //println("*****************************************")
  Seq("root@10.103.0.10", "root@10.103.0.11")
}


lazy val safeStyla = safeProject("safe-styla")
  .settings(
    sbtConnectInput in run  := true,   // send stdin to children
    fork                    := true,
    libraryDependencies     ++= safeStylaDeps,
    outputStrategy          := Some(StdoutOutput), // send child output to stdout
    resolvers               := commonResolvers,
    scalacOptions           ++= commonOps
  ).dependsOn(safelog)


lazy val safeProgramming = safeProject("safe-programming")
  .settings(
    sbtConnectInput in run  := true,   // send stdin to children
    fork                 := true,
    javaOptions in run   += "-Xmx8G",
    // default main to start when "run" cmd is issued from sbt repl
    //, mainClass in (Compile, run) := Some("safe.programming.GeniBenchmark") 
    libraryDependencies ++= safeProgrammingDeps,
    outputStrategy       := Some(StdoutOutput), // send child output to stdout
    resolvers            := commonResolvers,
    scalacOptions        ++= commonOps
  ).dependsOn(safelang)

//
//lazy val safeServer = safeProject("safe-server")
//  .settings(
//    sbtConnectInput in run  := true,   // send stdin to children
//    fork                    := true,
//    // mainClass in (Compile, run) := Some("safe.server.BootService"),
//    libraryDependencies     ++= safeServerDeps,
//    outputStrategy          := Some(StdoutOutput), // send child output to stdout
//    resolvers               := commonResolvers,
//    scalacOptions           ++= commonOps
//  ).dependsOn(safelang)
//

lazy val safeCache = safeProject("safe-cache")
  .settings(
    sbtConnectInput in run  := true,   // send stdin to children
    fork                 := true,
    libraryDependencies  ++= safeCacheDeps,
    outputStrategy       := Some(StdoutOutput), // send child output to stdout
    resolvers            := commonResolvers,
    scalacOptions        ++= commonOps
  )

lazy val safeRuntime = safeProject("safe-runtime")
  .settings(
    sbtConnectInput in run  := true,   // send stdin to children
    fork                 := true,
    libraryDependencies  ++= safeRuntimeDeps,
    outputStrategy       := Some(StdoutOutput), // send child output to stdout
    resolvers            := commonResolvers,
    scalacOptions        ++= commonOps
  )

lazy val safelang = safeProject("safe-lang")
  .settings( 
    sbtConnectInput in run  := true,   // send stdin to children
    fork                    := false,
    javaOptions in run      += "-Xmx8G",
    // default main to start when "run" cmd is issued from sbt repl
    mainClass in (Compile, run) := Some("safe.safelang.Repl"), 
    libraryDependencies     ++= safelangDeps,
    outputStrategy          := Some(StdoutOutput), // send child output to stdout
    resolvers               := commonResolvers,
    scalacOptions           ++= commonOps
  ).dependsOn(safeCache, safelog, safeRuntime, safeStyla)
// .dependsOn(cassandraTrunk)
//
// lazy val cassandraTrunk = RootProject(uri("https://github.com/apache/cassandra.git"))

lazy val safelog = safeProject("safe-logic")
  .settings(
    sbtConnectInput in run  := true,   // send stdin to children
    fork                    := true,
    // default main to start when "run" cmd is issued from sbt repl
    // mainClass in (Compile, run) := Some("safe.safelog.Repl"), 
    // libraryDependencies  ++= safeDeps,
    libraryDependencies     ++= safelogDeps,
    outputStrategy          := Some(StdoutOutput), // send child output to stdout
    resolvers               := commonResolvers,
    scalacOptions           ++= commonOps
  ).dependsOn(safeCache)

lazy val safeAkka = safeProject("safe-akka")
  .settings(
    SbtMultiJvm.multiJvmSettings,
    sbtConnectInput in run  := true,  // send stdin to children
    fork                    := true,
    libraryDependencies     ++= safeAkkaDeps,
    outputStrategy          := Some(StdoutOutput), // send child output to stdout
    resolvers               := commonResolvers,
    scalacOptions           ++= commonOps,
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm := { (compile in MultiJvm) triggeredBy (compile in Test) }.value,
    // change the "MultiJvm" identifier
    // multiJvmMarker in MultiJvm := "ClusterTest",
    jvmOptions in MultiJvm := Seq("-Xmx256M"),
    unmanagedSourceDirectories in MultiJvm := { Seq(baseDirectory(_ / "src/multi-jvm")).join }.value,  
    // disable parallel tests 
    parallelExecution in Test := false
    //// make sure that MultiJvm tests are executed by the default test target,
    //// and combine the results from ordinary test and multi-jvm tests
    // executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    //  case (testResults, multiNodeResults) =>
    //    val overall =
    //      if (testResults.overall.id < multiNodeResults.overall.id)
    //        multiNodeResults.overall
    //      else
    //        testResults.overall
    //    Tests.Output(overall,  
    //      testResults.events ++ multiNodeResults.events,
    //      testResults.summaries ++ multiNodeResults.summaries)
    //}
  ).dependsOn(safelang, safelog).configs(MultiJvm)

// Jersey dependency workaround
val jerseyFix  = {
  sys.props += "packaging.type" -> "jar"
  ()
//SettingKey[Boolean]("autoStartServer", "") := false
// val autoStartServer        := false,
}
