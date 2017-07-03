package safe.safelang
package parser

import safe.safelog.{ParserException, Term}
import model.SlogSetTemplate
import model.SlogSetHelper._

import scala.collection.mutable.{LinkedHashSet => OrderedSet}
 
trait SafelogParser extends safe.safelog.parser.ParserImpl {
  this: safe.safelog.ParserService =>

  /**
   * Parse SlogSetTemplate from a segment of slang source
   * @param source       string of a slang code snippet
   * @param labelInDef   label defined in defX, just in front of the first bracket
   */
  def parseSlogSetTemplate(source: String, labelInDef: Option[Term] = None): SlogSetTemplate = {
    println("\n\n=== [Safelang/SafelogParser]  parse slog ===")
    println(source)
    println("============================================\n")
    //println("[SafelogParser] Press enter to continue")
    //scala.io.StdIn.readLine()
    super.parseSlog(source) match {
      case Success(_result, _) =>
        SlogSetTemplate(_result.toMap, labelInDef) 
      case failure: NoSuccess => throw ParserException(s"${failure.msg}")
    }
  }
 
  /**
   * Parse a SlogSet from a certificate segment
   * @param source       string of a slang code snippet
   * @param labelPredef  label extracted from the cred section of a certifiate
   * @param setData      raw data that slogset signature is derived from (the signedData section of a certificate)
   * @param signature    encoded signature of the certificate
   */
  def parseSlogSet(source: String, labelPredef: String, setData: String, signature: String): SlogSet = {
    super.parseSlog(source) match {
      case Success(_result, _) =>
        buildSlogSet(_result.toMap, Some(labelPredef), Some(setData), Some(signature)) 
      case failure: NoSuccess => throw ParserException(s"${failure.msg}")
    }
  }
}
