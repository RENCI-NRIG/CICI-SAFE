package safe.safelang
package safesets

/**
 * @param storeId publick key hash of store server
 * @param authenticated status boolean 
 */
class AuthNStatus(val storeId: String, private var authenticated: Boolean) {
  def isAuthenticated(): Boolean = {
    this.synchronized {
      authenticated 
    }
  }

  def setAuthenticated(): Boolean = {
    this.synchronized {
      authenticated = true
      authenticated
    }
  }
 
  def getStoreID(): String = {
    storeId
  }
  
  override def toString(): String = {
    s"storeId: ${storeId}    authenticated: ${authenticated}"
  }
}

object AuthNStatus {
  def apply(s: String, authNed: Boolean): AuthNStatus = new AuthNStatus(s, authNed)
  def apply(s: String): AuthNStatus = new AuthNStatus(s, false)
}
