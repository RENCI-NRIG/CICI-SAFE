package safe.runtime
    
import com.typesafe.config.ConfigFactory

class Config(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "saferuntime")
  def this() {
    this(ConfigFactory.load())
  }             
  val jvmCompilerPath: String        = config.getString("saferuntime.jvmCompilerPath")
}             
                
object Config {
  val config = new Config(ConfigFactory.load("application")) // default config context
}
