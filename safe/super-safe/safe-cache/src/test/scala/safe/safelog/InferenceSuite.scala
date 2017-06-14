package safe.safelog

import org.scalatest._

class InferenceSuite extends UnitSpec {
  val inference = new Inference()
  var __program: String = _
 
  "Safelog" should "solve simple facts (edb)" in {
     __program =
       """hello(world).
       | hello(safelog).
       | hello(X)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "hello(world); hello(safelog)"

     __program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B).
       | hello(A, B)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "hello(world, world); hello(world, safelog); hello(safelog, world); hello(safelog, safelog)"

     __program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B), A = B.
       | hello(A, B)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "hello(world, world); hello(safelog, safelog)"


     __program =
       """hello(world).
       | hello(safelog).
       | hello(A, B) :- hello(A), hello(B), A != B.
       | hello(A, B)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "hello(world, safelog); hello(safelog, world)"

  }

  it should "solve recursive rules (idb)" in {
  /**
           a
          / \
         b   c   z
        / \ \ \ /
       d   e  f

  */
     __program =
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
     inference.solve(__program).flatten.mkString("; ") shouldBe "ancestor(b, a); ancestor(c, a); ancestor(d, b); ancestor(e, b); ancestor(f, c); ancestor(f, b); ancestor(f, z); ancestor(d, a); ancestor(e, a); ancestor(f, a); ancestor(f, a)"
  }

  it should "solve recursive rules (idb) with a cycle" in {
     __program =
       """ avoids(Source, Target) :- owes(Source, Target).
        | avoids(Source, Target) :- owes(Source, Intermediate), avoids(Intermediate, Target).
	| owes(alice, bob).
        | owes(bob, charlie).
        | owes(charlie, david).
        | avoids(alice, Target)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "avoids(alice, bob); avoids(alice, carl); avoids(alice, david)"

     __program =
       """ avoids(Source, Target) :- owes(Source, Target).
        | avoids(Source, Target) :- owes(Source, Intermediate), Source != Intermediate, avoids(Intermediate, Target). // Order matters (for now)
	| owes(alice, bob).
        | owes(bob, carl).
        | owes(charlie, alice).
        | avoids(Source, Target)?
       """.stripMargin
     inference.solve(__program).flatten.mkString("; ") shouldBe "avoids(alice, bob)"
  }
}
