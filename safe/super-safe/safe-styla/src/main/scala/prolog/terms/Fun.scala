package prolog.terms
import prolog.interp.Prog
import prolog.io.IO
import prolog.Config
import scala.collection.mutable.LinkedHashSet
import com.typesafe.scalalogging.LazyLogging

class Fun(sym: String, var args: Array[Term]) extends Const(sym) with LazyLogging {
  val isSpk: Boolean = (sym == ":")
  def this(sym: String) = this(sym, null)

  override def len = args.length

  override def exec(p: Prog) = {
    if(isSpk) {
      //assert(this.len == 2 && getArg(1).isInstanceOf[Fun], 
      //    s"Atom with speaker must have a predicate: $this; args.length=${args.length}; args(1)=${args(1)}; args(1).getClass=${args(1).getClass}; getArg(1)=${getArg(1)}; getArg(1).getClass=${getArg(1).getClass}")
      getArg(1).exec(p)
    } else {
      super.exec(p)
    }
  }
 
  def predicateName(): String = {
    if(isSpk) {
      //assert(this.len == 2 && getArg(1).isInstanceOf[Fun], 
      //    s"Atom with speaker must have a predicate: $this; args.length=${args.length}; getArg(1)=${getArg(1)}; getArg(1).getClass=${getArg(1).getClass}")
      getArg(1).asInstanceOf[Fun].sym 
    } else {
      sym
    }
  }

  def isFirstParamConstant(): Boolean = {
    if(isSpk) {
      if(args.length == 2) {
        getArg(1) match {
          case f: Fun => f.isFirstParamConstant() 
          case _ => throw new RuntimeException(s"Atom does not contain a valid predicate: ${getArg(1)}") 
        }
      } else {
        throw new RuntimeException(s"Invalid atom body: ${this}")
      }
    } else {
      if(args.length > 0) {
        getArg(0) match {
          case c: Nonvar => true
          case _ => false
          //case x => logger.info(s"x.getClass:${x.getClass}   x:${x}"); false
        }
      } else {
        false
      }
    }
  }

  def allParamsNonvar(): Boolean = {
    var res = true
    if(isSpk) {
      if(args.length == 2) {
        getArg(1) match {
          case f: Fun => f.allParamsNonvar() 
          case _ => throw new RuntimeException(s"Atom does not contain a valid predicate: ${getArg(1)}") 
        }
      } else {
        throw new RuntimeException(s"Invalid atom body: ${this}")
      }
    } else {
      for(i <- 0 to args.length -1) {
        getArg(i) match {
          case c: Var => res = false
          case _ => 
        }
      }
      res
    }
  }

  def getIndex(): String = {
    Config.config.indexing match {
      case "primary" => primaryIndex()
      case "secondary" => secondaryIndex()
      case "tertiary" => tertiaryIndex()
      case _ => throw new RuntimeException(s"Invalid indexing: ${Config.config.indexing}")
    }
  }

  def tertiaryIndex(): String = s"${sym}${args.length}"  // for speaksFor  (predicate + arity)

  def primaryIndex(): String = {
    if(isSpk) {
      if(args.length == 2) {
        getArg(1) match {
          case f: Fun => f.primaryIndex() 
          case _ => throw new RuntimeException(s"Atom does not contain a valid predicate: ${getArg(1)}") 
        }
      } else {
        throw new RuntimeException(s"Invalid atom body: ${this}")
      }
    } else {
      val sb = new StringBuffer(sym)
      sb.append(args.length)   // predicate + arity
      sb.toString
      //s"${sym}${args.length}"  // predicate + arity 
    }
  }

  def secondaryIndex(): String = {
    if(isSpk) {
      if(args.length == 2) {
        getArg(1) match {
          case f: Fun => f.secondaryIndex() 
          case _ => throw new RuntimeException(s"Atom does not contain a valid predicate: ${getArg(1)}") 
        }
      } else {
        throw new RuntimeException(s"Invalid atom body: ${this}")
      }
    } else {
      if(args.length > 0) {
        getArg(0) match {
          case c: Const => 
            val sb = new StringBuffer(sym)
            sb.append(args.length)
            //if(c.sym.length > 10) { 
            //  sb.append(c.sym.takeRight(10))
            //} else {
            //  sb.append(c.sym)
            //}
            sb.append(c.sym)
            sb.toString()
            //s"${sym}${args.length}${c.sym}"   // predicate + arity + 1st_arg
          case _ =>
            val sb = new StringBuffer(sym)
            sb.append(args.length)
            sb.toString()
            //s"${sym}${args.length}"
        }
      } else {
        val sb = new StringBuffer(sym)
        sb.append(args.length)
        sb.toString
        //s"${sym}${args.length}"
      }
    }
  }


  final def init(arity: Int) {
    this.args = new Array[Term](arity)
    for (i <- 0 until arity) {
      args(i) = new Var()
    }
  }

  final def getArg(i: Int) = args(i).ref

  final def putArg(i: Int, t: Term, p: Prog) = {
    val oldtop = p.trail.size
    if (args(i).ref.unify(t, p.trail)) 1
    else {
      p.trail.unwind(oldtop)
      0
    }
  }

  override def unify(term: Term, trail: Trail): Boolean = {
    val that = term.ref
    if (bind_to(that, trail)) {
      val other = that.asInstanceOf[Fun]
      val l = args.length
      if (other.args.length != l) return false
      for (i <- 0 until l) {
        if (!args(i).ref.unify(other.args(i), trail)) {
          //logger.info(s"[Styla Fun unify] Cannot bind: args($i)=${getArg(i)}   other.args($i)=${other.getArg(i)}")
          //logger.info(s"[Styla Fun unify] this=${this}    term=${term}")
          return false;
        }
      }
      return true;
    } else {
      //logger.info(s"[Styla Fun unify] that.bind_to  this=${this}    term=${term}")
      return that.bind_to(this, trail)
    }
  }

  def safeCopy(): Fun = {
    new Fun(sym)
  }

  // stuff allowing polymorphic cloning of Fun subclasses
  // without using reflection - should be probably faster than
  // reflection classes - to check

  final def funClone(): Fun = {
    try {
      // use of clone is needed for "polymorphic" copy 
      val res = clone().asInstanceOf[this.type]
      //println(s"funClone:  this:${this}   res:${res}   this==res:${this==res} \
      //          this.ref==res.ref:${this.ref==res.ref}   this==this.ref:${this==this.ref} \
      //          res==res.ref:${res==res.ref}")
      res.args = null
      res
    } catch {
      case _: Error => {
        IO.warnmes("funcClone failed on" + this)
        null
      }
    }
  }

  override def tcopy(dict: Copier): Fun = {
    val t = funClone()
    t.args = new Array[Term](args.length)
    for (i <- 0 until args.length) {
      t.args(i) = args(i).tcopy(dict)
    }
    t
  }

  override def vcollect(dict: LinkedHashSet[Term]) {
    for (i <- 0 until args.length) {
      args(i).vcollect(dict)
    }
  }

  /*
  override def ucopy(term: Term, dict: Copier, trail: Trail): Fun = {
    val that = term.ref
    if (that.isInstanceOf[Var]) {
      val t = funClone()
      t.args = new Array[Term](args.length)
      for (i <- 0 until args.length) {
        t.args(i) = args(i).tcopy(dict)
      }
      t
    } else if (unify(that, trail)) that.asInstanceOf[Fun]
    else null
  }
  */

  override def toString = {
    val s = new StringBuffer
    s.append(name)
    //println(s"[Fun toString] name=${name}   args.length=${args.length}  getArg(0)=${getArg(0)} \
    //          getArg(0).getClass=${getArg(0).getClass}")
    s.append('(')
    if (args.eq(null)) {
      s.append("...null...")
    } else {
      if(len >= 1)
        s.append(getArg(0))
      for (i <- 1 until args.length) {
        s.append("," + getArg(i))
        val argi = getArg(i)
        //println(s"[Fun toString] getArg(i)=${argi}     getArg(i).getClass=${argi.getClass}")
      }
    }
    s.append(')')
    s.toString
  }
  
  override def hashCode = {
    var result = 1
    val prime = 31
    result = result * prime + sym.hashCode
    for(i <- 0 to args.length-1) {
      val arg = getArg(i)
      result = result * prime + arg.hashCode
    } 
    result
  }

}
