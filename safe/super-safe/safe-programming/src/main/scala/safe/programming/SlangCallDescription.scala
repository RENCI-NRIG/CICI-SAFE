package safe.programming

import safe.safelang.Safelang
import safe.safelog.UnSafeException
import scala.collection.mutable.ListBuffer

class SlangCallDescription(val tpe: Int, val tuple: Product) extends Serializable with BenchCommons {

  def getOperationName(): String = {
    val t = tuple
    tpe match {
      case CALL =>
        t.asInstanceOf[SimpleRemoteCallTuple]._1
      case CALLSERVER =>
        t.asInstanceOf[RemoteCallToServerTuple]._1
      case DELEGATE =>
        t.asInstanceOf[SimpleDelegateTuple]._1
      case DELEGATE_QUERY =>
        t.asInstanceOf[DelegateThenQueryTuple]._1
    }
  }
  
  def invokeLocal(sl: Safelang): Boolean = {
    val t: Product = tuple
    //println(s"invokeLocal   tpe:${tpe}      tuple:${tuple}")
    tpe match {
      case CALL =>
        //assert(t.isInstanceOf[SimpleRemoteCallTuple], s"SimpleRemoteCallTuple expected: ${t}")
        val desc = t.asInstanceOf[SimpleRemoteCallTuple]
        val p: PrincipalStub = desc._2
        val res: String = p.simpleRemoteCall(sl, desc._1, desc._3, desc._4)
        !(res.isEmpty)
      case CALLSERVER =>
        //assert(t.isInstanceOf[RemoteCallToServerTuple], s"RemoteCallToServerTuple expected: ${t}")
        val desc = t.asInstanceOf[RemoteCallToServerTuple]
        val p: PrincipalStub = desc._2
        val res = p.remoteCallToServer(sl, desc._1, desc._3, desc._4, desc._5)
        !(res.isEmpty)
      case DELEGATE =>
        //assert(t.isInstanceOf[SimpleDelegateTuple], s"SimpleDelegateTuple expected: ${t}")
        val desc = t.asInstanceOf[SimpleDelegateTuple]
        simpleDelegate(sl, desc._1, desc._2, desc._3, desc._4, desc._5)
      case DELEGATE_QUERY =>
        //assert(t.isInstanceOf[DelegateThenQueryTuple], s"DelegateThenQueryTuple expected: ${t}")
        val desc = t.asInstanceOf[DelegateThenQueryTuple]
        delegateThenQuery(sl, desc._1, desc._2, desc._3, desc._4, desc._5, desc._6, desc._7)
    }
  }
}

object SlangCallDescription {
  def apply(op: String, delegator: PrincipalStub, principal: PrincipalStub, 
            envs: Seq[String], args: Seq[String]): SlangCallDescription = {
    val t = (op, delegator, principal, envs, args)
    new SlangCallDescription(DELEGATE, t)
  }

  def apply(op: String, delegator: PrincipalStub, principal: PrincipalStub,
            qserverprincipal: PrincipalStub, queryJvm: String, 
            envs: Seq[String], args: Seq[String]): SlangCallDescription = {
    val t = (op, delegator, principal, qserverprincipal, queryJvm, envs, args)
    new SlangCallDescription(DELEGATE_QUERY, t)
  }

  def apply(op: String, serverPrincipal: PrincipalStub, env: String, 
      args: Seq[String]): SlangCallDescription = {
    val t = (op, serverPrincipal, env, args)
    new SlangCallDescription(CALL, t)
  }

  def apply(op: String, serverPrincipal: PrincipalStub, serverJvm: String, 
      env: String, args: Seq[String]): SlangCallDescription = {
    val t = (op, serverPrincipal, serverJvm, env, args)
    new SlangCallDescription(CALLSERVER, t)
  }
}
