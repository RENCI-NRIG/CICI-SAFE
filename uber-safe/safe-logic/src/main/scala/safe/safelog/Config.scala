package safe.safelog

import com.typesafe.config.{ConfigException, ConfigFactory}
import scala.util.Try

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "safelog")
  def this() {
    this(ConfigFactory.load())
  }
  val self: String                   = Try(config.getString("safelog.self")).getOrElse("Self")
  val saysOperator: Boolean          = Try(config.getBoolean("safelog.saysOperator")).getOrElse(false)
  val intraQueryParallelism: Boolean = Try(config.getBoolean("safelog.intraQueryParallelism")).getOrElse(false)
  val initialContextSize: Int        = Try(config.getInt("safelog.initialContextSize")).getOrElse(16)
  val maxDepth: Int                  = Try(config.getInt("safelog.maxDepth")).getOrElse(1111111)
  val reserved: Map[StrLit, Int]     = Map(   // Map of reserved symbols with their arity
      StrLit("version")            ->  1
    , StrLit("speaker")            ->  3
    , StrLit("subject")            ->  2
    , StrLit("principal")          ->  1
    , StrLit("signatureAlgorithm") ->  1
    , StrLit("speaksFor")          ->  2
    , StrLit("speaksForOn")        ->  3
    , StrLit("name")               ->  1
    , StrLit("label")              ->  1
    , StrLit("link")               ->  1
    , StrLit("validity")           ->  3
    , StrLit("tag")                ->  1
    //, StrLit("import")             ->  1
    //, StrLit("importAll")          ->  1
    , StrLit("recipient")          ->  4
    , StrLit("encoding")           ->  1
    , StrLit("preferredSetStore")  ->  3
  )
  val metadata: Set[StrLit] = Set(  // stmts with a metadata head will be filtered out when a Slogset is instantiated
      StrLit("version")
    , StrLit("speaker")
    , StrLit("subject")
    , StrLit("signatureAlgorithm")
    , StrLit("name")
    , StrLit("label")
    , StrLit("validity")
    //, StrLit("import")
    //, StrLit("importAll")
    , StrLit("recipient")
    , StrLit("encoding")
  )
}

object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
