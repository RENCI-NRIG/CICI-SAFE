package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException

import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging

/** trait for recording and replaying requests */
class ReplayManager extends LazyLogging {

  // Record and replay
  private var slangCallRecords = ListBuffer[SlangCallDescription]()

  import java.io._
  def persistRecords(): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(s"record-replay/SlangCallRecords"))
    oos.writeObject(slangCallRecords)
    oos.close
  }

  def loadRecords(): Unit = {
    val ois = new ObjectInputStream(new FileInputStream(s"record-replay/SlangCallRecords"))
    slangCallRecords = ois.readObject.asInstanceOf[ListBuffer[SlangCallDescription]]
    ois.close
  }

  def recordSlangCall(calldesc: SlangCallDescription): Unit = {
    slangCallRecords.synchronized {
      slangCallRecords += calldesc
    }
  }

  /**
   * Replay a previously recorded operation
   * Now we don't record operations to replay and their delegation info
   */
  def replayOperation(inference: Safelang, idx: Int): Boolean = {
    val calldesc = getSlangCallRecord(idx)
    calldesc.invokeLocal(inference)
  }

  def replayOperation(inference: Safelang, calldesc: SlangCallDescription): Boolean = {
    calldesc.invokeLocal(inference)
  }

  def getSlangCallRecord(idx: Int): SlangCallDescription= {
    if(slangCallRecords.length > idx) { 
      slangCallRecords(idx)
    } else {
      throw UnSafeException(s"Index of slang call desc out of bound: ${idx}  ${slangCallRecords.length}")
    } 
  }

  def getNumRecords(): Int = {
    slangCallRecords.length
  }

}
