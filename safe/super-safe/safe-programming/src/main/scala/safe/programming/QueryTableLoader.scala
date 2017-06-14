package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException

import scala.io.Source
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging

/** trait for loading queries from a table */
trait QueryTableLoader extends BenchCommons with LazyLogging {
  // Format: [serverPrincipal]  [subject]  [bearerRef]  [sliceId]  
  val createSliverTable = ListBuffer[Tuple4[String, String, String, String]]() 

  // Format: [serverPrincipal]  [subject]  [bearerRef]  [projectId]  
  val createSliceTable = ListBuffer[Tuple4[String, String, String, String]]()

  // Format: [serverPrincipal]  [subject]  [bearerRef] 
  val createProjectTable = ListBuffer[Tuple3[String, String, String]]()

  private val createSliverType: Int   = 0
  private val createSliceType: Int    = 1
  private val createProjectType: Int  = 2


  def loadTable(filepath: String, qtype: Int): Unit = {
    val allLines = Source.fromFile(filepath).getLines
    qtype match {
      case `createSliverType`  =>
        for(l <- allLines) {
          val parts = l.split("\\s+")
          assert(parts.length == 4, s"Invalid query param list (length should be 4): $l")
          val r = (parts(0), parts(1), parts(2), parts(3)) 
          createSliverTable += r 
        } 
      case `createSliceType`   => 
        for(l <- allLines) {
          val parts = l.split("\\s+")
          assert(parts.length == 4, s"Invalid query param list (length should be 4): $l")
          val r = (parts(0), parts(1), parts(2), parts(3))
          createSliceTable += r 
        }
      case `createProjectType` =>
        for(l <- allLines) {
          val parts = l.split("\\s+")
          assert(parts.length == 3, s"Invalid query param list (length should be 3): $l")
          val r = (parts(0), parts(1), parts(2)) 
          createProjectTable += r 
        }
      case _ => throw UnSafeException(s"Unknown query type: ${qtype}")
    }   
  }
 
  def loadTables(path: String): Unit = {
    loadTable(s"$path/createSliverTable", createSliverType)
    loadTable(s"$path/createSliceTable", createSliceType)
    loadTable(s"$path/createProjectTable", createProjectType)
    logger.info(s"Loading tables completes:")
    logger.info(s"createSliverTable.length=${createSliverTable.length}")
    logger.info(s"createSliceTable.length=${createSliceTable.length}")
    logger.info(s"createProjectTable.length=${createProjectTable.length}")
    println(s"createSliverTable.length=${createSliverTable.length}")
    println(s"createSliceTable.length=${createSliceTable.length}")
    println(s"createProjectTable.length=${createProjectTable.length}")
  }

  /**
   * Replay a parametrized query with its param list
   * Now we don't record the replaying operations and their delegation info
   */
  def replayParamQuery(inference: Safelang, entrypoint: String, idx: Int): Boolean = {
    if(entrypoint == "createSliver") {
      require(idx < createSliverTable.length, s"Invalid index: $idx (length: ${createSliverTable.length})")
      val (serverPrincipal, subject, bearerRef, sliceId) = createSliverTable(idx)
      buildAndQuery(inference, entrypoint, testingCacheJvm, serverPrincipal, speaker=None, 
                    subject=Some(subject), objectId=None, bearerRef=Some(bearerRef), 
                    args=Seq(sliceId))
      true
    } else if(entrypoint == "createSlice") {
      require(idx < createSliceTable.length, s"Invalid index: $idx (length: ${createSliceTable.length})")
      val (serverPrincipal, subject, bearerRef, projectId) = createSliceTable(idx)
      buildAndQuery(inference, entrypoint, testingCacheJvm, serverPrincipal, speaker=None, 
                    subject=Some(subject), objectId=None, bearerRef=Some(bearerRef), 
                    args=Seq(projectId))
      true
    } else if(entrypoint == "createProject") {
      require(idx < createProjectTable.length, s"Invalid index: $idx (length: ${createProjectTable.length})")
      val (serverPrincipal, subject, bearerRef) = createProjectTable(idx)
      buildAndQuery(inference, entrypoint, testingCacheJvm, serverPrincipal, speaker=None, 
                    subject=Some(subject), objectId=None, bearerRef=Some(bearerRef), 
                    args=Seq())
      true
    } else {
      println(s"Invalid entrypoint: $entrypoint")
      false
    }
  }
}
