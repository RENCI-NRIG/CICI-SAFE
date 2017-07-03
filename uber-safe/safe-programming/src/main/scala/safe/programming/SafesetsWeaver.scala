package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException
import util.SlangObjectHelper
import safe.safelang.util.KeyPairManager

import scala.collection.mutable.ListBuffer
import scala.util.Random.nextInt


/**
 * Link slogsets and post them to safesets
 */

object SafesetsWeaver extends KeyPairManager with SlangObjectHelper {

  /**
   * Build a slogset graph
   * @param nNodes the number of nodes
   * @param nLinks the number of links for a node 
   */
  def run(inference: Safelang, nNodes: Int, nLinks: Int): Unit = {
    var numNodes = nNodes
    var numLinks = nLinks

    // JVMs
    val defaultJvm = "152.3.136.26:7777"

    // Principals
    val principalList = ListBuffer[PrincipalStub]()
    val keyPaths: Seq[String] = filepathsOfDir(Config.config.keyPairDir)
    var count = 0
    for(fname <- keyPaths) {
      val p: PrincipalStub = new PrincipalStub(fname, s"p${count}", defaultJvm)
      count += 1
      principalList += p
      // Set up Id set and subject set
      p.postIdSet(inference)
      p.postSubjectSetAndGetToken(inference)
    }
  
    if(principalList.size < numNodes) {
      //throw UnSafeException(s"${numNodes} principals expected, but only ${principalList.size} keypairs found") 
      numNodes = principalList.size
    }
    if(principalList.size < numLinks) {
      numLinks = principalList.size
    }

    for(node <- 0 to numNodes-1) {
      val p = principalList(node) 
      for(link <- 0 to numLinks-1) {
        val rdmPrincipal = principalList(nextInt(numNodes))
        val subjectSetTokens = rdmPrincipal.getSubjectSetTokens
        val link: String = subjectSetTokens(0)
        p.updateSubjectSet(inference, link)
      }
    }
  }

  /**
   * Test water 
   */
  def touch(inference: Safelang): Unit = {
    // JVMs
    val defaultJvm = "152.3.136.26:7777"

    // Principals
    val principalList = ListBuffer[PrincipalStub]()
    println(s"[SafesetsWeaver touch] Config.config.keyPairDir=${Config.config.keyPairDir}")
    val keyPaths = filepathsOfDir(Config.config.keyPairDir)
    val count = 0
    for(fname <- keyPaths) {
      println(s"Make a principal using the pem file: ${fname}")
      scala.io.StdIn.readLine()

      val p: PrincipalStub = new PrincipalStub(fname, s"p${count}", defaultJvm)
      principalList += p
      // Set up Id set and subject set
      p.postIdSet(inference)
      p.postSubjectSetAndGetToken(inference)
      println(s"Made a principal: \n${p}")
    }
  } 

}
