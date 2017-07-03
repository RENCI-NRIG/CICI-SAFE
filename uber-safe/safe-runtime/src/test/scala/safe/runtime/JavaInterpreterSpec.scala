package safe.runtime

import org.scalatest._

class JavaInterpreterSpec extends UnitSpec {
  val interpreter = JavaInterpreter()

  val simpleScript = s"""public Double power(Double x, Double y) = { x * y };
	               | return power(8, 9);
	             """.stripMargin

  val test = s"""return "abc";"""

  "Scala interpreter" should "evaluate simple function without explicit arguments" in {
    interpreter.eval(test).toString shouldBe "72.0"
  }
}
