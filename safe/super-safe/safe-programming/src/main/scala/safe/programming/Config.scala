package safe.programming

import com.typesafe.config.{ConfigException, ConfigFactory}
import scala.util.Try

import scala.concurrent.duration._

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "safeprogramming")
  def this() {
    this(ConfigFactory.load())
  }
  val self: String                   = Try(config.getString("safelang.self")).getOrElse("Self")
  val saysOperator: Boolean          = Try(config.getBoolean("safelang.saysOperator")).getOrElse(false)
  val maxDepth: Int                  = Try(config.getInt("safelang.maxDepth")).getOrElse(111)
  val keyPairDir: String             = Try(config.getString("safe.multiprincipal.keyPairDir")).getOrElse("")
  val safeSetsDir: String            = Try(config.getString("safelang.safeSetsDir")).getOrElse("")
  val localSafeSets: Boolean         = Try(config.getBoolean("safelang.localSafeSets")).getOrElse(false)
  val autoPerfStatsPeriod: Int       = Try(config.getInt("safe.programming.autoPerfStatsPeriodInOps")).getOrElse(1000)
  val akkaTimeout: FiniteDuration    = Try(
    FiniteDuration(config.getDuration("safelang.akkaTimeout", MILLISECONDS), MILLISECONDS)
  ).getOrElse(FiniteDuration(25, SECONDS))
}

object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
