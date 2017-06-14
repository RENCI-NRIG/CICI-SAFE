package safe.multijvm.sample
import safe.safeakka.SafeAkkaServer

object SafeMultiJvmNode1 {
  def main(args: Array[String]) {
    println("Hello from node 1")
    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
    println("safelang.program=" + System.getProperty("safelang.program"))
    val port = System.getProperty("akka.remote.port")
    val slangProgram = System.getProperty("safelang.program")
    //sbt> run -f /home/qiang/Desktop/safe-new-version-repo-working-copy/safe/safe-apps/safe_spark_guard.slang   safeService

    val cmdArgs = Array("-f", slangProgram, "-p", port, "safeService")
    SafeAkkaServer.main(cmdArgs)
  }
}

object SafeMultiJvmNode2 {
  def main(args: Array[String]) {
    println("Hello from node 2")
    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
    println("safelang.program=" + System.getProperty("safelang.program"))
    val port = System.getProperty("akka.remote.port")
    val slangProgram = System.getProperty("safelang.program")

    val cmdArgs = Array("-f", slangProgram, "-p", port, "safeService")
    SafeAkkaServer.main(cmdArgs)
  }
}

//object SafeMultiJvmNode3 {
//  def main(args: Array[String]) {
//    println("Hello from node 3")
//    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
//    val cmdArgs = Array("-f", "/home/qiang/Desktop/safe-new-version-repo-working-copy/safe/safe-apps/safe_spark_guard.slang",
//                        "safeService")
//    SafeAkkaServer.main(cmdArgs)
//  }
//}
