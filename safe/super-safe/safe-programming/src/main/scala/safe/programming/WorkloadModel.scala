package safe.programming

import safe.safelang.{Safelang, SafelangManager}
import safe.safelog.{UnSafeException, Query, Structure}
import util.SlangObjectHelper
import safe.safelang.util.KeyPairManager

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex
import scala.io.Source
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

/**
 * Benchmark workload model
 */
abstract class WorkloadModel {

  val operators: Seq[String]
  val opcountMap: Map[String, Int]
  val allPrincipals: ListBuffer[PrincipalStub]

  var testingCacheJvm = ""  // for cache testing

  def setTestingCacheJvm(jvm: String): Unit = {
    // jvm format: ip:port
    assert(jvm.split(":").length == 2, s"Invalid jvm: $jvm")
    testingCacheJvm = jvm
  }

  

}
