package safe.safelang

import akka.actor.{ActorRef, ActorSystem}
import scala.collection.mutable.{Set => MutableSet}
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import setcache.SetCache
import safe.cache.SafeTable
import safesets._
import model.Principal
import safe.safelog.{SetId, Index, MutableCache, Statement, StrLit, Constant}

class Repl(
  val safeSetsClient: SafeSetsClient,
  self: String, saysOperator: Boolean, 
  val slangCallClient: SlangRemoteCallClient, 
  val localSetTable: SafeTable[SetId, SlogSet], val setCache: SetCache, val contextCache: ContextCache,
  val safelangId: Int, val serverPrincipalPool: MutableMap[SetId, Principal]
) extends safe.safelog.Repl(self, saysOperator) with SafelangService {

  stdPromptMsg = "slang> "
  //override val greet = s"Safe Language v0.1: $date (To quit, press Ctrl+D or q.)"
  override val greet = "Welcome to\n" +
                       safeBanner +
                       s"Safe Language v0.1: $date (To quit, press Ctrl+D or q.)"

  override def updatePromptSelf(): Unit = {
    if(envContext.contains(StrLit("Self"))) {
      val currentSelf = envContext(StrLit("Self")).asInstanceOf[Constant].id.name
      stdPromptMsg = currentSelf + "@slang> "
    } else {
      stdPromptMsg = "slang> "
    }
  }
}
  
object Repl {

  import akka.util.Timeout
  import com.typesafe.config.ConfigFactory
  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration._

  def main(args: Array[String]): Unit = {

    val usage = """
      Usage: Repl [--port|-p number] [--file|-f fileName] [--numWorkers|-n number] [--args|-a fileArguments] [--help|-h]
    """

    val optionalArgs = Map(
        "--port"       -> 'port
      , "-p"           -> 'port
      , "--file"       -> 'file
      , "-f"           -> 'file
      , "--args"       -> 'fileArgs
      , "-a"           -> 'fileArgs
      , "--numWorkers" -> 'numWorkers
      , "-n"           -> 'numWorkers
      , "--help"       -> 'help
      , "-h"           -> 'help
    )

    val argMap = safe.safelog.Util.readCmdLine(
        args.toSeq
      , requiredArgs = Nil
      , optionalArgs
      , Map('help -> "false", 'port -> "4001", 'numWorkers -> "2")
    )

    if(argMap('help) == "true") {
      println(usage)
    } else {

      val port: Int = argMap('port).toInt
      val numWorkers: Int = argMap('numWorkers).toInt

      val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port}")
	.withFallback(ConfigFactory.load())

      //val shutdownGracefully: Boolean = conf.getBoolean("safe.safelang.system.shutdownGracefully")
      val shutdownGracefully: Boolean = true
       //val storeURI                = conf.getString("safe.safesets.storeURI")
      //val requestTimeout          = Timeout(conf.getDuration("safe.service.requestTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)
      val requestTimeout: FiniteDuration          = FiniteDuration(conf.getDuration("safe.safesets.requestTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

      val localSetTable: SafeTable[SetId, SlogSet] = new SafeTable[SetId, SlogSet](
        1024 * 1024,
        0.75f,
        16
      )
      val system: ActorSystem = ActorSystem("Safelang")
      val safeSetsClient: SafeSetsClient = new SafeSetsClient(system)
      val slangCallClient = new SlangRemoteCallClient(system)
      val setCache: SetCache = new SetCache(localSetTable, safeSetsClient)
      val contextCache: ContextCache = new ContextCache(setCache)
      val safelangId: Int = 0
      val serverPrincipalPool: MutableMap[SetId, Principal] = null
      val inference = new Repl(safeSetsClient, Config.config.self, Config.config.saysOperator, slangCallClient, localSetTable, setCache, contextCache, safelangId, serverPrincipalPool)

      try {
        (argMap.get('file), argMap.get('fileArgs)) match {
          case (None, None)     => inference.repl() // The init call 
          case (Some(sf), None) => inference.printOutput(inference.evalFileWithTime(sf, 'ms)) // Evaluate expressions from a file.
          case (Some(sf), Some(args)) => 
            val argSeq = args.split(",").toSeq
            var _fileContents = scala.io.Source.fromFile(sf).mkString
            argSeq.zipWithIndex.foreach{ case (arg, idx) =>
              _fileContents = _fileContents.replaceAll(s"\\$$${idx + 1}", s"'$arg'")
            }
            // write to a temp file // TODO: fix this
            import java.nio.file.{Paths, Files}
            import java.nio.charset.StandardCharsets
            //val tmpFileName = s"/tmp/$sf" //TODO: extract fileName from path
            val tmpFileName = s"/tmp/${java.util.UUID.randomUUID()}"
            Files.write(Paths.get(tmpFileName), _fileContents.getBytes(StandardCharsets.UTF_8))
 
            inference.printOutput(inference.evalFileWithTime(tmpFileName, 'ms)) // Evaluate expressions from a file.
          case (None, Some(args)) => //ignore args or throw an exception?
        }
      } catch {
        case err: Throwable => 
          //println(err)
          err.printStackTrace()
          gracefulShutdown(system)
      }
      if(shutdownGracefully) gracefulShutdown(system)
    }
  }

  def gracefulShutdown(system: ActorSystem, prompt: Boolean = false): Unit = {
    if(!prompt) {
      system.shutdown()
      sys.addShutdownHook(system.shutdown())
    } else {
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
}
