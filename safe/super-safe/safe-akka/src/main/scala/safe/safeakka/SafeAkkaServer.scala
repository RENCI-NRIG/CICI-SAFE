package safe.safeakka

import akka.actor.{Actor, Props, ActorSystem, ActorRef, PoisonPill}
import com.typesafe.config.ConfigFactory
import safe.safelog.UnSafeException
import akka.util.Timeout
import scala.util.parsing.json._

object SafeAkkaServer {
  println("Starting SAFE Akka server")
  var shutdownGracefully: Boolean = true

  def main(args: Array[String]): Unit = {

    val usage = """
      Usage: SafeAkkaServer [--port|-p number] [--file|-f fileName] [--numWorkers|-n number] [--help|-h] role
      // Available roles: (frontend|restService|safeMaster|safeSetsWorker|slangWorker|all)
    """
    if(args.isEmpty) throw UnSafeException(usage)

    val optionalArgs = Map(
        "--port"        -> 'port
      , "-p"            -> 'port
      , "--file"        -> 'slangFile
      , "-f"            -> 'slangFile
      , "--args"        -> 'fileArgs
      , "-a"            -> 'fileArgs
      , "--numWorkers"  -> 'numWorkers
      , "-n"            -> 'numWorkers
      , "-h"            -> 'help
      , "--help"        -> 'help
    )
    val requiredArgs = Seq('role)
    val definedRoles = Set("frontend", "safeService", "safeMaster", "safeSetsWorker", "slangWorker", "all")

    val argMap = safe.safelog.Util.readCmdLine(args.toSeq, requiredArgs, optionalArgs, Map('help -> "false", 'port -> "4321", 'numWorkers -> "2"))
    if(!definedRoles.contains(argMap('role))) throw UnSafeException(s"SafeServiceComponent role not recognized: $usage")

    val role = argMap('role)

    if(argMap('help) == "true") {
      println(usage)
    } else if(argMap('role) == "safeService") {
      startAkkaService(port = Some(argMap('port).toInt), masterRole = Some("SafeMaster"), fileName = argMap('slangFile), fileArgs = argMap.get('fileArgs), numWorkers = argMap('numWorkers).toInt)
    } else {
      throw UnSafeException(s"Not yet implemented")
    }
  }

  def startAkkaService(port: Option[Int], masterRole: Option[String] = Some("SafeMaster"), fileName: String, fileArgs: Option[String], numWorkers: Int): Unit = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port.getOrElse(4321)}")
      .withFallback(ConfigFactory.load())

    val storeURI = conf.getString("safe.safesets.storeURI")

    import java.util.concurrent.TimeUnit
    val requestTimeout   = Timeout(conf.getDuration("safe.service.requestTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

    implicit val system = ActorSystem("SafeAkkaSystem", ConfigFactory.load.getConfig("safeakkaserver"))

    val safeAkkaActor = system.actorOf(RemoteSafeActor.props(storeURI, masterRole, fileName, fileArgs, numWorkers, requestTimeout), RemoteSafeActor.name)

    //testing
    //val request_method = ("method" -> "guardTest")
    //val args_map = Map[String, String]()
    //val json_req = JSONObject (Map (request_method, "args" -> JSONObject(args_map))) .toString()
    //println("**************** Sending a test message *****************")
    //safeAkkaActor ! json_req      

//    val request_method = ("method" -> "guardTest")
//    val args_map = Map[String, String]("?Z" -> "ErcZNjpHYfLSMrN93uNY_Yx2W9cSyG_J9rn9F9kjGqM")
//    val json_req = JSONObject (Map (request_method, "args" -> JSONObject(args_map))) .toString()
//    println("**************** Sending a testing message *****************")
//    println("*** " + json_req)
//    safeAkkaActor ! json_req 

    val request_method = ("method" -> "guardTest")
    val args_map = Map[String, String]("?DIFCRef" -> "80RKAK5lNvXu8MUsAPlrzzPWELJm_juYWiWSUzzY6lU",  
                                       "?AdminRef" -> "r_kp-EqXxriNeoealpIQ_s0oHzmn-VPCqMDGsHC-3XU",
                                       "?Program" -> "w7sFlh0xDzhA/LNzgAP3qSls6D54uUi0XG8Z8Fjty0o=",
                                       "?User" -> "ImbZJXXB_O8I10mmPVc-KzWnNKODXpTwo-KqrVdlmTM",
                                       "?File" -> "hdfs://ec2-184-73-118-39.compute-1.amazonaws.com:9000/test/case_2/friendships_graph.txt"
                                       //"?File" -> "qiang_tmp.txt"
                                       )
    val json_req = JSONObject (Map (request_method, "args" -> JSONObject(args_map))) .toString()
    println("**************** Sending a testing message *****************")
    println("*** " + json_req)
    safeAkkaActor ! json_req 

    if(shutdownGracefully) gracefulShutdown(system)
  }


  def gracefulShutdown(system: ActorSystem): Unit = {
    // Allow an operator to shutdown the service gracefully
    val terminate: Boolean = {
      def loop(): Boolean = scala.io.StdIn.readLine() match {
        case s if s.toLowerCase.matches("""^[y\n](es)?""") => true
        case _ => println(s"terminate? [y(es)?]"); loop()
      }
      loop()
    }
    if(terminate) system.shutdown()

    /**
     * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
     */
    sys.addShutdownHook(system.shutdown())
  }

}
