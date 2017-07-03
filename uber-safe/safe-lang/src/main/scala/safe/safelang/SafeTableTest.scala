package safe.safelang

import safe.cache.SafeTable

import akka.actor.ActorSystem

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.util.Timeout

/**
 * @DeveloperAPI
 * Testing SafeTable under concurrent update and acces.
 */
object SafeTableTest extends App {
  //val system = ActorSystem("postcrowd")
  //val safesetsclient = SafeSetsClient(system)
  val timeout = Timeout(FiniteDuration(25, SECONDS)) 
  val len = 10000000
  var locks = new Array[AnyRef](len) 
  val putdone: Array[Boolean] = new Array[Boolean](len) 
  var numexceptions = 0
  for(i <- 0 to len-1) {
    locks(i) = new AnyRef
  }
  val safetable = new SafeTable[String, String]()
  println(s"Start testing")
  for(i <- 0 to len-1) {
    val key = s"str${i}"
    val value = key
    //println(s"Working on key ${i}")
    //putAsync(key, value, i)
    //getAsync(key, i)
    putThenGetAsync(key, value)
    if(i % 10000 == 0) {
      println(s"i=$i    numexceptions=${numexceptions}")
    }
  }
  Thread.sleep(100000L)
  println(s"numexceptions: ${numexceptions}")

  def putAsync(key: String, value: String, idx: Int): Unit = {
    val lock = locks(idx)
    val f = Future {
      lock.synchronized {
        safetable.put(key, value)
        putdone(idx) = true
        println(s"Put ${idx}")
        lock.notify()
      }
    } 
  }

  def getAsync(key: String, idx: Int): Unit = {
    val lock = locks(idx)
    val f = Future {
     blocking {
        lock.synchronized {
          while(putdone(idx) == false) {
            lock.wait()
          }
          if(!safetable.get(key).isDefined) {
            println(s"Get ${idx}: No value for $key")
            numexceptions += 1
          } else {
            println(s"Get ${idx}: value ${safetable.get(key)}")
          }
        }  
      }
    }
  } 

  def putThenGetAsync(key: String, value: String): Unit = {
    safetable.put(key, value)
    println(s"Put ${key}")
    getAsyncNoWait(key) 
  }

  def getAsyncNoWait(key: String): Unit = {
    val f = Future {
      blocking {
        if(!safetable.get(key).isDefined) {
          println(s"Get: No value for $key")
          numexceptions += 1
        } else {
          println(s"Get: value ${safetable.get(key)}")
        }
      }
    }  
  } 

}
