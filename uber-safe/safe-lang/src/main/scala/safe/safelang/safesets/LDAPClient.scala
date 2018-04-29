package safe.safelang
package safesets

import safe.safelog.UnSafeException
import safe.cache.SafeTable

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
import javax.naming.Context

import com.typesafe.scalalogging.LazyLogging

/**
 * An LDAP client to retrieve attributes of users from a remote LDAP server.
 * This client only implements set fetch.
 * Our identity model assumes that LDAP links are only attached to an ID set
 * as a way to provide user identity supplement endorsed by the LDAP server. 
 */

class LDAPClient extends LazyLogging { 
 
  /**
   * A Hash map holding all ldap contexts that have beeen created so far
   * We might want to put a cap on the nubmer of in-memory contexts later.
   * Ldap contexts in the map are indexed by the ldap server addresses. 
   */
  val ldapContexts = new SafeTable[String, LdapContext](
    1024*1024,
    0.75f,
    16
  ) 

  /**
   * Default authorized principal for LDAP queries.
   */
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
    if(!ldapPrincipal.isEmpty) {
      env.put(Context.SECURITY_PRINCIPAL, ldapPrincipal)
    }
    if(!ldapCredential.isEmpty) {
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

  // An example ldap search base:  "ou=people,o=ImPACT,dc=cilogon,dc=org"
  def queryLDAPAndGetGroups(ctx: LdapContext, searchBase: String): Tuple2[String, ListBuffer[String]] = {
    val searchFilter: String = "(objectClass=person)"
    val searchControls: SearchControls = new SearchControls()
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
   
    val results: NamingEnumeration[SearchResult] = ctx.search(searchBase, searchFilter, searchControls)

    // Only look into the first record
    //var itemCount = 0
    //while(results.hasMoreElements) {
    if(results.hasMoreElements) {
      val ldapUser: SearchResult = results.nextElement

      //val uid: String = srLdapUser.getAttributes.get("uid").get.asInstanceOf[String]
      //val cn: String = srLdapUser.getAttributes.get("cn").get.asInstanceOf[String]
      //println("uid: " + uid + "   cn: " + cn)
      val employeeNumber = ldapUser.getAttributes.get("employeeNumber").toString //.asInstanceOf[String]
      logger.info(s"EmployeeNumber: ${employeeNumber}")
      val memberAttr: Attribute = ldapUser.getAttributes.get("isMemberOf")
      // println("class: " + isMemberOf.getAll().getClass())
      //val memberships: NamingEnumeration[?] = isMemberOf.getAll
      val groups = ListBuffer[String]()
      val memberships = memberAttr.getAll
      while(memberships.hasMoreElements) {
        val m: String = memberships.nextElement.asInstanceOf[String]
        logger.info(s"isMemberOf: ${m}")
        groups += m
      }
      //println("")
      //itemCount += 1
      employeeNumber -> groups
    } else {
      throw UnSafeException(s"didn't find any record on ${ctx.getEnvironment().get(Context.PROVIDER_URL)}")
    }
  }

  def fetchSlogSet(certaddr: CertAddr): Seq[SlogSet] = {
    val serveraddr = certaddr.getUrl
    var ctx: LdapContext = null
    if(ldapContexts.containsKey(serveraddr)) {
      ctx = ldapContexts.get(serveraddr).get
    } else {  // make a context and add the context into cache for future user
      ctx = createLDAPContext(serveraddr, ldapUsername, ldapPassword)
      logger.info(s"add context for ${serveraddr}")
      ldapContexts.put(serveraddr, ctx)
    } 
    val uidGroups = queryLDAPAndGetGroups(ctx, "")  // search base is part of the provider URL
    Seq[SlogSet]()
  }
}

object LDAPClient extends App {
  val client = new LDAPClient()
//  val serverAddr = "ldap://registry-test.cilogon.org:389"
//  val searchBase = "ou=people,o=ImPACT,dc=cilogon,dc=org"
//  val serverAddr = "ldap://registry-test.cilogon.org:389/dc=org"
//  val searchBase = "ou=people,o=ImPACT,dc=cilogon"
  val serverAddr = "ldap://registry-test.cilogon.org:389/ou=people,o=ImPACT,dc=cilogon,dc=org"
  val searchBase = ""
  val ctx = client.createLDAPContext(serverAddr, client.ldapUsername, client.ldapPassword)
  client.queryLDAPAndGetGroups(ctx, searchBase)
}
