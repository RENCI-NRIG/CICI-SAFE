package prolog.terms
import prolog.interp.Prog
import prolog.io.IO
import prolog.Config
import scala.collection.mutable.LinkedHashSet
import com.typesafe.scalalogging.LazyLogging

/**
 * A skeleton retraction marker 
 */
class RetractionTerm(args: Array[Term]) extends Fun("_retraction", args) with LazyLogging {
  override def safeCopy(): Fun = {
    new RetractionTerm(null)
  }
}
