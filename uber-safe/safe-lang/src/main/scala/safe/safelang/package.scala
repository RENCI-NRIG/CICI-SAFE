package safe

import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import akka.util.Timeout
  
import safe.runtime.{Lang, JVMContext, JVMInterpreter}
import safe.safelog.{Index, Statement}
//import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

package object safelang {
  // These values should come from config
  val StringEncoding: String = "UTF-8"

  if(java.security.Security.getProvider(org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME) == null) {
    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
  }

  val safelogContext: SafelogContext = new SafelogContext() // for inference but not parsing
  private[safelang] val jvmContext: Map[Lang, JVMInterpreter] = JVMContext.interpreters

  private[safelang] implicit val timeout = Timeout(Config.config.akkaTimeout)
  //private[safelang] implicit val ec      = scala.concurrent.ExecutionContext.Implicits.global // customize the execution context if required
 
  //type SafeTable[K, V] = ConcurrentLinkedHashMap[K, V]
  //type SafeTableBuilder[K, V] = ConcurrentLinkedHashMap.Builder[K, V]
  val slangPerfCollector = new SlangPerfCollector()
  //val REQ_ENV_DELIMITER = "|"
  val REQ_ENV_DELIMITER = ":"

  // Global constants for labeling queries/invokable guards
  val DEF_GUARD  = 0
  val DEF_POST   = 1
  val DEF_FETCH  = 2
  val DEF_FUN    = 3

  val emptyReqEnvs: Map[String, Option[String]] = Map(
    "Speaker" -> None, "Subject" -> None, "Object" -> None,
    "BearerRef" -> None, "Principal" -> None)
}
