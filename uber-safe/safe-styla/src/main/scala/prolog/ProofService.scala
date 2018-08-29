package prolog

import prolog.interp.Unfolder
import prolog.terms.{Var, Term}

import scala.collection.mutable.ListBuffer

/**
 * This object provides a service to generate logical proofs and
 * to debug logical inference. It also provides APIs for pretty
 * printing of proofs and debugging info. 
 */
object ProofService {

  // For proof print
  // Inference is the inverse of the proof (deduction)
  val proofHead =       "\n     ========================================== SAFE PROOF ========================================"
  val proofEnd  =         "     ========================================= END OF PROOF =======================================\n"
  val inferenceHead =   "\n     ======================================== SAFE INFERENCE ======================================"
  val inferenceEnd  =     "     ======================================= END OF INFERENCE =====================================\n"
  val linePrefixOfFrame = "    |        "
  val lineSuffixOfFrame =                                                                                            "        |"
  val inferStepHead     =               "          || "
  val inferArrowPart0   =              "         \\||/"
  val inferArrowPart1   =              "          \\/"
  val commonOrigin      =               "          {}" 

  val lineLen = proofHead.length
  assert(proofHead.length == proofEnd.length, 
           s"proofHead (of length ${proofHead.length}) and proofEnd (of length ${proofEnd.length}) must be of equal length")
  val goalCharsPerLine = lineLen - linePrefixOfFrame.length - lineSuffixOfFrame.length

  /**
   * Format a proof line according to the proof template (a frame)"
   * @param s    input proof line
   * @return     a formatted line with frame prefix, frame suffix, and needed padding
   */
  def formatProofln(s: String): String = {
    assert(s.length <= goalCharsPerLine,
             s"String too long to fit into a line: ${s}   length: ${s.length}    line limit: ${goalCharsPerLine}") 
    println(s"s.length: ${s.length}   s: ${s}")
    val padding = mintUniformString(' ', goalCharsPerLine - s.length)
    println(s"padding: ${padding}")
    s"${linePrefixOfFrame}${s}${padding}${lineSuffixOfFrame}\n"
  }

  def formatProofBlock(additionalPrefix: String, content: String): String = {
    assert(additionalPrefix.length < goalCharsPerLine, s"Asked prefix too long: ${additionalPrefix.length}, maximum allowed length: ${goalCharsPerLine}")
    val numCharsPerLine = goalCharsPerLine - additionalPrefix.length
    println(s"numCharsPerLine: ${numCharsPerLine}")
    val numLines = content.length / numCharsPerLine + 1
    println(s"numLines: ${numLines}")
    val sb = new StringBuilder()
    var i = 0
    while (i < numLines-1) {
      println(s"i: ${i}")
      val line = content.substring(i*numCharsPerLine, (i+1)*numCharsPerLine)
      println(s"line: ${line}")
      val formatted = formatProofln(s"${additionalPrefix}${line}")
      println(s"formatted: ${formatted}")
      sb.append( formatProofln(s"${additionalPrefix}${line}") )  
      i = i + 1
    }
    // i == numLines - 1

    println(s"end i: ${i}")
    sb.append( formatProofln(s"${additionalPrefix}${content.substring(i*numCharsPerLine, content.length)}") )
    sb.toString
  }

  def formatGoalsBlock(goals: String): String = {
    val sb = new StringBuilder()
    sb.append( formatProofln("") )  // add vspace
    sb.append( formatProofBlock("", goals) )
    sb.toString
  }

  def formatInferStepBlock(step: String): String = {
    formatProofBlock(inferStepHead, step)
  }

  def formatInferArrow(): String = {
    val sb = new StringBuilder()
    sb.append( formatProofBlock(inferArrowPart0, "") )
    sb.append( formatProofBlock(inferArrowPart1, "") )
    sb.toString
  }

  def mintUniformString(c: Char, len: Int): String = {
    println(s"mint a string c: ${c}   len: ${len}")
    val sb = new StringBuilder()
    var i = 0
    while(i < len) {
      sb.append(c)
      i = i + 1
    }
    sb.toString
  }


  /**
   * Format a logical proof based on a listed stack (with the item on top of the stack shows up
   * as the first entry on the list) of unfolders and a listed stack of variable substitutions.
   * The tWo input lists are snapshots taken from a Styla inference engine when it finds a solution
   * to a query.  
   *
   * @return a formatted SAFE proof
   */

  def formatLogicalProof(proofSteps: List[Unfolder], substitutions: List[Var]): String = {
    val sb = new StringBuilder()
    sb.append( proofHead + "\n" )
    sb.append( formatProofln("") ) // add vspace

    // A proof starts from a common origin, with zero subgoals
    sb.append( formatGoalsBlock(commonOrigin) )

    var i = 0
    var substIndex = 0
    while(i >= 0) {
      val step: Unfolder = proofSteps(i)
      val goals = step.goal
      if(step.getOldtop != 0) {
        var j = substIndex
        val substsOfStep = ListBuffer[String]()
        while(j < step.getOldtop) {
          assert(j < substitutions.length, s"Invalid index: substIndex=${substIndex}  substsLen=${substitutions.length}")
          val s = substitutions(substitutions.length - j - 1)
          substsOfStep += s"${s}=>${s.name}"
          j = j + 1
        }
        if(substsOfStep.length > 0) {
          val substsAsString = substsOfStep.mkString("; ")
          substIndex = step.getOldtop  // advance substIndex
          sb.append( formatInferStepBlock("") )
          sb.append( formatInferStepBlock(substsAsString) )
        }
        sb.append( formatInferArrow )
      }

      sb.append( formatGoalsBlock(goals.toString) )
      sb.append( formatInferStepBlock("") )
      sb.append( formatInferStepBlock(step.previousClause.toString) ) 
      i = i - 1
    }
    // Finishing the last step
    sb.append( formatInferStepBlock("") )
    sb.append( formatInferArrow ) 
    sb.append( formatGoalsBlock(commonOrigin) )

    sb.append( formatProofln("") ) // add vspace
    sb.append(proofEnd)
    sb.toString
  }


  def formatStatement(atoms: List[Term]): String = {
    assert(atoms.length > 0, s"Empty list needs no formatting: ${atoms}")
    if(atoms.length == 1) {
      return atoms(0).toString + "."
    }
    val sb = new StringBuilder()
    sb.append(atoms(0).toString)
    sb.append(" :- ")
    val body = new ListBuffer[Term]()
    var i = 1
    while(i < atoms.length) {
      body += atoms(i)
      i = i + 1
    }
    sb.append(body.mkString(", "))
    sb.append(".") 
    return sb.toString
  }

  def formatLogicalInference(proofSteps: List[Unfolder], substitutions: List[Var]): String = {
    val sb = new StringBuilder()
    sb.append( proofHead + "\n" )
    sb.append( formatProofln("") ) // add vspace

    var i = proofSteps.length -1
    var substIndex = 0
    while(i >= 0) {
      val step: Unfolder = proofSteps(i)
      val goals = step.goal
      if(step.getOldtop != 0) {
        var j = substIndex
        val substsOfStep = ListBuffer[String]()
        while(j < step.getOldtop) {
          assert(j < substitutions.length, s"Invalid index: substIndex=${substIndex}  substsLen=${substitutions.length}")
          val s = substitutions(substitutions.length - j - 1)
          substsOfStep += s"${s}=>${s.name}"
          j = j + 1
        }
        if(substsOfStep.length > 0) {
          val substsAsString = substsOfStep.mkString(";    ")
          substIndex = step.getOldtop  // advance substIndex
          sb.append( formatInferStepBlock("") )
          sb.append( formatInferStepBlock(substsAsString) )
        }
        sb.append( formatInferArrow )
      }

      sb.append( formatGoalsBlock(goals.mkString(";    ")) )
      sb.append( formatProofln("") ) // add vspace
      sb.append( formatInferStepBlock("") )
      sb.append( formatInferStepBlock( formatStatement(step.previousClause) ) ) 
      i = i - 1
    }
    // Finishing the last step
    sb.append( formatInferStepBlock("") )
    sb.append( formatInferArrow ) 
    sb.append( formatGoalsBlock(commonOrigin) )

    sb.append( formatProofln("") ) // add vspace
    sb.append(proofEnd)
    sb.toString
  }

}
