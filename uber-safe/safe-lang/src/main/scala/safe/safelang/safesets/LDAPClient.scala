package safe.safelang
package safesets

import safe.safelog.UnSafeException

import scala.collection.mutable.{Map => MutableMap, ListBuffer}
import java.util.Hashtable
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.directory.Attribute
import javax.naming.directory.BasicAttribute
import javax.naming.directory.DirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext

/**
 * An LDAP client to retrieve attributes of users from a remote LDAP server
 */

class LDAPClient { 
 
  /**
   * A Hash map holding all ldap contexts that have beeen created so far
   * We might want to put a cap on the nubmer of in-memory contexts later.
   * Ldap contexts in the map are indexed by the ldap server addresses. 
   */
  val ldapContexts = MutableMap[String, LdapContext]()

  val ldapUsername = Config.config.ldapUsername
  val ldapPassword = Config.config.ldapPassword

  println(s"ldapUsername: ${ldapUsername}    ldapPassword: ${ldapPassword}")
 
  /**
   * An LDAP context is created under a principal using its credential. All queries
   * later performed through this context are under this principal. The SAFE instance
   * operator needs to ensure that the principal it supplies is authorized to 
   * to perform later queries and supply the username and its passwd through
   * the SAFE configuration along with other properties.
   */
  def createLDAPContext(ldapServerAddr: String, ldapPrincipal: String,
      ldapCredential: String): LdapContext = {

    val env = new Hashtable[String, Object]()
    env.put(Context.SECURITY_AUTHENTICATION, "simple")
    if(ldapPrincipal != null) {
      env.put(Context.SECURITY_PRINCIPAL, ldapPrincipal)
    }
    if(ldapCredential != null) {
      env.put(Context.SECURITY_CREDENTIALS, ldapCredential)
    }
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapServerAddr);
    
    // Ensures that objectSID attribute values
    // will be returned as a byte array rather than a String
    env.put("java.naming.ldap.attributes.binary", "objectSID")

    // The second argument is for request connection control, which we set to null
    val ctx = new InitialLdapContext(env, null)

    ctx   
  }

  // AneExample ldap search base:  "ou=people,o=ImPACT,dc=cilogon,dc=org"
  def queryLDAPAndGetGroups(ctx: LdapContext, searchBase: String): ListBuffer[String] = {
    val searchFilter: String = "(objectClass=person)"
    val searchControls: SearchControls = new SearchControls()
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
   
    val results: NamingEnumeration[SearchResult] = ctx.search(searchBase, searchFilter, searchControls)

    var itemCount = 0
    while(results.hasMoreElements) {
      val srLdapUser: SearchResult = results.nextElement

      val uid: String = srLdapUser.getAttributes.get("uid").get.asInstanceOf[String]
      val cn: String = srLdapUser.getAttributes.get("cn").get.asInstanceOf[String]
      println("uid: " + uid + "   cn: " + cn)
      val memberAttr: Attribute = srLdapUser.getAttributes.get("isMemberOf")
      // println("class: " + isMemberOf.getAll().getClass())
      //val memberships: NamingEnumeration[?] = isMemberOf.getAll
      val memberships = memberAttr.getAll
      while(memberships.hasMoreElements) {
        val m: String = memberships.nextElement.asInstanceOf[String]
        println("isMemberOf: " + m)
      }
      println("")
      itemCount += 1
    }
    ListBuffer[String]()
  }
}

object LDAPClient extends App {
  val client = new LDAPClient()
  val serverAddr = "ldap://registry-test.cilogon.org:389"
  val searchBase = "ou=people,o=ImPACT,dc=cilogon,dc=org"
  val ctx = client.createLDAPContext(serverAddr, client.ldapUsername, client.ldapPassword)
  client.queryLDAPAndGetGroups(ctx, searchBase)
}
