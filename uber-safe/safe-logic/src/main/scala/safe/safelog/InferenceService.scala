package safe.safelog

import scala.collection.mutable.{LinkedHashSet => OrderedSet}
import scala.collection.mutable.{Map => MutableMap}

trait InferenceService {

  private[safe] implicit var envContext: MutableMap[StrLit, EnvValue] = MutableMap.empty[StrLit, EnvValue]

  /**
   * solve: given a parsed data set, solve a sequence of queries
   */
  def solve(
      renderedSet: Map[Index, OrderedSet[Statement]]
    , queries: Seq[Statement]
    , isInteractive: Boolean
  ): Seq[Seq[Statement]]

  // localSlangContext is the set of statements made in a defguard and defcon other than the import statements from the context
  // useful when slang invokes slog
  def solveWithContext(
      queries: Seq[Statement]
    , isInteractive: Boolean
    , findAllSolutions: Boolean
  )(
      envContext: MutableMap[StrLit, EnvValue]
    , subcontexts: Seq[Subcontext]
  ): Seq[Seq[Statement]] // Note: import statements are part of envContext

  def solveWithValue(
      renderedSet: Map[Index, OrderedSet[Statement]]
    , queries: Seq[Statement]
    , isInteractive: Boolean
  ): Seq[Seq[Map[String, Seq[String]]]]
}

object InferenceService {
}
