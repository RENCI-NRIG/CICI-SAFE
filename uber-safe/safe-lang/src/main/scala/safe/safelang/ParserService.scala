package safe.safelang

import scala.collection.mutable.{Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import safe.safelog.{Index, MutableCache, Statement, ParserException}

trait ParserService extends safe.safelog.ParserService {
  private[safe] def parseCertificate(source: String): SlogSet
  //private[safe] def parseCertificate(source: java.io.Reader): SlogSet
  private[safe] def parseFileAsCertificate(fileName: String): SlogSet
}

class Parser(
    val self: String
  , val saysOperator: Boolean
) extends ParserService with parser.ParserImpl

object Parser {
  def apply() = new Parser(Config.config.self, Config.config.saysOperator)
  def apply(self: String) = new Parser(self, true)
}
