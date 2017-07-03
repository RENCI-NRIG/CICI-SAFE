package prolog.fluents

import scala.collection.mutable._
import prolog.terms._

class Deque[T] extends Queue[T] {
  def push(x: T) = prependElem(x)
  def add(x: T) = appendElem(x) 
  def add(d: Deque[T]): Unit = {
    val iter = d.iterator
    while(iter.hasNext) {
      add(iter.next)
    }
  }
}
