package prolog

import com.typesafe.config.{ConfigException, ConfigFactory}
import scala.util.Try

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "safestyla")
  def this() {
    this(ConfigFactory.load())
  }
  val indexing: String               = Try(config.getString("safestyla.indexing")).getOrElse("primary")
  val stylibOn: Boolean               = Try(config.getBoolean("safestyla.libraryOn")).getOrElse(false)
}

object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
