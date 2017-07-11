package safe.programming

import scala.util.control.Breaks._
import scala.collection.mutable.{Map => MutableMap, LinkedHashSet => OrderedSet}
import java.util.concurrent.ThreadLocalRandom
import com.typesafe.scalalogging.LazyLogging

/** Helpers for sets used in FSM */
trait StateMachineSetHelper extends LazyLogging {
  /** Get a random entry from a set of principals */
  def getRandomEntry(pset: OrderedSet[PrincipalStub]): Option[PrincipalStub] = {
    var entry: Option[PrincipalStub] = None
    pset.synchronized {
      if(pset.size != 0) {
        val pos = ThreadLocalRandom.current().nextInt(pset.size)
        logger.info(s"[Genibench getRandomEntry] pset.size:${pset.size}   pos:${pos}")
        entry = Some(getEntryAt(pset, pos).asInstanceOf[PrincipalStub])
      }
    }
    entry
  }

  /** 
   * Get a random KV pair from a map. First randomly pick up a key. If the key
   * is mappped to a set of values (in a map), randomly pick up a value from 
   * that map.
   */
  implicit val dummyInt: Int = 0
  implicit val dummyMap: Boolean = true
  def getRandomEntry(m: MutableMap[String, MutableMap[PrincipalStub, Int]]) (implicit ignore: Int, map: Boolean):
      Option[Tuple3[String, PrincipalStub, Int]] = {
    var entry: Option[Tuple3[String, PrincipalStub, Int]] = None
    m.synchronized {
      val rdm = ThreadLocalRandom.current()
      val keyset = m.keySet
      if(keyset.size != 0) {
        val k_pos = rdm.nextInt(keyset.size)
        val key = getEntryAt(keyset, k_pos).asInstanceOf[String]
        val pset = m(key).keySet // m must contain key 
        if(pset.size != 0) {
          val p_pos = rdm.nextInt(pset.size)
          val p = getEntryAt(pset, p_pos).asInstanceOf[PrincipalStub]
          val count = m(key)(p)
          entry = Some((key, p, count))
        }
      }
    }
    entry
  }

  /** 
   * Pick a random dentry, as the above.
   */
  implicit val dummyPrincipalStub: PrincipalStub = null 
  def getRandomEntry(m: MutableMap[String, MutableMap[String, PrincipalStub]]) (implicit ignore: PrincipalStub, map: Boolean):
      Option[Tuple3[String, String, PrincipalStub]] = {
    var entry: Option[Tuple3[String, String, PrincipalStub]] = None
    m.synchronized {
      val rdm = ThreadLocalRandom.current()
      val keyset = m.keySet
      if(keyset.size != 0) {
        val k_pos = rdm.nextInt(keyset.size)
        val key = getEntryAt(keyset, k_pos).asInstanceOf[String]
        val sset = m(key).keySet // m must contain key 
        if(sset.size != 0) {
          val s_pos = rdm.nextInt(sset.size)
          val s = getEntryAt(sset, s_pos).asInstanceOf[String]
          val p = m(key)(s)
          entry = Some((key, s, p))
        }
      }
    }
    entry
  }

  /** 
   * Pick a random dentry, as the above.
   */
  implicit val dummyString: String = ""
  def getRandomEntry(m: MutableMap[String, MutableMap[String, String]]) (implicit ignore: String, map: Boolean):
      Option[Tuple3[String, String, String]] = {
    var entry: Option[Tuple3[String, String, String]] = None
    m.synchronized {
      val rdm = ThreadLocalRandom.current()
      val keyset = m.keySet
      if(keyset.size != 0) {
        val k_pos = rdm.nextInt(keyset.size)
        val key = getEntryAt(keyset, k_pos).asInstanceOf[String]
        val aclset = m(key).keySet // m must contain key 
        if(aclset.size != 0) {
          val a_pos = rdm.nextInt(aclset.size)
          val acl = getEntryAt(aclset, a_pos).asInstanceOf[String]
          val s = m(key)(acl)
          entry = Some((key, acl, s))
        }
      }
    }
    entry
  }

  /** Get a random entry, for selecting sliver and cp pair */
  def getRandomEntry(sliverset: MutableMap[String, PrincipalStub]) (implicit ignore: PrincipalStub): 
      Option[Tuple2[String, PrincipalStub]] = {
    var entry: Option[Tuple2[String, PrincipalStub]] = None
    sliverset.synchronized {
      val keyset = sliverset.keySet
      if(keyset.size != 0) {
        val pos = ThreadLocalRandom.current().nextInt(keyset.size)
        logger.info(s"[Genibench getRandomEntry] keyset.size:${keyset.size}   pos:${pos}")
        val sliverId = getEntryAt(keyset, pos).asInstanceOf[String]
        val cp = sliverset(sliverId)
        entry = Some((sliverId, cp))
      }
    }
    entry
  }

  /** Get a random entry, for selecting slice member and delcount pair */
  def getRandomEntry(userset: MutableMap[PrincipalStub, Int]) (implicit ignore: Int): 
      Option[Tuple2[PrincipalStub, Int]] = {
    var entry: Option[Tuple2[PrincipalStub, Int]] = None
    userset.synchronized {
      val keyset = userset.keySet
      if(keyset.size != 0) {
        val pos = ThreadLocalRandom.current().nextInt(keyset.size)
        logger.info(s"[Genibench getRandomEntry] keyset.size:${keyset.size}   pos:${pos}")
        val member = getEntryAt(keyset, pos).asInstanceOf[PrincipalStub]
        val delcount = userset(member)
        entry = Some((member, delcount))
      }
    }
    entry
  }


  /** Get a random entry, for selecting acl and sliver's controlling slice pair */
  def getRandomEntry(aclset: MutableMap[String, String]) (implicit ignore: String): 
      Option[Tuple2[String, String]] = {
    var entry: Option[Tuple2[String, String]] = None
    aclset.synchronized {
      val keyset = aclset.keySet
      if(keyset.size != 0) {
        val pos = ThreadLocalRandom.current().nextInt(keyset.size)
        logger.info(s"[Genibench getRandomEntry] keyset.size:${keyset.size}   pos:${pos}")
        val acl = getEntryAt(keyset, pos).asInstanceOf[String]
        val sliceId = aclset(acl)
        entry = Some((acl, sliceId))
      }
    }
    entry
  }


  /** Get the ith entry from a non-empty iterable collection */
  def getEntryAt(collection: Iterable[_ <: AnyRef], pos: Int): AnyRef = {
    require(collection.size > pos, s"pos out of bounds: ${pos} >= ${collection.size}")
    var entry: AnyRef = null
    var i: Int = 0
    breakable {
      for( e <- collection) {
        if(i == pos) {
          entry = e
          break
        }
        i += 1
      }
    }
    require(entry != null, s"null entry error")
    entry
  }

  /** Add an entry into a set */
  def addEntry(pset: OrderedSet[PrincipalStub], p: PrincipalStub): Unit = {
    pset.synchronized {
      pset += p
    }
  }

  def addEntry(m: MutableMap[String, MutableMap[PrincipalStub, Int]],
      key: String, p: PrincipalStub, delcount: Int) (implicit ignore: Int): Unit = {
    m.synchronized {
      val userset = m.get(key)
      if(userset.isDefined) {
        val uset = userset.get
        uset.put(p, delcount)      // at delegation count 0
      } else {
        val uset = MutableMap[PrincipalStub, Int]()
        uset.put(p, delcount)
        m.put(key, uset)
      }
    }
  }

  def addEntry(m: MutableMap[String, MutableMap[String, PrincipalStub]],
      key: String, s: String, cp: PrincipalStub) (implicit ignore: PrincipalStub): Unit = {
    m.synchronized {
      val sliverset = m.get(key)
      if(sliverset.isDefined) {
        val sset = sliverset.get
        sset.put(s, cp)      // at delegation count 0
      } else {
        val sset = MutableMap[String, PrincipalStub]()
        sset.put(s, cp)
        m.put(key, sset)
      }
    }
  }

  def addEntry(m: MutableMap[String, MutableMap[String, String]],
      key: String, acl: String, s: String) (implicit ignore: String): Unit = {
    m.synchronized {
      val aclset = m.get(key)
      if(aclset.isDefined) {
        val aset = aclset.get
        aset.put(acl, s)      // at delegation count 0
      } else {
        val aset = MutableMap[String, String]()
        aset.put(acl, s)
        m.put(key, aset)
      }
    }
  }

}
