package safe.safelang

import scala.util.control.Breaks
import scala.collection.mutable.{LinkedHashSet => OrderedSet, ListBuffer}

import org.apache.commons.validator.routines.UrlValidator
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime

import safe.safelog.{StrLit, Index, Statement, Structure, Validity, UnSafeException, NotImplementedException}
import model.{Subject, Principal, Identity, Id, Scid, SpeakerStmt, SubjectStmt}
import model.SlogSetHelper._
import safesets._
import prolog.terms.{Const => StyConstant, Fun => StyFun}

class SlogSet (
    val issuer: Option[String],                       // Hash of the issuer's public key; the speaker of a signed credential
    val subject: Option[String],                      // Subject of the set; used in conjunction with 
                                                      // speaksForToken, when it's different from the issuer
    val freshUntil: Option[DateTime],                 // Expiration time of this set
    var speakersFreshUntil: Option[DateTime],         // Expiration time of speaksFor authorization, 
    var issuerFreshUntil: Option[DateTime],           // Expiration time of the issuer's ID set
    var validatedSpeaker: Boolean,                    // Member of SlogSet
    var validated: Boolean,                           // True if the signature checks out
    val resetTime: Option[DateTime],                  // Time to refresh the set
    var queries: Seq[Statement],                      // Queries in the SlogSet
    var statements: Map[Index, OrderedSet[Statement]],// Statments in the SlogSet
    var links: Seq[String],                           // Links included in the SlogSet
    val speaksForToken: Option[String],               // Token of a set to endorse the validity of speaksFor
    val label: String,                                // Label of the SlogSet
    val signature: Option[String],                    // Signature of the slogset by the issuer 
    var setData: Option[String],                      // Set data signed over in a certificate
    var containingContexts: OrderedSet[String]        // References to inference contexts containing this set
  ) extends LazyLogging {

  /**
   * @DeveloperApi
   */
  override def toString(): String = {
    s"""|issuer: $issuer,
        |subject: $subject,
        |freshUtil: $freshUntil,
        |speakersFreshUtil: $speakersFreshUntil,
        |issuerFreshUntil: $issuerFreshUntil,
        |validatedSpeaker: $validatedSpeaker,
        |validated: $validated,
        |resetTime: $resetTime,
        |queries: $queries,
        |statements: $statements,
        |links: $links,
        |speaksForToken: $speaksForToken,
        |label: $label,
        |signature: $signature,
        |setData: $setData,
        |containingContexts: $containingContexts""".stripMargin
  }

  /**
   * Setting the speaker for each statement in the slogset.
   * The value of the speaker is determined by the value of the
   * the issuer and the value of the subject of the set.
   */
  def setStatementSpeaker(): Unit = {
    val spkr: String = if(subject.isDefined) {
      subject.get
    } else if(issuer.isDefined) {
      issuer.get 
    } else {
      ""
    }
   
    if(!spkr.isEmpty) { 
      logger.info(s"statements (without speaker) = ${statements}")
      logger.info(s"spkr = ${spkr}")
      statements = statements.keySet.map {
        case idx: Index =>
          val resultStatements: OrderedSet[Statement] = 
               statements.get(idx).getOrElse(OrderedSet.empty).map(stmt => stmt.addSpeaker(spkr))
          idx -> resultStatements
      }.toMap
      logger.info(s"statements (with speaker) = ${statements}")

      logger.info(s"queries (without speaker) = ${queries}")
      logger.info(s"spkr = ${spkr}")
      queries = queries.map(stmt => stmt.addSpeaker(spkr))
      logger.info(s"queries (with speaker) = ${queries}")
    }
  } 

  /**
   * Add token of a containing context
   */
  def addContainingContextToken(token: String): Unit = {
    containingContexts.synchronized {
      containingContexts += token
    }
  }

  def getContainingContexts(): OrderedSet[String] = {
    containingContexts.synchronized {
      containingContexts
    }
  }

  def expired(): Boolean = {
    val now = new DateTime()
    val res = (freshUntil.isDefined && freshUntil.get.isBefore(now)) || 
      (issuerFreshUntil.isDefined && issuerFreshUntil.get.isBefore(now))
    res
  }

  def setIssuerFreshUntil(expiration: DateTime): Unit = {
    issuerFreshUntil = Some(expiration)
  }

  /**
   * Merge the slogset with another. This happens when we merge an existing set with
   * a change (also a slogset) to the set referred by the same token.
   *  
   * We also merge sibling slogsets on fetch
   */
  def mergeSlogset(slogset: SlogSet): Unit = { 
    import model.SlogSetHelper._
    this.synchronized {
      val stmtsToAdd: Map[Index, OrderedSet[Statement]] = slogset.statements

      // Merge
      statements = (statements.keySet ++ stmtsToAdd.keySet).map {
        case i: Index => 
          //println(s"[SlogSet mergeSlogset] index =${i}")
          //statements.get(i).getOrElse(OrderedSet.empty[Statement]).foreach{ stmt => println(s"[SlogSet mergeSlogset] hashCode=${stmt.hashCode}; stmt=${stmt}") }
          //stmtsToAdd.get(i).getOrElse(OrderedSet.empty[Statement]).foreach{ stmt => println(s"[SlogSet mergeSlogset] hashCode=${stmt.hashCode}; stmt=${stmt}") }
          val newStmtSet: OrderedSet[Statement] = statements.get(i).getOrElse(OrderedSet.empty[Statement]) ++ 
            stmtsToAdd.get(i).getOrElse(OrderedSet.empty[Statement]) 
          i -> newStmtSet 
      }.toMap 
      links = (links ++ slogset.links).distinct

      // Process retraction
      val retractionIdx = StrLit("_retraction")
      val linksToRetract = ListBuffer[String]() 
      //println(s"[SlogSet mergeSlogset] statements.get(retractionIdx)=${statements.get(retractionIdx)}")
      if(statements.contains(retractionIdx)) {
        val retracting: OrderedSet[Statement] = statements(retractionIdx)
        for(r <- retracting) { 
          //println(s"[SlogSet mergeSlogset] r.getClass=${r.getClass}  r.isInstanceOf[StyRetraction]=${r.isInstanceOf[StyRetraction]}")
          if(r.isInstanceOf[StyRetraction]) { // process styla retraction
            val s = r.asInstanceOf[StyRetraction]    
            if(s.primaryIndex == StrLit("_link")) { // retract a link
              val l = getAttribute(Some(s), 1)
              if(l.isDefined) {
                linksToRetract += l.get
              } else {
                throw UnSafeException(s"link isn't found: ${s}")
              }
            }
            // Retract statements, including link statements
            val sidx = s.secondaryIndex
            //println(s"[SlogSet mergeSlogset retracting] idx=${idx}  statements.get(idx)=${statements.get(idx)}  r.hashCode=${r.hashCode}")
            retractStatement(statements, sidx, r)
            val pidx = s.primaryIndex 
            retractStatement(statements, pidx, r)
          }
        }
        links = (links diff linksToRetract)
        statements -= retractionIdx    // In the case of multi-version certificate, we don't want to remove the retraction stmts. 
      } 

    }
  }

  def retractStatement(statements: Map[Index, OrderedSet[Statement]], idx: Index, r: Statement): Unit = {
    //println(s"[SlogSet mergeSlogset retracting] idx=${idx}  statements.get(idx)=${statements.get(idx)}  r.hashCode=${r.hashCode}")
    if(statements.contains(idx)) {
      val stmtset: OrderedSet[Statement] = statements(idx)
      //stmtset.foreach(s => println(s"[SlogSet mergeSlogset retracting] s=${s}   s.hashCode=${s.hashCode}"))
      stmtset -= r // the retracted statement has the same hash code as r 
      //println(s"[SlogSet mergeSlogset retracting] stmtset=${stmtset}   statements=${statements}")
    }
  }

  def setValidated(): Unit = {
    if(!validated) { 
      this.synchronized {
        validated = true
        setData = None    // We won't touch setData any more once the set signature is validated
      }
    }
  }

  def checkMatchingSpeaker(): Boolean = {
    val now = new DateTime()
    if(!issuer.isDefined) { // A local slogset
      true
    }
    //else if(!issuerFreshUntil.isDefined || issuerFreshUntil.get.isBefore(now)) {
    else if(issuerFreshUntil.isDefined && issuerFreshUntil.get.isBefore(now)) {
      println(s"[SlogSet checkMatchingSpeaker] stale issuer: ${issuerFreshUntil}")
      false
    } else {
      var speakerUnmatched: Boolean = false 
      val outerLoop = new Breaks
      val innerLoop = new Breaks
      outerLoop.breakable {
        for(idx <- statements.keys) {
          val stmts: OrderedSet[Statement] = statements(idx)
          innerLoop.breakable {
            for(stmt <- stmts) {
              stmt match {
                case stystmt: StyStmt => // styla statement 
                  val hterm = stystmt.styterms.head
                  hterm match {
                    case f: StyFun if f.sym == ":" && f.args(0).isInstanceOf[StyConstant] => 
                      if(f.args(0).asInstanceOf[StyConstant].sym != issuer.get) {
                        println(s"[SlogSet checkMatchingSpeaker styla] speaker:${f.args(0)}  issuer.get:${issuer.get}")
                        speakerUnmatched = true 
                        innerLoop.break
                      }
                    case _ =>
                  }
          
                case _: Statement =>
                  stmt.terms.head match {
                    case Structure(pred, speaker +: other, _, _, _) => 
                      if(speaker.id.name != issuer.get) {
                        println(s"[SlogSet checkMatchingSpeaker] speaker:$speaker  issuer.get:${issuer.get}")
                        speakerUnmatched = true 
                        innerLoop.break
                      }
                    case _ =>  // other cases 
                  } 
              }
            }
          }
          if(speakerUnmatched) outerLoop.break
        }
      }
    
      // Set validatedSpeaker if it passes the checking
      if(!speakerUnmatched && !validatedSpeaker) setValidatedSpeaker()  
      validatedSpeaker
    }
  }
 

  private def setValidatedSpeaker(): Unit = {
    issuer.synchronized {
      if(!validatedSpeaker)
        validatedSpeaker = true
    } 
  }

  def isIDSet(): Boolean = {
    val principalStmts: Option[OrderedSet[Statement]] = statements.get(StrLit("_principal")) 
    // A slogset is an IDSet if it has a principal statement, and the label is an empty string
    if(principalStmts.isDefined && label == "") { true } else { false }
  }

  def toIDSet(): IDSet = {
    val principalPublicKey: Option[String] = getAttribute(
      getUniqueStatement(statements.get(StrLit("_principal"))), 1)  // self: principal("str")
    val principalSubject: Subject = principalPublicKey match {
      case None => throw UnSafeException(s"Principal subject expected in an ID Set, but nothing found")
      case Some(p) => Subject(p)
    }
    /**
     * Format of a preferredSetStore stmt in an ID set:
     *   self: preferredSetStore("storeaddr", "protocol", "storeID")
     * When protocol is HTTPS, storeID is the hash of the public key of the server certificate 
     * 
     * Example:
     * preferredSetStore("http://152.3.145.36:808", "HTTPS", "[keyhash]")
     * preferredSetStore("127.0.0.1:9042", "CASSANDRA_NATIVE", "")
     */
    var setstores = ListBuffer[SetStoreDesc]()
    val s: Option[OrderedSet[Statement]] = statements.get(StrLit("_preferredSetStore")) 
    val storestmts: OrderedSet[Statement] = if(s.isDefined) s.get else OrderedSet[Statement]()
    val urlValidator: UrlValidator = new UrlValidator()
    for(stmt <- storestmts) {
      val storeaddr: Option[String] = getAttribute(Some(stmt), 1)
      val protocol: Option[String]  = getAttribute(Some(stmt), 2)
      val storeId: Option[String]   = getAttribute(Some(stmt), 3)
      if(storeaddr.isDefined && protocol.isDefined && storeId.isDefined) {
        // Do simple checks on store address
        val url = storeaddr.get 
        val validatedaddr: String = if(urlValidator.isValid(url)) {
          //if(url.last == '/') { url } else { s"${url}/" }
          url
        } else {
          throw UnSafeException(s"Invalid url for set store (${url}) in stmt ${stmt}")
        }
        val store = SetStoreDesc(validatedaddr, protocol.get, storeId.get)
        setstores += store
      } else {
        throw UnSafeException(s"Problematic set store stmt: ${stmt} (${storeaddr}, ${protocol}, ${storeId})") 
      }
    } 
    IDSet(issuer, freshUntil, validated, resetTime, statements, signature, setData, principalSubject, setstores.toSeq)
  }

  def getStatementsAsString(self: String): String = { // Remove _speaker, _subject
    //println(s"[SlogSet getStatementsAsString] statements=${statements}")
    //val stmts: String = statements.values.flatten.toSeq.map{x => println(s"[SlogSet getStatementsAsString] x.getClass=${x.getClass}"); x.toStringCompact(self)}.mkString("\n      ")
    //println(s"[Safelang SlogSet  getStatementsAsString] ${stmts}")
    val allstmts = OrderedSet[Statement]()
    for(s <- statements.values) {
      allstmts ++= s
    }
    val stmts: String = allstmts.toSeq.map{x => x.toStringCompact(self)}.mkString("\n")
    stmts
  }

  /**
   * Compute the token for a SlogSet
   * We may pass in $Self for a local SlogSet
   */
  def computeToken(self: Option[Principal] = None): String = {
    var pid = ""
    if(issuer.isDefined) {
      //if(subject.isDefined) { // speaksForToken must point to a valid proof
      //  pid = subject.get
      //} else {
      //  pid = issuer.get
      //}
      pid = issuer.get  // the logic set is always under the issuer's namespace
    } else {
      if(self.isDefined) {
        pid = self.get.pid
      } 
    }
    println(s"[safelang/SlogSet.computeToken()] pid: ${pid}    label: ${label}")
    Identity.computeSetToken(pid, label)
  } 
  
  def computeToken(self: Principal): String = {
    computeToken(Some(self))
  }

  def prepareSignedData(self: Principal, cred: String, isEncrypted: Boolean, version: Int = 1, 
      encode: String = "slang", signatureAlgorithm: String = "SHA256withRSA"): String = {

    val selfID: String = if(issuer.isDefined) { issuer.get } 
                         else {  throw UnSafeException(s"No issuer for this slogset: ${issuer}")  }
    if(self != null) {
      if(issuer.get != self.pid) {
        throw UnSafeException(s"Signing principal does not match the speaker provided in the set: ${self}    ${issuer}")
      }
    }

    // process subject
    val subjectID = if(subject.isDefined) subject.get else ""

    val spksForT = if(speaksForToken.isDefined) speaksForToken.get else "" 

    val now = new DateTime()
    val notAfter = if(freshUntil.isDefined) freshUntil.get else now.plusYears(3) 
    val validity: String = s"${Validity.format.print(now)}, ${Validity.format.print(notAfter)}, ${Validity.periodFormat.print(Validity.defaultPeriod)}"

    // Data to sign
    s"""|${selfID}
        |${subjectID}
        |${spksForT}
        |${validity}
        |${signatureAlgorithm}
        |${cred}""".stripMargin
  }

  /**
   * Standard format of posted certificate:
   *    SetToken
   *    Signature
   *    SpeakerID
   *    SubjectID
   *    Validity
   *    Signature Algorithm
   *    SetLabel
   *    [Empty line]
   *    Logical Statements 
   */
  private def signAndGetData(self: Principal, cred: String, isEncrypted: Boolean = false, 
      signatureAlgorithm: String = "SHA256withRSA"): Tuple2[String, String] = {
    val dataToSign = prepareSignedData(self, cred, isEncrypted)
    //println(s"[slang slogset] Signing")
    //println(s"[slang slogset] dataToSign=${dataToSign}")
    //println(s"[slang slogset] self.subject.publicKey=${self.subject.publicKey}")
    if(self != null) {
      val s = System.nanoTime
      val h: Array[Byte] = Identity.hash(dataToSign.getBytes())  // sign data hash
      val signatureBytes  = self.sign(h, signatureAlgorithm) 
      val t = (System.nanoTime - s) / 1000

      val signatureEncoded = Identity.base64EncodeURLSafe(signatureBytes)

      val data_length = dataToSign.length
      val sig_length = signatureEncoded.length
      slangPerfCollector.addSetSignTime(t.toString, s"$label ${data_length} ${sig_length}") // collect set sign time 

      (signatureEncoded, dataToSign)

    } else { // empty signature
      ("", dataToSign)
    }
  }

  private def encodeToSlang(self: Principal): Tuple2[String, String] = {
    //println(s"[encodeToSlang] self: ${self}")
    val setToken = if(self == null) computeToken(None) else computeToken(self)
    val stmts: String = if(self != null) getStatementsAsString(self.pid) else getStatementsAsString(issuer.get) // issuer must exist

    val cred: String = s"""|${label}
                           |
                           |${stmts}""".stripMargin

    val cert: String = {
      val (signatureEncoded, signedData) = signAndGetData(self, cred)
      val certStmt = s"""|${setToken}
                         |${signatureEncoded}
                         |${signedData}""".stripMargin
      certStmt
    }
    //println(s"[encodeToSlang] setToken.toString: ${setToken.toString}   cert: ${cert}")
    (setToken.toString, cert)
  }

  /**  
   * A principal signs the slogset and encodes it into a string according to some format
   * Make sure that the principal speaker is issuer
   * @return a tuple of a set token and the encoded cert
   */
  def signAndEncode(self: Principal, format: String = "slang"): Tuple2[String, String] = format.toLowerCase match {
    case "safe" | "slang" => 
      encodeToSlang(self)
    case "x509" => 
      throw NotImplementedException(s"X509 certificate format not yet implemented; use safe as an alternative")
  }
  
  def verify(speaker: Subject, signatureAlgorithm: String = "SHA256withRSA"): Boolean = {
    val verifiable: Boolean = this.synchronized { signature.isDefined && !signature.get.isEmpty && setData.isDefined }
    if(!verifiable) {
      true //throw UnSafeException(s"Signature and setData must exist to verify a slog set: \n${this}")
    } else {  // Verify
      val s = System.nanoTime

      val sig: java.security.Signature = java.security.Signature.getInstance(signatureAlgorithm)
      //println(s"[slang slogset] verifying")
      //println(s"[slang slogset] speaker.publicKey=${speaker.publicKey}")
      //println(s"[slang slogset] setData=${setData.get}")
      sig.initVerify(speaker.publicKey)
      val h: Array[Byte] = Identity.hash(setData.get.getBytes()) 
      sig.update(h)
      val res = sig.verify(Identity.base64Decode(signature.get))

      val t = (System.nanoTime -s) / 1000
      slangPerfCollector.addSetVerifyTime(t.toString, s"${label} ${setData.get.length}") // collect set verification time

      if(!res) {
        println(s"[slang slogset] speaker.id.toString=${speaker.id.toString}")
        println(s"[slang slogset] this=${this}") //setData=${setData.get}")
      }
      res
    }
  }

  /**
   * Verify the slogset first. If it's a valid slogset, set the field validated 
   * and set the setData to None.
   *
   * We set the setData to None, because we don't need setData any more if the 
   * set passes the validation. The old setData object can be then garbage
   * collected. setData = None has been moved to setValidated().
   */
  def verifyAndSetValidated(speaker: Subject, signatureAlgorithm: String = "SHA256withRSA"): Boolean = {
    val validSet: Boolean = verify(speaker, signatureAlgorithm)
    if(validSet) {
      if(!validated) {
        setValidated()
      }
    }
    validSet
  }

} 

object SlogSet {
  def apply(issuer: Option[String], subject: Option[String], freshUntil: Option[DateTime],
      speakersFreshUntil: Option[DateTime], issuerFreshUntil: Option[DateTime], 
      validatedSpeaker: Boolean, validated: Boolean, resetTime: Option[DateTime], 
      queries: Seq[Statement], statements: Map[Index, OrderedSet[Statement]], links: Seq[String], 
      speaksForToken: Option[String], label: String, signature: Option[String], 
      setData: Option[String]): SlogSet = {

    val slogset = new SlogSet(issuer, subject, freshUntil, speakersFreshUntil, issuerFreshUntil,
      validatedSpeaker, validated, resetTime, queries, statements, links, speaksForToken, label, 
      signature, setData, OrderedSet[String]())
    slogset
  }

  /**
   * Build a slog set from a group of queries
   * build an inferred query set
   * for local use only
   */
  def apply(queries: Seq[Statement], label: String): SlogSet = {
    val slogset = new SlogSet(None, None, None, None, None, true, true, None, queries, 
      Map[Index, OrderedSet[Statement]](), Seq[String](), None, label, None, None, OrderedSet[String]())
    slogset
  }

  /**
   * Make an empty slog set
   */
  def apply(issuer: String, label: String): SlogSet = {
    val slogset = new SlogSet(Some(issuer), None, None, None, None, true, true, None, Seq[Statement](), 
      Map[Index, OrderedSet[Statement]](), Seq[String](), None, label, None, None, OrderedSet[String]())
    slogset
  }
}


class IDSet(
    issuer: Option[String],
    subject: Option[String] = None,
    freshUntil: Option[DateTime],   
    speakersFreshUntil: Option[DateTime] = None, 
    issuerFreshUntil: Option[DateTime] = None,
    validatedSpeaker: Boolean = false,
    validated: Boolean,
    resetTime: Option[DateTime],
    queries: Seq[Statement] = Nil,
    statements: Map[Index, OrderedSet[Statement]],
    links: Seq[String] = Nil,
    speaksForToken: Option[String] = None,
    label: String = "",
    signature: Option[String],
    setData: Option[String], 
    containingContexts: OrderedSet[String] = OrderedSet[String](), 
    val principalSubject: Subject,
    val preferredSetStores: Seq[SetStoreDesc]
  ) extends SlogSet(issuer, subject, freshUntil, speakersFreshUntil, issuerFreshUntil, validatedSpeaker, 
      validated, resetTime, queries, statements, links, speaksForToken, label, signature, setData, containingContexts) {

  def getPrincipalSubject(): Subject = principalSubject 
  def getPreferredSetStores(): Seq[SetStoreDesc] = preferredSetStores
  override def toString(): String = {
    val sb = new StringBuilder()
    sb.append(super.toString)
    sb.append("\n")
    sb.append(s"principalSubject: ${principalSubject}")
    var i: Int = 0
    for(ss <- preferredSetStores) {
      sb.append("\n")
      sb.append(s"preferredSetStore${i}: ${preferredSetStores(i)}")
    }
    sb.toString
  }
}

object IDSet {
  def apply(issuer: Option[String], freshUntil: Option[DateTime], validated: Boolean, 
      resetTime: Option[DateTime], statements: Map[Index, OrderedSet[Statement]], 
      signature: Option[String], setData: Option[String], principalSubject: Subject,
      preferredSetStores: Seq[SetStoreDesc]): IDSet = {

    val idset = new IDSet(issuer, None, freshUntil, None, None, false, validated, 
        resetTime, Nil, statements, Nil, None, "", signature, setData, OrderedSet[String](), 
        principalSubject, preferredSetStores)
    idset
  }
}

/** This might be useful */
object SlogSetState extends Enumeration {
  type SlogSetState = Value
  val Active, Revoked, Suspended = Value
}
