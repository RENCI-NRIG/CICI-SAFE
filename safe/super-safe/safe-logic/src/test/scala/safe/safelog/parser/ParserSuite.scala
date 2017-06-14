package safe.safelog
package parser

import org.scalatest._

class ParserSuite extends UnitSpec {
  val parser = new Parser()
  def clear(): Unit = parser._statementCache.clear()
  
  "Parser" should "parse standard constants such as Byte, Short, Int, Long, Float, Double, Hex, BigInteger, and a String" in {
    parser.parse("12b.").values.flatten shouldBe Seq(Assertion(Seq(Constant("12"))))
    clear()
    parser.parse("12B.").values.flatten shouldBe Seq(Assertion(Seq(Constant("12"))))
    clear()
    parser.parse("12s.").values.flatten shouldBe Seq(Assertion(Seq(Constant("12"))))
    clear()
    parser.parse("12S.").values.flatten shouldBe Seq(Assertion(Seq(Constant("12"))))
    clear()
    parser.parse("1.").values.flatten shouldBe Seq(Assertion(Seq(Constant("1"))))
    clear()
    parser.parse("1343333333333l.").values.flatten shouldBe Seq(Assertion(Seq(Constant("1343333333333"))))
    clear()
    parser.parse("1343333333333L.").values.flatten shouldBe Seq(Assertion(Seq(Constant("1343333333333"))))
    clear()
    parser.parse("2f.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2"))))
    clear()
    parser.parse("2.3f.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    clear()
    parser.parse("2.3F.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    clear()
    parser.parse("1d.").values.flatten shouldBe Seq(Assertion(Seq(Constant("1"))))
    clear()
    parser.parse("2.3d.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    clear()
    parser.parse("2.3D.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2.3"))))
    clear()
    parser.parse("0xCAFE.").values.flatten shouldBe Seq(Assertion(Seq(Constant("51966"))))
    clear()
    parser.parse("0xFACE.").values.flatten shouldBe Seq(Assertion(Seq(Constant("64206"))))
    clear()
    parser.parse("2223243232323232323232z.").values.flatten shouldBe Seq(Assertion(Seq(Constant("2223243232323232323232"))))
    clear()
    parser.parse("a.").values.flatten shouldBe Seq(Assertion(Seq(Constant("a"))))
    clear()
    parser.parse("hello.").values.flatten shouldBe Seq(Assertion(Seq(Constant("hello"))))
    clear()
  }

  it should "not parse constant types for numbers starting with a dot or terminating with a dot" in {
    intercept[RuntimeException] { //reserved for library calls
      parser.parse(".3.")
    }
    intercept[RuntimeException] {
      clear()
      parser.parse("3..")
    }
  }

  it should "parse single quoted constants" in {
    clear()
    parser.parse("""'hello'.""").values.flatten shouldBe Seq(Assertion(Seq(Constant("""'hello'"""))))
    clear()
    parser.parse("""'Hello'.""").values.flatten shouldBe Seq(Assertion(Seq(Constant("""'Hello'"""))))
    clear()
    parser.parse("""'Hello\'hello'.""").values.flatten shouldBe Seq(Assertion(Seq(Constant("""'Hello'hello'"""))))
    clear()
  }

  it should "parse variables starting with a captial letter or an underbar" in {
    parser.parse("X.").values.flatten shouldBe Seq(Assertion(Seq(Variable("X"))))
    clear()
    parser.parse("Hello.").values.flatten shouldBe Seq(Assertion(Seq(Variable("Hello"))))
    clear()
    parser.parse("_12Hello.").values.flatten shouldBe Seq(Assertion(Seq(Variable("_12Hello"))))
    clear()
  }

  it should "parse simple structures" in {
    parser.parse("hello(world).").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("world"))))))
    clear()
    parser.parse("hello(world, Who).").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("world"), Variable("Who"))))))
    clear()
    parser.parse("hello(a, World) :- World := test.").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Structure("_is", Seq(Variable("World"), Constant("test"))))))
    clear()
    parser.parse("hello(a, World) :- World; test.").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Variable("World"))), Assertion(Seq(Structure("hello", Seq(Constant("a"), Variable("World"))), Constant("test"))))
    clear()
  }

  it should "ignore single line and multiline comments" in {
    parser.parse("hello. // this is a single line comment test").values.flatten shouldBe Seq(Assertion(Seq(Constant("hello"))))
    clear()
    parser.parse(s"""hello. /* this is a multi line comment test
                     |  spanning more than a single line */""").values.flatten shouldBe Seq(Assertion(Seq(Constant("hello"))))
    clear()
  }

  it should "parse statement types (assertion, retraction, query, and queryAll)" in {
    clear()
    parser.parse("hello.").values.flatten shouldBe Seq(Assertion(Seq(Constant("hello"))))
    clear()
    parser.parse("hello(World) :- World := test.").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Variable("World"))), Structure("_is", Seq(Variable("World"), Constant("test"))))))
    clear()
    parser.parse("hello~").values.flatten shouldBe Seq(Retraction(Seq(Constant("hello"))))
    clear()
    parser.parse("hello/1~").values.flatten shouldBe Seq(Retraction(Seq(Constant("_withArity"), Constant("hello"), Constant("1"))))
    clear()
    parser.parse("hello?").values.flatten shouldBe Seq(Query(Seq(Constant("hello"))))
    clear()
    parser.parse("hello??").values.flatten shouldBe Seq(QueryAll(Seq(Constant("hello"))))
    clear()
  }

  it should "parse statement types (assertion, retraction, query, and queryAll) written in alternative style as sentences" in {
    parser.parse("assert hello(World) if World is test end").values.flatten shouldBe Seq(Assertion(Seq(Structure("hello", Seq(Variable("World"))), Structure("_is", Seq(Variable("World"), Constant("test"))))))
    clear()

    parser.parse("retract hello end").values.flatten shouldBe Seq(Retraction(Seq(Constant("hello"))))
    clear()
    parser.parse("query hello end").values.flatten shouldBe Seq(Query(Seq(Constant("hello"))))
    clear()
    parser.parse("queryAll hello end").values.flatten shouldBe Seq(QueryAll(Seq(Constant("hello"))))
    clear()
  }

  it should "raise an parser exception when not terminated properly (missing [.~?])" in {
    intercept[RuntimeException] {
      parser.parse("Hello(world)")
    }
    intercept[RuntimeException] {
      clear()
      parser.parse("hello(world~)")
    }
  }

  it should "parse arthimetic operators" in {
    clear()
    parser.parse("+(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_plus", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("-(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_minus", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("*(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_times", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("/(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_div", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("%(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_rem", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("min(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_min", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("max(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_max", Seq(Constant("2"), Constant("3"))))))
    clear()
  }

  it should "parse comparsion operators" in {
    parser.parse("<(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_lt", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 < 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_lt", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("<=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_lteq", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 <= 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_lteq", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse(">(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_gt", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 > 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_gt", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse(">=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_gteq", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 >= 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_gteq", Seq(Constant("2"), Constant("3"))))))
    clear()
  }

  it should "parse built-in operators" in {
    parser.parse(":=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_is", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("is(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_is", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 := 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_is", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("=:=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_compare", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("!=:=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(NegatedTerm("_not", Structure("_compare", Seq(Constant("2"), Constant("3")))))))
    clear()
    parser.parse("compare(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_compare", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("not compare(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(NegatedTerm("_not", Structure("_compare", Seq(Constant("2"), Constant("3")))))))
    clear()
    parser.parse("2 =:= 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_compare", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("=(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_unify", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("unify(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_unify", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 = 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_unify", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 != 3.").values.flatten shouldBe Seq(Assertion(Seq(NegatedTerm("_not", Structure("_unify", Seq(Constant("2"), Constant("3")))))))
    clear()
  }

  it should "parse implicit constructs through slang" in {
    parser.parse("..+(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("+", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("@(2, 3).").values.flatten shouldBe Seq(Assertion(Seq(Structure("_at", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse("2 @ 3.").values.flatten shouldBe Seq(Assertion(Seq(Structure("_at", Seq(Constant("2"), Constant("3"))))))
    clear()
    parser.parse(""""2".""").values.flatten shouldBe Seq(Assertion(Seq(Constant(""""2""""))))
    clear()
    parser.parse(""""hello($World)".""").values.flatten shouldBe Seq(Assertion(Seq(Structure("_interpolate", Constant(""""hello($World)"""") +: Constant("World") +: Seq(Variable("World"))))))
    clear()
  }
}
