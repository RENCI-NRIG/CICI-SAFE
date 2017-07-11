package safe.safelog

import scala.collection.mutable.{Set => MutableSet}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

trait SafelogImpl {
  self: InferenceService with ParserService =>

  //def solve(source: String): Seq[Seq[Statement]]
  //def solve(source: java.io.Reader): Seq[Seq[Statement]]

  //def solveWithValue(source: String): Seq[Seq[Map[String, Seq[String]]]]
  //def solveWithValue(source: java.io.Reader): Seq[Seq[Map[String, Seq[String]]]]
  
  def solve(source: String): Seq[Seq[Statement]] = {
    val renderedSet = parseAsSegments(source)
    solve(renderedSet._1, renderedSet._2, false)
  }
  def solve(source: java.io.Reader): Seq[Seq[Statement]] = {
    val renderedSet = parseAsSegments(source)
    solve(renderedSet._1, renderedSet._2, false)
  }
  def solveWithValue(source: String): Seq[Seq[Map[String, Seq[String]]]] = {
    val renderedSet = parseAsSegments(source)
    solveWithValue(renderedSet._1, renderedSet._2, false)
  }
  def solveWithValue(source: java.io.Reader): Seq[Seq[Map[String, Seq[String]]]] = {
    val renderedSet = parseAsSegments(source)
    solveWithValue(renderedSet._1, renderedSet._2, false)
  }
}

trait SafelogService extends InferenceService 
  with ParserService 
  with SafelogImpl 
  with InferenceImpl 
  with parser.ParserImpl

class Safelog(
    val self: String
  , val saysOperator: Boolean
  , val _statementCache: MutableCache[Index, OrderedSet[Statement]]
) extends SafelogService

object Safelog {
  def apply() = new Safelog(Config.config.self, Config.config.saysOperator, new MutableCache[Index, OrderedSet[Statement]]())
}
