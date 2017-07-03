package safe.safelang
package parser

import safe.safelog.{ParserException, Term, Validity}
import model.SlogSetTemplate
import prolog.io.{TermParser => StyParser}
import model.SlogSetHelper._
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

class StylaParserService {

  import safe.safelang.StyStmtHelper._
  /**
   * Parse SlogSetTemplate from a segment of slang source
   * @param source       string of a slang code snippet
   * @param labelInDef   label defined in defX, just in front of the first bracket
   */
  def parseSlogSetTemplate(source: String, labelInDef: Option[Term] = None): SlogSetTemplate = {
    println("\n\n=== [Safelang/StylaParser]  parse using styla ===")
    println(source)
    println("============================================\n")
    //println("[SafelogParser] Press enter to continue")
    //scala.io.StdIn.readLine()
    val styparser = new StyParser 
    val prolog = styparser.parseProg(source)
    SlogSetTemplate(indexStyStmts(prolog, styparser.vars), labelInDef) 
  }
 
  /**
   * Parse a SlogSet from a certificate segment
   * @param source       string of a slang code snippet
   * @param labelPredef  label extracted from the cred section of a certifiate
   * @param setData      raw data that slogset signature is derived from (the signedData section of a certificate)
   * @param signature    encoded signature of the certificate
   */
  def parseSlogSet(source: String, labelPredef: String, setData: String, signature: String, speaker: String, validity: Validity): SlogSet = {
    val styparser = new StyParser
    val prolog = styparser.parseProg(source) 
    buildSlogSet(indexStyStmts(prolog, styparser.vars, speaker), Some(labelPredef), Some(setData), Some(signature), Some(speaker), Some(validity)) 
  }
}

object StylaParserService {
  val parser = new StylaParserService()
  def getParser(): StylaParserService = parser
}
