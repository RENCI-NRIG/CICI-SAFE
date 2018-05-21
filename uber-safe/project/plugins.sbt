//========= sbt-assembly================//
// Deploy fat JARs. Restart processes.
// sbt-assembly is a sbt plugin originally ported from codahale's assembly-sbt,
// which I'm guessing was inspired by Maven's assembly plugin. The goal is
// simple: Create a fat JAR of your project with all of its dependencies.
// https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")


//========= sbt-resolver================//
// sbt-revolver is a plugin for SBT enabling a super-fast development turnaround
// for your Scala applications.  It supports the following features:
//
//  - Starting and stopping your application in the background of your interactive SBT shell (in a forked JVM)
//  - Triggered restart: automatically restart your application as soon as some of its sources have been changed
// https://github.com/spray/sbt-revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// https://github.com/MasseGuillaume/ScalaKata/
// scala in the browser
//addSbtPlugin("com.scalakata" % "plugin" % "1.1.5")

// add multi-jvm plugin
// https://github.com/sbt/sbt-multi-jvm
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.8.0")
