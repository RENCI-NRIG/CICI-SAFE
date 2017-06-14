package safe.safelang
package model

import safe.safelog.{Assertion, Constant, Statement, Structure, UnSafeException, StrLit}

case class SpeaksFor(speaker: Id, subject: Id, delegatable: Boolean)

/**
 * Delegatable is mis-interpreted from a wrong source 
 * The input stmts is: speaksFor($Self, $Speaker, $Self)
 */

object SpeaksFor {
  def apply(statement: Statement): SpeaksFor = statement match {
    case Assertion(Structure(StrLit("speaksFor"), Constant(speaker, _, _, _) +: Constant(subject, _, _, _) +: Nil, _, _, _) +: Nil) => 
      //println(s"[safelang model SpeaksFor] statement=${statement}")
      // Qiang: bug fix 
      // use speaker.name instead of speaker.toString
      new SpeaksFor(Id(speaker.name), Id(subject.name), false)
    case Assertion(Structure(StrLit("speaksFor"), 
         Constant(speaker, _, _, _) 
      +: Constant(subject, _, _, _) 
      +: Constant(delegatable, _, _, _) 
      +: Nil, _, _, _) 
    +: Nil) => 
      //println(s"[safelang model SpeaksFor] statement=${statement}; delegatable=${delegatable}")
      new SpeaksFor(Id(speaker.name), Id(subject.name), if(delegatable.name == "true") true else false)
    case _ => throw UnSafeException(s"Not a valid speaksFor statement")
  }
}

case class SpeaksForOn(speaker: Id, subject: Id, objectId: Scid, delegatable: Boolean)

object SpeaksForOn {
  def apply(statement: Statement): SpeaksForOn = statement match {
    case Assertion(Structure(StrLit("speaksForOn"), 
         Constant(speaker, _, _, _) 
      +: Constant(subject, _, _, _) 
      +: Constant(objectId, _, _, _) 
      +: Nil, _, _, _) 
    +: Nil) => 
      new SpeaksForOn(Id(speaker.toString), Id(subject.toString), Scid(objectId.toString), false)
    case Assertion(Structure(StrLit("speaksForOn"), 
         Constant(speaker, _, _, _) 
      +: Constant(subject, _, _, _) 
      +: Constant(objectId, _, _, _) 
      +: Constant(delegatable, _, _, _)
      +: Nil, _, _, _) 
    +: Nil) => 
      new SpeaksForOn(Id(speaker.toString), Id(subject.toString), Scid(objectId.toString), if(delegatable.toString == "true") true else false)
    case _ => throw UnSafeException(s"Not a valid speaksForOn statement")
  }
}
