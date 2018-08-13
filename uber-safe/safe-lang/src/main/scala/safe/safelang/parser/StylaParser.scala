package safe.safelang
package parser

import safe.safelog.{ParserException, Term, Validity}
import model.SlogSetTemplate
import prolog.io.{TermParser => StyParser}
import scala.collection.mutable.{LinkedHashSet => OrderedSet}

class StylaParserService {

  import safe.safelang.StyStmtHelper._
  import model.SlogSetHelper._

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
  def parseSlogSet(source: String, labelPredef: String, setData: String, signature: String, sigAlgorithm: String,
      speaker: String, subject: String, speaksForToken: String, validity: Validity): SlogSet = {
    val styparser = new StyParser
    val prolog = styparser.parseProg(source) 
    val spkr: Option[String] = if(speaker.isEmpty) None else Some(speaker)
    val subj: Option[String] = if(subject.isEmpty) None else Some(subject)
    val spkForT: Option[String] = if(speaksForToken.isEmpty) None else Some(speaksForToken)
    val slogset = buildSlogSet(indexStyStmts(prolog, styparser.vars), Some(labelPredef), Some(setData),
        Some(signature), Some(sigAlgorithm),spkr, subj, spkForT, Some(validity)) 
    slogset.setStatementSpeaker()
    slogset
  }
}

object StylaParserService {
  val parser = new StylaParserService()
  def getParser(): StylaParserService = parser
}
