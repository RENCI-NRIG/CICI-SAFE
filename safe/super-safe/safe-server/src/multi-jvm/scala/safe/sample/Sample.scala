package safe.sample

object SampleMultiJvmNode1 {
  def main(args: Array[String]) {
    println("Hello from node 1")
    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
  }
}

object SampleMultiJvmNode2 {
  def main(args: Array[String]) {
    println("Hello from node 2")
    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
  }
}

object SampleMultiJvmNode3 {
  def main(args: Array[String]) {
    println("Hello from node 3")
    println("akka.remote.port=" + System.getProperty("akka.remote.port"))
  }
}
