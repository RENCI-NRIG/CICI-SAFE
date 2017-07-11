package safe.safelang

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

import safe.safelog.{Index, MutableCache, Statement, Subcontext, Term}

class SafelogContext() extends safe.safelog.Inference

class SafelogParserContext(  // why extending safe.safelog.Inference
    val self: String
  , val saysOperator: Boolean
  , val _statementCache: MutableCache[Index, OrderedSet[Statement]]
) extends safe.safelog.Inference with safe.safelog.ParserService with parser.SafelogParser {
  require(saysOperator == true)
}

object SafelogParserContext {
  def apply(self: String) = new SafelogParserContext(self, true, new MutableCache[Index, OrderedSet[Statement]]())
}
