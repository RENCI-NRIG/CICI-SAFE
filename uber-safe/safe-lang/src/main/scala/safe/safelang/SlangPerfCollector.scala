package safe.safelang

import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import java.io._
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

class SlangPerfCollector() {
  // Define a set of locks to protect each perf log
  private val latencyLock = new Object()
  private val throughputLock = new Object()
  private val delegationLock = new Object()
  private val contextSizeLock = new Object()
  private val retryLock = new Object()
  private val setcacheMissLock = new Object()
  private val cpuLoadLock = new Object()
  private val setParsingTimeLock = new Object()
  private val setFetchTimeLock = new Object()
  private val setVerifyTimeLock = new Object()
  private val setPostTimeLock = new Object()
  private val setSignTimeLock = new Object()
  private val contextBuildingTimeLock = new Object()
  private val contextRenderTimeLock = new Object()
  private val closureFetchTimeLock = new Object()
  private val slangQueryLatencyLock = new Object()
  private val logicInferTimeLock = new Object()
  private val dbBuiltTimeLock = new Object()
  private val requestLatencyLock = new Object()
  private val starPerfStatsLock = new Object()

  private val partMaxSize = 5000

  // Slang perf logs
  private var latency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)         // [latency of a req, t, req desc]   
  private var throughput = new ArrayBuffer[Tuple3[Double, Long, String]](partMaxSize)    // [throughput, t, measure period]
  private var delegation = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)       // [delegation depth, t, op desc] 

  private var contextSize = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)      // [context size, t, context desc]
  private var retry = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)            // [retry context size, t, retry query]
  private var setcacheMiss = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)     // [total misses, t, retry query]
  private var cpuLoad = new ArrayBuffer[Tuple3[Double, Long, String]](partMaxSize)       // [cpu utilization, t, lastReq]
 
  private var setParsingTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
  private var setFetchTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
  private var setVerifyTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
  private var setPostTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
  private var setSignTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
  private var contextBuildingTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) // including fetch, parsing, verify, cnt rendering 
  private var contextRenderTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) // only context rendering 
  private var closureFetchTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)  // only clousure fetch; 
  // contextBuildingTime = contextRenderTime + closureFetchTime 
  private var slangQueryLatency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)   // [latency of a slang infer, t, slang query]   
  private var logicInferTime = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)      // [exec time of a logic infer, t, infer desc]
  private var dbBuiltTime = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)         // [time of building styla db, t, db desc]
  private var requestLatency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)      // [latency of a request, t, request]   
  private var starPerfStats = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)           // [star perf, t, desc] placeholder for ad-hoc perf (*) stats

  // For gathering cpu usage
  private val mxbean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean().asInstanceOf[OperatingSystemMXBean]
  
  private var isInited = true 

  def isCollectorOn(): Boolean = {
    Config.config.perfCollectorOn && isInited 
  }

  def addLatency(latMillis: Long, reqDesc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      latencyLock.synchronized {
        val r = (latMillis, t, reqDesc)
        latency += r
      }
    }
  }

  def addDelegation(depth: Int, opDesc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      delegationLock.synchronized {
        val r = (depth, t, opDesc)
        delegation += r
      }
    }
  } 

  def addContextSize(cntSize: Int, cntDesc: String): Unit = {
    if(isCollectorOn) { 
      val t = System.currentTimeMillis
      contextSizeLock.synchronized {
        val r = (cntSize, t, cntDesc)
        contextSize += r
      }
    }
  } 

  def addThroughput(xput: Double, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      throughputLock.synchronized {
        val r = (xput, t, desc)
        throughput += r
      }
    }
  }

  def addRetry(retryCntSize: Int, query: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      retryLock.synchronized {
        val r = (retryCntSize, t, query)
        retry += r
      }
    }
  }

  def addSetcacheMiss(totalmisses: Int, token: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setcacheMissLock.synchronized {
        val r = (totalmisses, t, token)
        setcacheMiss += r
      }
    }
  }

  def addCpuLoad(desc: String): Unit = {
    if(isCollectorOn) {
      val cpuUtil = mxbean.getSystemCpuLoad()  // getProcessCpuLoad() for the jvm 
      val t = System.currentTimeMillis
      cpuLoadLock.synchronized {
        val r = (cpuUtil, t, desc)
        cpuLoad += r
      }
    }
  }

  def addSetParsingTime(parsingt: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setParsingTimeLock.synchronized {
        val r = (parsingt, t, desc)
        setParsingTime += r
      }
    }
  }

  def addSetFetchTime(fetcht: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setFetchTimeLock.synchronized {
        val r = (fetcht, t, desc)
        setFetchTime += r
      }
    }
  }

  def addSetVerifyTime(verifyt: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setVerifyTimeLock.synchronized {
        val r = (verifyt, t, desc)
        setVerifyTime += r
      }
    }
  }

  def addSetPostTime(postt: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setPostTimeLock.synchronized {
        val r = (postt, t, desc)
        setPostTime += r
      }
    }
  }

  def addSetSignTime(signt: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      setSignTimeLock.synchronized {
        val r = (signt, t, desc)
        setSignTime += r
      }
    }
  }

  def addContextBuildingTime(buildt: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      contextBuildingTimeLock.synchronized {
        val r = (buildt, t, desc)
        contextBuildingTime += r
      }
    }
  }

  def addContextRenderTime(rendert: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      contextRenderTimeLock.synchronized {
        val r = (rendert, t, desc)
        contextRenderTime += r
      }
    }
  }

  def addClosureFetchTime(fetcht: String, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      closureFetchTimeLock.synchronized {
        val r = (fetcht, t, desc)
        closureFetchTime += r
      }
    }
  }

  def addSlangQueryLatency(lat: Long, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      slangQueryLatencyLock.synchronized {
        val r = (lat, t, desc)
        slangQueryLatency += r
      }
    }
  }

  def addLogicInferTime(lat: Long, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      logicInferTimeLock.synchronized {
        val r = (lat, t, desc)
        logicInferTime += r
      }
    }
  }

  def addDbBuiltTime(lat: Long, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      dbBuiltTimeLock.synchronized {
        val r = (lat, t, desc)
        dbBuiltTime += r
      }
    }
  }

  def addRequestLatency(lat: Long, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      requestLatencyLock.synchronized {
        val r = (lat, t, desc)
        requestLatency += r
      }
    }
  }

  def addStarPerfStats(metric: Long, desc: String): Unit = {
    if(isCollectorOn) {
      val t = System.currentTimeMillis
      starPerfStatsLock.synchronized {
        val r = (metric, t, desc)
        starPerfStats += r
      }
    }
  }

  def init(): Unit = {
    isInited = true
  }

  def persist(data: ArrayBuffer[_ <: Tuple3[_ <: Any, Long, String]], filepath: String): Unit = {  
    if(isCollectorOn) {
      val file = new File(filepath)    
      val bw = new BufferedWriter(new FileWriter(file))
      val startingIdx = 0
      for(i <- startingIdx to data.size-1) {
        val d = data(i)
        bw.write(s"${d._1}\t${d._2}\t${d._3}\n") 
      }
      bw.close()
      println("Persisting completes")
    }
  }
  
  def persist(perfFilepath: String, allRecords: Boolean = true): Unit = { // Output the collected performance measures to disk
    if(isCollectorOn) {
      val s = System.nanoTime()

      val latencyFilepath = s"${perfFilepath}.latency"
      val _latency = latency
      latencyLock.synchronized {
        latency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)    
      } 
      println(s"Persisting ${_latency.size} latency items to ${latencyFilepath}")
      persist(_latency, latencyFilepath)
   
      
      val delegationFilepath = s"${perfFilepath}.delegation"
      val _delegation = delegation   
      delegationLock.synchronized {
        delegation = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_delegation.size} delegation items to ${delegationFilepath}")
      persist(_delegation, delegationFilepath)
      
      val contextFilepath = s"${perfFilepath}.contextsize"
      val _contextSize = contextSize
      contextSizeLock.synchronized {
        contextSize = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_contextSize.size} context items to ${contextFilepath}")
      persist(_contextSize, contextFilepath)

      val xputFilepath = s"${perfFilepath}.throughput"
      val _throughput = throughput
      throughputLock.synchronized {
        throughput = new ArrayBuffer[Tuple3[Double, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_throughput.size} throughput items to ${xputFilepath}")
      persist(_throughput, xputFilepath) 

      val retryFilepath = s"${perfFilepath}.retry"
      val _retry = retry
      retryLock.synchronized {
        retry = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)  
      }
      println(s"Persisting ${_retry.size} retry items to ${retryFilepath}")
      persist(_retry, retryFilepath)

      val setcacheMissFilepath = s"${perfFilepath}.setcachemiss"
      val _setcacheMiss = setcacheMiss
      setcacheMissLock.synchronized {
        setcacheMiss = new ArrayBuffer[Tuple3[Int, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setcacheMiss.size} set cache miss items to ${setcacheMissFilepath}")
      persist(_setcacheMiss, setcacheMissFilepath)

      val cpuLoadFilepath = s"${perfFilepath}.cpuload"
      val _cpuLoad = cpuLoad
      cpuLoadLock.synchronized {
        cpuLoad = new ArrayBuffer[Tuple3[Double, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_cpuLoad.size} cpu load items to ${cpuLoadFilepath}")
      persist(_cpuLoad, cpuLoadFilepath)

      val setParsingTimeFilepath = s"${perfFilepath}.set.parsing.time"
      val _setParsingTime = setParsingTime
      setParsingTimeLock.synchronized {
        setParsingTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setParsingTime.size} set parsing items to ${setParsingTimeFilepath}")
      persist(_setParsingTime, setParsingTimeFilepath)

      val setFetchTimeFilepath = s"${perfFilepath}.set.fetch.time"
      val _setFetchTime = setFetchTime
      setFetchTimeLock.synchronized {
        setFetchTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setFetchTime.size} set fetch items to ${setFetchTimeFilepath}")
      persist(_setFetchTime, setFetchTimeFilepath)

      val setVerifyTimeFilepath = s"${perfFilepath}.set.verify.time"
      val _setVerifyTime = setVerifyTime
      setVerifyTimeLock.synchronized {
        setVerifyTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setVerifyTime.size} set verify items to ${setVerifyTimeFilepath}")
      persist(_setVerifyTime, setVerifyTimeFilepath)

      val setPostTimeFilepath = s"${perfFilepath}.set.post.time"
      val _setPostTime = setPostTime
      setPostTimeLock.synchronized {
        setPostTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setPostTime.size} set post items to ${setPostTimeFilepath}")
      persist(_setPostTime, setPostTimeFilepath)

      val setSignTimeFilepath = s"${perfFilepath}.set.sign.time"
      val _setSignTime = setSignTime
      setSignTimeLock.synchronized {
        setSignTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_setSignTime.size} set sign items to ${setSignTimeFilepath}")
      persist(_setSignTime, setSignTimeFilepath)

      val contextBuildingTimeFilepath = s"${perfFilepath}.context.building.time"
      val _contextBuildingTime = contextBuildingTime
      contextBuildingTimeLock.synchronized {
        contextBuildingTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_contextBuildingTime.size} context building items to ${contextBuildingTimeFilepath}")
      persist(_contextBuildingTime, contextBuildingTimeFilepath)

      val contextRenderTimeFilepath = s"${perfFilepath}.context.render.time"
      val _contextRenderTime = contextRenderTime
      contextRenderTimeLock.synchronized {
        contextRenderTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_contextRenderTime.size} context render items to ${contextRenderTimeFilepath}")
      persist(_contextRenderTime, contextRenderTimeFilepath)

      val closureFetchTimeFilepath = s"${perfFilepath}.closure.fetch.time"
      val _closureFetchTime = closureFetchTime
      closureFetchTimeLock.synchronized {
        closureFetchTime = new ArrayBuffer[Tuple3[String, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_closureFetchTime.size} closure fetch items to ${closureFetchTimeFilepath}")
      persist(_closureFetchTime, closureFetchTimeFilepath)

      val slangQueryLatencyFilepath = s"${perfFilepath}.slang.query.latency.time"
      val _slangQueryLatency = slangQueryLatency
      slangQueryLatencyLock.synchronized {
        slangQueryLatency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_slangQueryLatency.size} slang query latency items to ${slangQueryLatencyFilepath}")
      persist(_slangQueryLatency, slangQueryLatencyFilepath)

      val logicInferTimeFilepath = s"${perfFilepath}.logic.infer.time"
      val _logicInferTime = logicInferTime
      logicInferTimeLock.synchronized {
        logicInferTime = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize)
      }
      println(s"Persisting ${_logicInferTime.size} logic infer items to ${logicInferTimeFilepath}")
      persist(_logicInferTime, logicInferTimeFilepath)

      val dbBuiltTimeFilepath = s"${perfFilepath}.db.built.time"
      val _dbBuiltTime = dbBuiltTime
      dbBuiltTimeLock.synchronized {
        dbBuiltTime = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_dbBuiltTime.size} db building items to ${dbBuiltTimeFilepath}")
      persist(_dbBuiltTime, dbBuiltTimeFilepath)

      val requestLatencyFilepath = s"${perfFilepath}.request.latency.time"
      val _requestLatency = requestLatency
      requestLatencyLock.synchronized {
        requestLatency = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_requestLatency.size} request latency items to ${requestLatencyFilepath}")
      persist(_requestLatency, requestLatencyFilepath)

      val starPerfStatsFilepath = s"${perfFilepath}.star.perf.stats"
      val _starPerfStats = starPerfStats
      starPerfStatsLock.synchronized {
        starPerfStats = new ArrayBuffer[Tuple3[Long, Long, String]](partMaxSize) 
      }
      println(s"Persisting ${_starPerfStats.size} star perf items to ${starPerfStatsFilepath}")
      persist(_starPerfStats, starPerfStatsFilepath)
 
      val t = (System.nanoTime() - s)/1000000
      println(s"Persisting round completed in ${t} ms")

    }
  }

}
