package safe.safelog

import scala.collection.mutable.{Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

trait ParserService {

  private[safelog] val saysOperator: Boolean
  private[safelog] val self: String          
  // _statementCache holds all the statements after parsing
  private[safelog] var _statementCache = new MutableCache[Index, OrderedSet[Statement]]()


  // A cache of set of statements indexed by statement head functor + arity + principal
  // No support for concurrent operations at safelog level.
  // Used in repl and parser
  private[safe] def parseCmdLine(source: String): Tuple2[Option[MutableCache[Index, OrderedSet[Statement]]], Symbol] // for REPL
  private[safe] def parseFileFresh(speaker: String, fileName: String): Map[Index, OrderedSet[Statement]]

  /**
   * parse: parses a list of logic sentences to a list of statements
   * source is either a string of statements or a file
   */
  def parse(source: String): Map[Index, OrderedSet[Statement]]
  def parse(source: java.io.Reader): Map[Index, OrderedSet[Statement]]
  def parseFile(fileName: String): Map[Index, OrderedSet[Statement]]

  // def compile(source: String): String // source: fileName, Output: compiled file name
  // def execute(source: String): Seq[Seq[Statement]] // source: compiled file name

  def parseAsSegments(source: String): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]]
  def parseAsSegments(source: java.io.Reader): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]]
  def parseFileAsSegments(fileName: String): Tuple4[Map[Index, OrderedSet[Statement]], Seq[Statement], Seq[Statement], Seq[Statement]]
}

class Parser(
    val self: String
  , val saysOperator: Boolean
) extends ParserService with parser.ParserImpl

object Parser {
  def apply() = new Parser(Config.config.self, Config.config.saysOperator)
  def apply(self: String) = new Parser(self, true)
}
