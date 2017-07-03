package safe.programming

import safe.safelang.{Safelang, SafelangManager}

import com.typesafe.config.ConfigFactory
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import safe.safelog.UnSafeException

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object TestHarness {

  def main(args: Array[String]) {
    println("[main] launched!")

    val usage = """
      Usage: BenchmarkHarness [--file|-f fileName] [--args|-a fileArgs] [--help|-h]
    """
    if(args.isEmpty) throw UnSafeException(usage)

    val optionalArgs = Map(
        "--file"        -> 'slangFile
      , "-f"            -> 'slangFile
      , "--args"        -> 'fileArgs
      , "-a"            -> 'fileArgs
      , "--jvmmap"      -> 'jvmMap       // file that stores the principal-jvm map
      , "-jvmm"         -> 'jvmMap
      , "-h"            -> 'help
      , "--help"        -> 'help
      , "-c"            -> 'concurrency
      , "--concurrency" -> 'concurrency
    )
    val requiredArgs = Nil

    val argMap = safe.safelog.Util.readCmdLine(args.toSeq, requiredArgs, optionalArgs, Map('help -> "false", 'concurrency -> "5"))

    if(argMap('help) == "true") {
      println(usage)
    } else {

     // Perform benchmark
      benchmark(argMap('slangFile), argMap.get('fileArgs), argMap('jvmMap), argMap('concurrency).toInt)
    }
  }
 
  def benchmark(slangFile: String, fileArgs: Option[String], jvmmapFile: String, concurrency: Int): Unit = { 
    val slangManager = SafelangManager()
    val inference0 = slangManager.createSafelang()
    inference0.compileSlang(slangFile, fileArgs) 

    val attestationBench = new CloudAttestationBench(concurrency, jvmmapFile, slangManager)
    attestationBench.run()

    //val strongBench = new StrongBench(concurrency, jvmmapFile, slangManager)
    //strongBench.run()
    //strongBench.benchNameParsing()

//    val difcBench = new DifcBench(concurrency, jvmmapFile, slangManager)
//    difcBench.run()

//    val geniBench = new SimpleGeniBench(inference0, jvmmapFile)
//    geniBench.run()


//    val geniBench = new GeniBench(concurrency, jvmmapFile, slangManager)
//    geniBench.replayParametrizedQueriesOverlappingChain()
    //geniBench.replayParametrizedQueries()
    //geniBench.replayQueries()
    //geniBench.testCacheWithReplay()
    //geniBench.testCache()
    //geniBench.testRandomOps()
    //geniBench.test()
//     geniBench.testFederationOps()
 
    //val inference0 = SafelangManager.createSafelang()
    //inference0.compileSlang(slangFile, fileArgs) 
    //val inference1 = SafelangManager.createSafelang()
    //val inference2 = SafelangManager.createSafelang()

    // Multi-threading with SimpleGeniBench
    //val geniBench0 = Future { 
    //  val geniBench = new SimpleGeniBench(inference0)
    //  geniBench.run()
    //}
    //val geniBench1 = Future { 
    //  val geniBench = new SimpleGeniBench(inference1)
    //  geniBench.run()
    //}
    //val geniBench2 = Future { 
    //  val geniBench = new SimpleGeniBench(inference2)
    //  geniBench.run()
    //}   
    //Await.result(Future.sequence(Seq(geniBench0, geniBench1, geniBench2)), 60 seconds)

    //SafesetsWeaver.touch(inference)
    //SafesetsWeaver.run(inference, 1000, 3)
  }
}
