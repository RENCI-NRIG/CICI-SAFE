package safe.multijvm.sample
import safe.server.BootService

object SafeMultiJvmHelper {
  def launchSafeServer(nodeName: String):Unit = {
    println("Hello from " + nodeName)
    val httpPort = System.getProperty("http.port")
    val slangProgram = System.getProperty("safeserver.program")
    val keypairDir = System.getProperty("safeserver.keypairdir")
    println(s"http.port=${httpPort}" )
    println("safeserver.program=${slangProgram}")
    println("safeserver.keypairdir=${keypairDir}")
    //sbt> run -f /home/qiang/Desktop/safe-new-version-repo-working-copy/safe/safe-apps/safe_spark_guard.slang  -r safeService

    val cmdArgs = Array("-f", slangProgram, "-hp", httpPort, "-kd", keypairDir, "-r", "safeService")
    BootService.main(cmdArgs) 
  }
}


object SafeMultiJvmNode1 {
  import safe.multijvm.sample.SafeMultiJvmHelper._ 
  def main(args: Array[String]) {
    launchSafeServer("server 1")
  }
}

object SafeMultiJvmNode2 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("server 2")
  }
}


/*
object SafeMultiJvmNode10 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 10 (tag_owner10)")
  }
}

object SafeMultiJvmNode11 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 11 (tag_owner11)")
  }
}

object SafeMultiJvmNode12 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 12 (file_owner10)")
  }
}

object SafeMultiJvmNode13 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 13 (user10)")
  }
}

object SafeMultiJvmNode14 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 14 (user11)")
  }
}

object SafeMultiJvmNode15 {
  import safe.multijvm.sample.SafeMultiJvmHelper._
  def main(args: Array[String]) {
    launchSafeServer("node 15 (registry10)")
  }
}
*/
