package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException

import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging

/** trait for recording and replaying requests */
trait RecordReplay extends SafeBench with LazyLogging {
  // Record and replay
  var simpleDelegateRecords = ListBuffer[Tuple5[String, PrincipalStub, PrincipalStub, Seq[String], Seq[String]]]()
  var delegateThenQueryRecords = ListBuffer[Tuple7[String, PrincipalStub, PrincipalStub, PrincipalStub, String, Seq[String], Seq[String]]]()
  var simpleRemoteCallRecords = ListBuffer[Tuple4[PrincipalStub, String, String, Seq[String]]]()
  var remoteCallToServerRecords = ListBuffer[Tuple5[PrincipalStub, String, String, String, Seq[String]]]()   

  import java.io._
  def persistRecords(rtype: String): Unit = {
    var records: ListBuffer[_ <: AnyRef] = null
    records = rtype match {
      case "simpleDelegate" => simpleDelegateRecords
      case "delegateThenQuery" => delegateThenQueryRecords
      case "simpleRemoteCall" => simpleRemoteCallRecords
      case "remoteCallToServer" => remoteCallToServerRecords
      case _ => throw UnSafeException(s"Unknown record type: ${rtype}")
    }
    val oos = new ObjectOutputStream(new FileOutputStream(s"record-replay/${rtype}"))
    oos.writeObject(records)
    oos.close
  }

  def persistRecords(): Unit = {
    persistRecords("simpleDelegate")
    persistRecords("delegateThenQuery")
    persistRecords("simpleRemoteCall")
    persistRecords("remoteCallToServer")
  }

  def loadRecords(rtype: String): Unit = {
    val ois = new ObjectInputStream(new FileInputStream(s"record-replay/${rtype}"))
    rtype match {
      case "simpleDelegate" => simpleDelegateRecords = ois.readObject.asInstanceOf[ListBuffer[Tuple5[String, PrincipalStub, PrincipalStub, Seq[String], Seq[String]]]]
      case "delegateThenQuery" => delegateThenQueryRecords = ois.readObject.asInstanceOf[ListBuffer[Tuple7[String, PrincipalStub, PrincipalStub, PrincipalStub, String, Seq[String], Seq[String]]]]
      case "simpleRemoteCall" => simpleRemoteCallRecords = ois.readObject.asInstanceOf[ListBuffer[Tuple4[PrincipalStub, String, String, Seq[String]]]]
      case "remoteCallToServer" => remoteCallToServerRecords = ois.readObject.asInstanceOf[ListBuffer[Tuple5[PrincipalStub, String, String, String, Seq[String]]]]
      case _ => throw UnSafeException(s"Unknown record type: ${rtype}")
    }
    ois.close
  }

  def loadRecords(): Unit = {
    loadRecords("simpleDelegate")
    loadRecords("delegateThenQuery")
    loadRecords("simpleRemoteCall")
    loadRecords("remoteCallToServer")
  }

  def recordOperation[T <: AnyRef](optype: String, op: T, torecord: Boolean = false): Unit = {
    if(torecord) {
      if(optype == "simpleDelegate") {
        val t = op.asInstanceOf[Tuple5[String, PrincipalStub, PrincipalStub, Seq[String], Seq[String]]]
        simpleDelegateRecords.synchronized {
          simpleDelegateRecords += t
        }
      } else if(optype == "delegateThenQuery") {
        val t = op.asInstanceOf[Tuple7[String, PrincipalStub, PrincipalStub, PrincipalStub, String, Seq[String], Seq[String]]]
        delegateThenQueryRecords.synchronized {
          delegateThenQueryRecords += t
        }
      } else if(optype == "simpleRemoteCall") {
        val t = op.asInstanceOf[Tuple4[PrincipalStub, String, String, Seq[String]]]
        simpleRemoteCallRecords.synchronized {
          simpleRemoteCallRecords += t
        }
      } else if(optype == "remoteCallToServer") {
        val t = op.asInstanceOf[Tuple5[PrincipalStub, String, String, String, Seq[String]]]
        remoteCallToServerRecords.synchronized {
          remoteCallToServerRecords += t
        }
      } else {
        println(s"Unrecognized operation: type=${optype}   op=${op}")
      }
    }
  }

  /**
   * Replay a previous operation that's recorded
   * Now we don't record the replaying operations and their delegation info
   */
  def replayOperation(inference: Safelang, optype: String, idx: Int): Boolean = {
    if(optype == "simpleDelegate") {
      require(idx < simpleDelegateRecords.length, s"Invalid index: $idx (length: ${simpleDelegateRecords.length})")
      val (entrypoint, delegator, principal, envs, args) = simpleDelegateRecords(idx)
      simpleDelegate(inference, entrypoint, delegator, principal, envs, args)
      true
    } else if(optype == "delegateThenQuery") {
      require(idx < delegateThenQueryRecords.length, s"Invalid index: $idx (length: ${delegateThenQueryRecords.length})")
      val (entrypoint, delegator, principal, qserverprincipal, queryJvm, envs, args) = delegateThenQueryRecords(idx)
      delegateThenQuery(inference, entrypoint, delegator, principal, qserverprincipal, queryJvm, envs, args)
      true
    } else if(optype == "simpleRemoteCall") {
      require(idx < simpleRemoteCallRecords.length, s"Invalid index: $idx (length: ${simpleRemoteCallRecords.length})")
      val (principal, entrypoint, env, args) = simpleRemoteCallRecords(idx)
      principal.simpleRemoteCall(inference, entrypoint, env, args)
      true
    } else if(optype == "remoteCallToServer") {
      require(idx < remoteCallToServerRecords.length, s"Invalid index: $idx (length: ${remoteCallToServerRecords.length})")
      val (principal, entrypoint, specifiedJvm, env, args) = remoteCallToServerRecords(idx)
      principal.remoteCallToServer(inference, entrypoint, specifiedJvm, env, args)
      true
    } else {
      println(s"Invalid optype: $optype")
      false
    }
  }

}
