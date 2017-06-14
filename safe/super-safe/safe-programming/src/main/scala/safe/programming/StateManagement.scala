package safe.programming

import scala.util.control.Breaks._
import scala.collection.mutable.{Map => MutableMap, LinkedHashSet => OrderedSet}
import java.util.concurrent.ThreadLocalRandom
import com.typesafe.scalalogging.LazyLogging

/** Helpers for sets used in FSM */
trait StateManagementHelper extends LazyLogging {
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
   * Get a random entry from a map of maps. First randomly pick up a key. If the key
   * is mappped to a set of values (as in a map), randomly pick up a map key and its
   * corresponding value from that map.
   */
  def getRandomEntry[T1, T2, T3](m: MutableMap[T1, MutableMap[T2, T3]]):
      Option[Tuple3[T1, T2, T3]] = {
    var entry: Option[Tuple3[T1, T2, T3]] = None
    m.synchronized {
      val rdm = ThreadLocalRandom.current()
      val keyset = m.keySet
      if(keyset.size != 0) {
        val k_pos = rdm.nextInt(keyset.size)
        val key = getEntryAt(keyset, k_pos).asInstanceOf[T1]
        val pset = m(key).keySet // m must contain key 
        if(pset.size != 0) {
          val p_pos = rdm.nextInt(pset.size)
          val p = getEntryAt(pset, p_pos).asInstanceOf[T2]
          val v = m(key)(p)
          entry = Some((key, p, v))
        }
      }
    }
    entry
  }

  /** Get a random entry from a map, for selecting sliver and cp pair */
  implicit val simpleMap: Boolean = true
  def getRandomEntry[T1, T2](m: MutableMap[T1, T2]) (implicit b: Boolean): 
      Option[Tuple2[T1, T2]] = {
    var entry: Option[Tuple2[T1, T2]] = None
    m.synchronized {
      val keyset = m.keySet
      if(keyset.size != 0) {
        val pos = ThreadLocalRandom.current().nextInt(keyset.size)
        logger.info(s"[Genibench getRandomEntry] keyset.size:${keyset.size}   pos:${pos}")
        val k = getEntryAt(keyset, pos).asInstanceOf[T1]
        val v = m(k)
        entry = Some((k, v))
      }
    }
    entry
  }


  /** Get the ith entry from a non-empty iterable collection */
  def getEntryAt[T >: Null](collection: Iterable[T], pos: Int): T = {
    require(collection.size > pos, s"pos out of bounds: ${pos} >= ${collection.size}")
    var entry: T = null
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

  def addEntry[T1, T2, T3](m: MutableMap[T1, MutableMap[T2, T3]],
      mkey: T1, k: T2, v: T3): Unit = {
    m.synchronized {
      val kvmap = m.get(mkey)
      if(kvmap.isDefined) {
        val map = kvmap.get
        map.put(k, v) 
      } else {
        val map = MutableMap[T2, T3]()
        map.put(k, v)
        m.put(mkey, map)
      }
    }
  }

}
