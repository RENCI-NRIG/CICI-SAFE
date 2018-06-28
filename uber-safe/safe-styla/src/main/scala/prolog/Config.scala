package prolog

import com.typesafe.config.{ConfigException, ConfigFactory}
import scala.util.Try

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "safestyla")
  def this() {
    this(ConfigFactory.load())
  }
  val indexing: String               = Try(config.getString("safestyla.indexing")).getOrElse("primary")
  val stylibOn: Boolean              = Try(config.getBoolean("safestyla.libraryOn")).getOrElse(false)
  val maxDepth: Int                  = Try(config.getInt("safestyla.maxDepth")).getOrElse(10000)
}

object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
