package safe.safelog

object AnnotationTags {
  val UNCLASSIFIED = -1
  val ALLOW: Int = 0
  val REQUIRE: Int = 1
  val DENY: Int = 2

  def getTag(tagName: String): Int = tagName match {
    case "" => UNCLASSIFIED
    case "allow" => ALLOW
    case "require" => REQUIRE
    case "deny" => DENY
    case _ => throw ParserException(s"""Invalid tag name $tagName""")
  }
  
  def tagToString(tag: Int): String = tag match {
    case UNCLASSIFIED => ""
    case ALLOW => "allow"
    case REQUIRE => "require"
    case DENY => "deny"
    case _ => throw ParserException(s"""Invalid query tag $tag""")
  } 
}
