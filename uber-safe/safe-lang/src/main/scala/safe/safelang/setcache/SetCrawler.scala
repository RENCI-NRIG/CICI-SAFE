/**
 * Based on CertCrawler authored by Anthony Hagouel
 */

package safe.safelang
package setcache

import safesets.SafeSetsClient
import safe.cache.SafeTable
import safe.safelog.SetId
import util.KeyPairManager
import akka.actor.ActorSystem

import scala.util.Random.nextInt
//import scala.util.{Try, Success, Failure}

import java.io.File

/**
 * @DeveloperAPI
 * Main function to do some testing.
 */
object CacheTestHarness extends KeyPairManager with App {

  val certFilenames: Seq[String] = filenamesOfDir(Config.config.safeSetsDir)
  val tokens = certFilenames.filter(_.startsWith("cert_"))
    .map{case name => Token(name.stripPrefix("cert_"))} 
  println(s"[Slang CacheTestHarness] ${tokens.size} certs in dir ${Config.config.safeSetsDir}")
  //println(tokens)
 
  val system = ActorSystem("Safelang") 
  val localSetTable = new SafeTable[SetId, SlogSet]()
  val safesetsClient = SafeSetsClient(system) 
  val cache = new SetCache(localSetTable, safesetsClient)

  cache.get(tokens(6))
  println("got 6")
  println()
  Thread.sleep(10000L)

  cache.get(tokens(8))
  println("got 8")
  Thread.sleep(10000L)

  cache.get(tokens(3))
  println("got 3")
  println()
  Thread.sleep(10000L)

  //val v200 = cache.get(200)
  //println(s"v200=${v200}")
  //println()

  // Load a large random set of certs
  val rdmLoads = 1000
  for(link <- 0 to 1000) {
    cache.get(tokens(nextInt(tokens.length)))
  }

  // Warm benchmark
  val numGets = 10000
  println("Benchmark the warm cache")
  val t0 = System.currentTimeMillis  
  for(link <- 0 to numGets) {
    cache.get(tokens(nextInt(tokens.length)))
  }
  val t1 = System.currentTimeMillis
  val runtime = t1 - t0
  val throughput = numGets / runtime
  println(s"Done in $runtime ms ($throughput Kops/sec)") 
  system.shutdown()

}
