package safe.safelang
   
import safe.safelog.{Term, SetId, Index, Statement, EnvValue, StrLit}
import model._
import setcache.SetCache

import safe.cache.SafeTable
import akka.actor.ActorSystem
import scala.collection.mutable.{Map => MutableMap}
    
trait InferenceService extends safe.safelog.InferenceService {
  /**
   * solveSlang: given a parsed data set ("_object"), solve a sequence of queries
   */     
  def solveSlang(
      queries: Seq[Statement]
    , isInteractive: Boolean
  ): Seq[Seq[Statement]]

  /**
   * Each Safelang has its own envContext
   * Bind envContext to the inference engine
   */ 
  def bindEnvContext(envcnt: MutableMap[StrLit, EnvValue]): Unit = {
    envContext = envcnt
  }
  
  def getEnvContext(): MutableMap[StrLit, EnvValue] = envContext

  //private[safe] implicit val localSetTable: SafeTable[SetId, SlogSet]  

  private[safe] val setCache: SetCache 

  private[safe] val contextCache: ContextCache 

  private[safe] val safelangId: Int
}

object InferenceService {
}
