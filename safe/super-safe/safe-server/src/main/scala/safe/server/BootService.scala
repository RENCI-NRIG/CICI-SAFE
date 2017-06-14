package safe.server

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Address, AddressFromURIString, Identify, Props, RootActorPath}
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import safe.safelog.UnSafeException
import safe.safelang.Config

import java.net.{NetworkInterface, InetAddress}
import scala.collection.JavaConversions._
import com.typesafe.scalalogging.LazyLogging

/**
 * BootService trait implements ``SafeSystem`` by starting the
 * required ``ActorSystem`` and registering the termination handler to stop the
 * system when the JVM exits.
 */
object BootService extends LazyLogging {

  var shutdownGracefully: Boolean = true

  def main(args: Array[String]): Unit = {

    val usage = """
      Usage: BootService [--port|-p number] [--file|-f fileName] [--numWorkers|-n number] [--help|-h] [--role|-r]
      // Available roles: (safeService|safesetsService)
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
      , "-r"            -> 'role
      , "--role"        -> 'role
      , "-hp"           -> 'httpPort
      , "--httpPort"    -> 'httpPort
      , "-kd"           -> 'keyDir         // Directory of key pairs
      , "--keyDir"      -> 'keyDir
    )
    //val requiredArgs = Seq('role)
    val requiredArgs = Nil
    val definedRoles = Set("safeService", "safesetsService")

    val argMap = safe.safelog.Util.readCmdLine(args.toSeq, requiredArgs, optionalArgs, Map('help -> "false", 'port -> "4001", 'numWorkers -> "2", 'role -> "safeService", 'httpPort -> "7777", 'keyDir -> Config.config.keyPairDir))
    //if(!definedRoles.contains(argMap('role))) throw UnSafeException(s"SafeServiceComponent role not recognized: $usage")

    //val role = argMap('role)
    val role = argMap('role)

    if(argMap('help) == "true") {
      println(usage)
    } else if(role == "safeService") {
      startRestService(port = Some(argMap('port).toInt), masterRole = Some("safeService"), fileName = argMap('slangFile), fileArgs = argMap.get('fileArgs), numWorkers = argMap('numWorkers).toInt, httpPort = Some(argMap('httpPort).toInt), keypairDir = argMap('keyDir))
    } else if(role == "safesetsService") {
      startRestService(port = Some(argMap('port).toInt), masterRole = Some("safesetsService"), fileName = argMap('slangFile), fileArgs = argMap.get('fileArgs), numWorkers = argMap('numWorkers).toInt, httpPort = Some(argMap('httpPort).toInt), keypairDir = argMap('keyDir))
    } else {
      throw UnSafeException(s"Not yet implemented")
    }
  }

  def startRestService(port: Option[Int], masterRole: Option[String] = Some("safeService"), fileName: String, fileArgs: Option[String], numWorkers: Int, httpPort: Option[Int], keypairDir: String): Unit = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port.getOrElse(0)} \n safe.service.port=${httpPort.getOrElse(0)}").withFallback(ConfigFactory.load())

    //val ipAddr = conf.getString("safe.service.interface")
    val interfaces = enumerationAsScalaIterator(NetworkInterface.getNetworkInterfaces).toList.filter(!_.isLoopback).filter(_.isUp)
    val ipAddr = interfaces.head.getInterfaceAddresses.filter(_.getBroadcast != null).head.getAddress.getHostAddress.toString
    val hPort      = conf.getInt("safe.service.port")

    val storeURI = conf.getString("safe.safesets.storeURI")

    import java.util.concurrent.TimeUnit
    val requestTimeout   = Timeout(conf.getDuration("safe.service.requestTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

    implicit val system = ActorSystem("SafeSystem", conf)

    import akka.io.IO
    import spray.can.Http

    val safeRest = system.actorOf(frontend.RestfulService.props(storeURI, masterRole, fileName, fileArgs, numWorkers, requestTimeout, keypairDir), frontend.RestfulService.name)

    println(s"HTTP service: $ipAddr:$hPort")
    logger.info(s"HTTP service: $ipAddr:$hPort")

    IO(Http) ! Http.Bind(safeRest, ipAddr, hPort)

    if(shutdownGracefully) gracefulShutdown(system)
  }

  def gracefulShutdown(system: ActorSystem): Unit = {
    // Allow an operator to shutdown the service gracefully
    //val terminate: Boolean = {
    //  def loop(): Boolean = scala.io.StdIn.readLine() match {
    //    case s if s.toLowerCase.matches("""^[y\n](es)?""") => true
    //    case _ => println(s"terminate? [y(es)?]"); loop()
    //  }
    //  loop()
    //}
    //if(terminate) system.shutdown()

    /**
     * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
     */
    sys.addShutdownHook(system.shutdown())
  }
}
