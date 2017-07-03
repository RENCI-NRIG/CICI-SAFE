package safe
package safelang

/**
 * Token isn't a subclass of Any,
 * so token is different from StrLit
 */

case class Token(val name: String)
