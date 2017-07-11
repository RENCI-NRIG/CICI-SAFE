package sample

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

class SpecMultiJvmNode3 extends WordSpec with MustMatchers {
  "A node" should {
    "be able to say hello" in {
      val message = "Hello from spec node 3"
      message must be("Hello from wrong spec node 3")
    }
  }
}

class SpecMultiJvmNode4 extends WordSpec with MustMatchers {
  "A node" should {
    "be able to say hello" in {
      val message = "Hello from spec node 4"
      message must be("Hello from spec node 4")
    }
  }
}
