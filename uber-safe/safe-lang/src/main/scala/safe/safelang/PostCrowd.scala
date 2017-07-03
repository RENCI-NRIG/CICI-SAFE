package safe.safelang

import safesets._
import akka.actor.ActorSystem

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.util.Timeout

/**
 * @DeveloperAPI
 * Testing a crowd of concurrent posts to riak.
 */
object PostCrowd extends App {
  val system = ActorSystem("postcrowd")
  val safesetsclient = SafeSetsClient(system)
  val timeout = Timeout(FiniteDuration(25, SECONDS)) 
  val locks = Seq("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
  for(i <- 0 to 0) {
    //val f = Future {
      val r = Random.nextInt(10)
      var t = r.toString * 10 
      val v = t * 100 + Random.nextInt(10000).toString
      //t = "ATTCPB3CQ0VMax0pFnINQk_NfK7De8EQsGupLFvUzo8" 
      t = "lgsdiYNBUDUuVZ8uRHNHdbevGj1D4PMwm9tPCjv_YqM" 
      safesetsclient.deleteSlogSet(t)
      //locks(r).synchronized {
      //safesetsclient.fetchRemote(t)
      //safesetsclient.fetchSlogSet(t)
      //safesetsclient.postRemote(t, v)
      //}
    //}
    //Await.result(f, timeout.duration) 
    //Thread.sleep(1000)
    println(s"i: $i")
//    f.onComplete {
//      case Success(res) =>
//      case Failure(e) => println(s"Failed $e")
//    }
//    if(i % 100 == 0) {
//      println(s"i: $i")
//    }
  }
  //system.shutdown()
}
