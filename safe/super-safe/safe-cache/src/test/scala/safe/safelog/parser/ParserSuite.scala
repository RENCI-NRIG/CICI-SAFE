package safe.safelog
package parser

import org.scalatest._

class ParserSuite extends UnitSpec {
  val inference = new Inference()
  
  "Parser" should "parse standard constants such as Byte, Short, Int, Long, Float, Double, Hex, BigInteger, and a String" in {
    inference.parse("12b.") shouldBe Seq(Assertion(Seq(Constant("12"))))
    inference.parse("12B.") shouldBe Seq(Assertion(Seq(Constant("12"))))
    inference.parse("12s.") shouldBe Seq(Assertion(Seq(Constant("12"))))
    inference.parse("12S.") shouldBe Seq(Assertion(Seq(Constant("12"))))
    inference.parse("1.") shouldBe Seq(Assertion(Seq(Constant("1"))))
    inference.parse("1343333333333l.") shouldBe Seq(Assertion(Seq(Constant("1343333333333"))))
    inference.parse("1343333333333L.") shouldBe Seq(Assertion(Seq(Constant("1343333333333"))))
    inference.parse("2f.") shouldBe Seq(Assertion(Seq(Constant("2"))))
    inference.parse("2.3f.") shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    inference.parse("2.3F.") shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    inference.parse("1d.") shouldBe Seq(Assertion(Seq(Constant("1"))))
    inference.parse("2.3d.") shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    inference.parse("2.3D.") shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    inference.parse("0xCAFE.") shouldBe Seq(Assertion(Seq(Constant("51966"))))
    inference.parse("0xFACE.") shouldBe Seq(Assertion(Seq(Constant("64206"))))
    inference.parse("2223243232323232323232z.") shouldBe Seq(Assertion(Seq(Constant("2223243232323232323232"))))
    inference.parse("a.") shouldBe Seq(Assertion(Seq(Constant("a"))))
    inference.parse("hello.") shouldBe Seq(Assertion(Seq(Constant("hello"))))
  }

  it should "not parse constant types for numbers starting with a dot or terminating with a dot" in {
    intercept[RuntimeException] { //reserved for library calls
      inference.parse(".3.")
    }
    intercept[RuntimeException] {
      inference.parse("3..")
    }
  }

  it should "parse single quoted constants" in {
    inference.parse("""'hello'.""") shouldBe Seq(Assertion(Seq(Constant("""'hello'"""))))
    inference.parse("""'Hello'.""") shouldBe Seq(Assertion(Seq(Constant("""'Hello'"""))))
    inference.parse("""'Hello\'hello'.""") shouldBe Seq(Assertion(Seq(Constant("""'Hello'hello'"""))))
  }

  it should "parse variables starting with a captial letter or an underbar" in {
    inference.parse("X.") shouldBe Seq(Assertion(Seq(Variable("X"))))
    inference.parse("Hello.") shouldBe Seq(Assertion(Seq(Variable("Hello"))))
    inference.parse("_12Hello.") shouldBe Seq(Assertion(Seq(Variable("_12Hello"))))
  }

  it should "parse simple structures" in {
    inference.parse("hello(world).") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("world"))))))
    inference.parse("hello(world, Who).") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("world"), Variable("Who"))))))
    inference.parse("hello(a, World) :- World := test.") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Structure(".is", Seq(Variable("World"), Constant("test"))))))
    inference.parse("hello(a, World) :- World; test.") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Variable("World"))), Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Constant("test"))))
  }

  it should "ignore single line and multiline comments" in {
    inference.parse("hello. // this is a single line comment test") shouldBe Seq(Assertion(Seq(Constant("hello"))))
    inference.parse(s"""hello. /* this is a multi line comment test
                     |  spanning more than a single line */""") shouldBe Seq(Assertion(Seq(Constant("hello"))))
  }

  it should "parse statement types (assertion, retraction, query, and queryAll)" in {
    inference.parse("hello.") shouldBe Seq(Assertion(Seq(Constant("hello"))))
    inference.parse("hello(World) :- World := test.") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Variable("World"))), Structure(".is", Seq(Variable("World"), Constant("test"))))))
    inference.parse("hello~") shouldBe Seq(Retraction(Seq(Constant("hello"))))
    inference.parse("hello?") shouldBe Seq(Query(Seq(Constant("hello"))))
    inference.parse("hello??") shouldBe Seq(QueryAll(Seq(Constant("hello"))))
  }

  it should "parse statement types (assertion, retraction, query, and queryAll) written in alternative style as sentences" in {
    inference.parse("assert hello(World) if World is test end") shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Variable("World"))), Structure(".is", Seq(Variable("World"), Constant("test"))))))

    inference.parse("retract hello end") shouldBe Seq(Retraction(Seq(Constant("hello"))))
    inference.parse("query hello end") shouldBe Seq(Query(Seq(Constant("hello"))))
    inference.parse("queryAll hello end") shouldBe Seq(QueryAll(Seq(Constant("hello"))))
  }

  it should "raise an parser exception when not terminated properly (missing [.~?])" in {
    intercept[RuntimeException] {
      inference.parse("Hello(world)")
    }
    intercept[RuntimeException] {
      inference.parse("hello(world~)")
    }
  }

  it should "parse arthimetic operators" in {
    inference.parse("+(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".plus", Seq(Constant("2"), Constant("3"))))))
    inference.parse("-(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".minus", Seq(Constant("2"), Constant("3"))))))
    inference.parse("*(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".times", Seq(Constant("2"), Constant("3"))))))
    inference.parse("/(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".div", Seq(Constant("2"), Constant("3"))))))
    inference.parse("%(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".rem", Seq(Constant("2"), Constant("3"))))))
    inference.parse("min(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".min", Seq(Constant("2"), Constant("3"))))))
    inference.parse("max(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".max", Seq(Constant("2"), Constant("3"))))))
  }

  it should "parse comparsion operators" in {
    inference.parse("<(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".lt", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 < 3.") shouldBe Seq(Assertion(Seq(Structure(".lt", Seq(Constant("2"), Constant("3"))))))
    inference.parse("<=(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".lteq", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 <= 3.") shouldBe Seq(Assertion(Seq(Structure(".lteq", Seq(Constant("2"), Constant("3"))))))
    inference.parse(">(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".gt", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 > 3.") shouldBe Seq(Assertion(Seq(Structure(".gt", Seq(Constant("2"), Constant("3"))))))
    inference.parse(">=(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".gteq", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 >= 3.") shouldBe Seq(Assertion(Seq(Structure(".gteq", Seq(Constant("2"), Constant("3"))))))
  }

  it should "parse built-in operators" in {
    inference.parse(":=(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".is", Seq(Constant("2"), Constant("3"))))))
    inference.parse("is(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".is", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 := 3.") shouldBe Seq(Assertion(Seq(Structure(".is", Seq(Constant("2"), Constant("3"))))))
    inference.parse("=:=(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".compare", Seq(Constant("2"), Constant("3"))))))
    inference.parse("!=:=(2, 3).") shouldBe Seq(Assertion(Seq(NegatedTerm(".not", Structure(".compare", Seq(Constant("2"), Constant("3")))))))
    inference.parse("compare(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".compare", Seq(Constant("2"), Constant("3"))))))
    inference.parse("not compare(2, 3).") shouldBe Seq(Assertion(Seq(NegatedTerm(".not", Structure(".compare", Seq(Constant("2"), Constant("3")))))))
    inference.parse("2 =:= 3.") shouldBe Seq(Assertion(Seq(Structure(".compare", Seq(Constant("2"), Constant("3"))))))
    inference.parse("=(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".unify", Seq(Constant("2"), Constant("3"))))))
    inference.parse("unify(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".unify", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 = 3.") shouldBe Seq(Assertion(Seq(Structure(".unify", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 != 3.") shouldBe Seq(Assertion(Seq(NegatedTerm(".not", Structure(".unify", Seq(Constant("2"), Constant("3")))))))
  }

  it should "parse implicit constructs through slang" in {
    inference.parse("..+(2, 3).") shouldBe Seq(Assertion(Seq(Structure("+", Seq(Constant("2"), Constant("3"))))))
    inference.parse("@(2, 3).") shouldBe Seq(Assertion(Seq(Structure(".at", Seq(Constant("2"), Constant("3"))))))
    inference.parse("2 @ 3.") shouldBe Seq(Assertion(Seq(Structure(".at", Seq(Constant("2"), Constant("3"))))))
    inference.parse(""""2".""") shouldBe Seq(Assertion(Seq(Constant(""""2""""))))
    inference.parse(""""hello($World)".""") shouldBe Seq(Assertion(Seq(Structure(".quasiString", Constant(""""hello($World)"""") +: Constant("World") +: Seq(Variable("World"))))))
  }
}
