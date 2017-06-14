package safe.safeakka

import akka.actor.{Actor, Props, ActorSystem, ActorRef, PoisonPill}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}

import java.util.UUID

import safe.safelang.{Safelang, SetTerm}
import safe.safelog._
import safe.safelang.InferenceService.{proofContext}
import scala.util.parsing.json.{JSON, JSONObject}
import scala.concurrent.ExecutionContext.Implicits.global
import safe.safeakka.SAFEGuardMessageProtocol._

import safe.safelang.SlogResult

object RemoteSafeActor {
  def props(storeURI: String, role: Option[String], slangFile: String, fileArgs: Option[String], numWorkers: Int, requestTimeout: Timeout): Props =
    Props(classOf[RemoteSafeActor], storeURI, role, slangFile, fileArgs, numWorkers, requestTimeout)
  def name = "safeakkaserver"
}


class RemoteSafeActor(storeURI: String, role: Option[String], slangFile: String, fileArgs: Option[String], numWorkers: Int, requestTimeout: Timeout) extends Actor {

  def actorRefFactory = context

  val setCacheInitialCapacity: Int = 4096
  val setCacheLoadFactor: Float    = 0.75f
  val setCacheConcurrency: Int     = 16
  val timeout: FiniteDuration = FiniteDuration(30, java.util.concurrent.TimeUnit.SECONDS)

  val safeSetsMaster = context.actorOf(
      safe.safesets.client.Master.props(timeout, setCacheInitialCapacity, setCacheLoadFactor, setCacheConcurrency)
    , safe.safesets.client.Master.name
  )

  for(i <- 1 to numWorkers) {
    context.actorOf(safe.safesets.client.Worker.props(safeSetsMaster, 10.seconds, 30.seconds, storeURI), safe.safesets.client.Worker.name + i)
  }

  val inference = safe.safelang.Safelang(safeSetsMaster)

  val guards: Map[String, Seq[String]] = compileSlang(slangFile, fileArgs)

  def compileSlang(
      slangFile: String
    , fileArgs: Option[String]
  ): Map[String, Seq[String]] = (slangFile, fileArgs) match {
    case (file, None)       => compileSlangHelper(file, isFile = true)
    case (file, Some(args)) =>
      val argSeq = args.split(",").toSeq
      var _fileContents = scala.io.Source.fromFile(file).mkString
      argSeq.zipWithIndex.foreach{ case (arg, idx) =>
        _fileContents = _fileContents.replaceAll(s"\\$$${idx + 1}", s"'$arg'")
      }
      compileSlangHelper(_fileContents)
  }

  private def compileSlangHelper(source: String, isFile: Boolean = false): Map[String, Seq[String]] = {
    val compiledSlangProgram = if(isFile) inference.initFile(source) else inference.init(source)
    println("[Qiang] compiled slang program")
    compiledSlangProgram.foreach{ case (k, v) => println(s"[Qiang] key=$k   value=$v") }

    val guardSet: Set[safe.safelog.Statement] = compiledSlangProgram(StrLit("defguard0"))
    val guards: Map[String, Seq[String]] = guardSet.collect {
      case safe.safelog.Assertion(safe.safelog.Structure(method, args, _, _, _) +: other) =>
        println("[Qiang]  method.name=" + method.name + "  \targs.map=" + args.map(arg => arg.toString))
        (method.name, args.map(arg => arg.toString))
    }.toMap
    //println("[Qiang] defguard1: " + compiledSlangProgram(StrLit("defguard1")))
    guards
  }

  def executeAGuardMethod(guardMethod: String, inference: Safelang, args: Seq[String]): Tuple2[Boolean, String] = {
//    val queryResult = Future {
//      val query = Query(Seq(Structure(guardMethod, args.map{
//        case x: String if(x.startsWith("?")) => Variable(x)
//        case x: String                       => Constant(x)
//      })))
//      println("[Qiang] solve query " + query + "......")
//      inference.solveSlang(Seq(query), false)
//    }
//    val result = queryResult.onComplete {
//      case Success(res) => println(s"""${res.mkString("; ")}\n"""); res.mkString
//      case Failure(res) => println(s"Query failed with msg: $res"); res.mkString
//    }
    val query = Query(Seq(Structure(guardMethod, args.map{
      case x: String if(x.startsWith("?")) => Variable(x)
      case x: String                       => Constant(x)
    })))

    println("[Qiang] solve query " + query + "......")
    val result: Seq[Seq[Statement]] = inference.solveSlang(Seq(query), false)

    println("****[Qiang]: end of the query")
    println("Result class: " + result.getClass)
    println("Result: " + result);
    println("result.head: " + result.head)
    println("result.head.length: " + result.head.length)

    result.head.headOption match {
      //case Some(stmt) if stmt.terms.length > 0 => println("stmt: " + stmt + "    stmt.terms.length: " + stmt.terms.length + "     stmt.terms.head.id.name: " + stmt.terms.head.id.name + "    stmt.terms.head: " + stmt.terms.head + "     stmt.terms.head.getClass: " + stmt.terms.head.getClass); println("    stmt.terms.head.statements.length: " + stmt.terms.head.asInstanceOf[SlogResult].statements.length); println("    stmt.terms.head.statements.head.size: " + stmt.terms.head.asInstanceOf[SlogResult].statements.head.size);   println("    stmt.terms.head.statements.head.head.terms.head.terms.length: " + stmt.terms.head.asInstanceOf[SlogResult].statements.head.head.terms.head.asInstanceOf[Structure].terms.length);  (true, result.mkString)
      case Some(stmt) if stmt.terms.head.asInstanceOf[SlogResult].statements.head.size > 0 => (true, result.mkString)
      case _ =>   (false, result.mkString)
    }   
  }

  def receive = {
    case msg: String => JSON.parseFull(msg) match {
      case Some(request: Map[_, _]) =>
        val req = request.asInstanceOf[Map[String, _]]
        req.foreach { case (k, v) => println("[Qiang]:  request key=" + k + "      request value =" + v) }
        req.get(GUARD_METHOD) match {
          case Some(guardMethod: String) if guards.contains(guardMethod) => 
            val req_guard = guardMethod
            req.get(GUARD_ARGS) match {
              case Some(map: Map[_, _]) => 
                val args_map = map.asInstanceOf[Map[String, String]]
                val req_args = guards(req_guard).map {
                  case arg_name => args_map.get(arg_name) match {
                    case Some(arg) => arg
                    case None => arg_name
                  }
                }
                // Execue guard method with parsed arguments
                val res: Tuple2[Boolean, String] = executeAGuardMethod(req_guard, inference, req_args)
                val res_str = (res._1.toString(), res._2)
                val json_resp = JSONObject(Map(res_str)).toString()
                println("*********[[Qiang]] Sending to [" + sender + "] the message: " + json_resp)
                sender ! json_resp
              case _ => println("Invalid args map!")
            }
          case _ => println("Requested guard method doesn't exist!")
        }
      case _ => println("Error in parsing JSON message: " + msg)
    }
    case _ => println("Recieved some wrong msg rather than a JSON string!")
  }

}
