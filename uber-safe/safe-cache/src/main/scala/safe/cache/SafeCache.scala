package safe.cache

//import java.util.concurrent.ConcurrentHashMap // TODO: prompt for ConcurrentHashMap or ConcurrentLinkedHashMap during class initialization
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

trait CacheLike[K, V] // TODO: to implement later?

class SafeTable[K, V](
    //initialCapacity: Int   = 1024 * 1024 // initialCapacity, i.e., 1024 statements in a context
    initialCapacity: Int   = 1024 * 10240 // initialCapacity, i.e., 1024 statements in a context
  , loadFactor: Float      = 0.99f       // loadFactor; we do not expect to rehash often
  , concurrencyLevel: Int  = 16          // concurrencyLevel; not many writers at the same time?
) extends CacheLike[K, V] {

  import scala.collection.JavaConversions._

  //private[this] val store = new ConcurrentHashMap[K, V](initialCapacity, loadFactor, concurrencyLevel)
  private[this] val store = new ConcurrentLinkedHashMap.Builder[K, V]
    .initialCapacity(initialCapacity)
    .maximumWeightedCapacity(initialCapacity)
    .concurrencyLevel(concurrencyLevel)
    .build()

  def get(key: K): Option[V] = Option(store.get(key))
  def put(key: K, value: V): V = store.put(key, value)
  def putIfAbsent(key: K, value: V): Option[V] = Option(store.putIfAbsent(key, value))
  def clear(): Boolean = {
    store.clear()
    true
  }
  def values(): java.util.Collection[V] = store.values()
  def containsKey(key: K): Boolean = store.containsKey(key)
  def keySet(): Set[K] = store.keySet().toSet
  def remove(key: K): V = store.remove(key)
  def remove(key: K, value: V): Boolean = store.remove(key, value)
  def replace(key: K, value: V): V = store.replace(key, value)
  def replace(key: K, oldvalue: V, newvalue: V): Boolean = store.replace(key, oldvalue, newvalue)
  def capacity(): Long = store.capacity()
  def isEmpty(): Boolean = store.isEmpty()
  def size(): Int = store.size()
}
