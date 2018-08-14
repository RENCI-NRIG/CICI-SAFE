package safe.safelang

import safesets.SetStoreDesc
import com.typesafe.config.{ConfigException, ConfigFactory}
import scala.util.Try

import scala.concurrent.duration._
import org.joda.time.Period

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "safelang")
  def this() {
    this(ConfigFactory.load())
  }
  val self: String                   = Try(config.getString("safelang.self")).getOrElse("Self")
  val saysOperator: Boolean          = Try(config.getBoolean("safelang.saysOperator")).getOrElse(false)
  val maxDepth: Int                  = Try(config.getInt("safelang.maxDepth")).getOrElse(Int.MaxValue)
  val keyPairDir: String             = Try(config.getString("safe.multiprincipal.keyPairDir")).getOrElse("")
  val accessKeyDir: String           = Try(config.getString("safe.multiprincipal.accessKeyDir")).getOrElse("")
  val safeSetsDir: String            = Try(config.getString("safelang.safeSetsDir")).getOrElse("")
  val localSafeSets: Boolean         = Try(config.getBoolean("safelang.localSafeSets")).getOrElse(false)
  val storeURI: String               = Try(config.getString("safe.safesets.storeURI")).getOrElse("")
  val slangPerfFile: String          = Try(config.getString("safelang.slangPerfFile")).getOrElse("slangPerfDefaultFile")
  val maxSubcontextSize: Int         = Try(config.getInt("safelang.maxSubcontextSize")).getOrElse(Int.MaxValue)
  val logicEngine: String            = Try(config.getString("safelang.logicEngine")).getOrElse("slog") 
  val proofsOn: Boolean              = Try(config.getBoolean("safelang.proofsOn")).getOrElse(false)
  val signatureAlgorithm: String     = Try(config.getString("safelang.signatureAlgorithm")).getOrElse("SHA256withRSA")
  val maxEnvcontextsOnServer: Int    = Try(config.getInt("safelang.maxEnvcontextsOnServer")).getOrElse(Int.MaxValue)
  // Period(hours, minutes, seconds, millis)
  val minContextRefreshTime: Period  = new Period(0, 0, 0, Try(config.getInt("safelang.minContextRefreshTimeInMillis")).getOrElse(10000)) 

  val selfCertifyingSetToken: Boolean= Try(config.getBoolean("safelang.selfCertifyingSetToken")).getOrElse(false)
  val akkaTimeout: FiniteDuration    = Try(
    FiniteDuration(config.getDuration("safelang.akkaTimeout", MILLISECONDS), MILLISECONDS)
  ).getOrElse(FiniteDuration(25, SECONDS))
  val perfCollectorOn: Boolean       = Try(config.getBoolean("safelang.perfCollectorOn")).getOrElse(false)
  val sslOn: Boolean                 = Try(config.getBoolean("safelang.ssl.sslOn")).getOrElse(false)
  val sslKeyStore: String            = Try(config.getString("safelang.ssl.keystorepath")).getOrElse("")
  val keystorePasswd: String         = Try(config.getString("safelang.ssl.passwd")).getOrElse("qiangcao")
  val metastore: SetStoreDesc        = SetStoreDesc(Try(config.getString("safelang.metastore.url")).getOrElse(""),
                                                    Try(config.getString("safelang.metastore.protocol")).getOrElse("http"),
                                                    Try(config.getString("safelang.metastore.serverID")).getOrElse("")
                                                   )
  val ldapUsername: String           = Try(config.getString("ldap.username")).getOrElse("")
  val ldapPassword: String           = Try(config.getString("ldap.password")).getOrElse("")
  val importGuardSlangPath: String   = Try(config.getString("safelang.importGuardSlangPath")).getOrElse("")
  val importGuardName: String        = Try(config.getString("safelang.importGuardName")).getOrElse("")
  val speaksForGuardSlangPath: String= Try(config.getString("safelang.speaksForGuardSlangPath")).getOrElse("") 
  val speaksForGuardName: String     = Try(config.getString("safelang.speaksForGuardName")).getOrElse("")
}

object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
