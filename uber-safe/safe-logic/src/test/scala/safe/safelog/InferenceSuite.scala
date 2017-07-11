package safe.safelog

import org.scalatest._

class InferenceSuite extends UnitSpec {
  val inference = new Safelog()
  var _program: String = _
  def clear(): Unit = inference._statementCache.clear()

 
  "Safelog" should "solve simple facts (edb)" in {
     _program =
       """hello(world).
       | hello(safelog).
       | hello(X)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("hello(world)", "hello(safelog)")
     clear()
  }

  it should "solve simple rules (idb)" in {
     _program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B).
       | hello(A, B)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("hello(world, world)", "hello(world, safelog)", "hello(safelog, world)", "hello(safelog, safelog)")
     clear()
   }

  it should "solve simple rules with equality (idb)" in {
     _program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B), A = B.
       | hello(A, B)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("hello(world, world)", "hello(safelog, safelog)")
     clear()
  }

  it should "solve simple rules with inequality (idb)" in {
     _program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B), A != B.
       | hello(A, B)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("hello(world, safelog)", "hello(safelog, world)")
     clear()
   }

  it should "solve simple rules with arithmetic (idb)" in {
     _program =
       """twice(X, Y) :- Y is *(2, X).
       | twice(4, Y)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("twice(4, 8)")
     clear()
  }

  it should "solve recursive rules (idb)" in {
  /**
           a
          / \
         b   c   z
        / \ \ \ /
       d   e  f

  */
     _program =
       """parent(b, a).
	| parent(c, a).
	| parent(d, b).
	| parent(e, b).
	| parent(f, c).
	| parent(f, b).
	| parent(f, z).
	| ancestor(X, Y) :- parent(X, Y).
	| ancestor(X, Y) :- parent(X, Z), ancestor(Z, Y).
	| ancestor(X, Y)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("ancestor(b, a)", "ancestor(c, a)", "ancestor(d, b)", "ancestor(e, b)", "ancestor(f, c)", "ancestor(f, b)", "ancestor(f, z)", "ancestor(d, a)", "ancestor(e, a)", "ancestor(f, a)", "ancestor(f, a)")
     clear()
  }

  it should "solve recursive rules (idb) with a cycle" in {
     _program =
       """ avoids(Source, Target) :- owes(Source, Target).
        | avoids(Source, Target) :- owes(Source, Intermediate), avoids(Intermediate, Target).
	| owes(alice, bob).
        | owes(bob, charlie).
        | owes(charlie, david).
        | avoids(alice, Target)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("avoids(alice, bob)", "avoids(alice, charlie)", "avoids(alice, david)")
     clear()

     _program =
       """ avoids(Source, Target) :- owes(Source, Target).
        | avoids(Source, Target) :- owes(Source, Intermediate), Source != Intermediate, avoids(Intermediate, Target). // Order matters (for now)
	| owes(alice, bob).
        | owes(bob, charlie).
        | owes(charlie, alice).
        | avoids(Source, Target)?
       """.stripMargin
     inference.solve(_program).flatten.map(_.toString).toSet shouldBe Set("avoids(alice, bob)")
     clear()
  }
}
