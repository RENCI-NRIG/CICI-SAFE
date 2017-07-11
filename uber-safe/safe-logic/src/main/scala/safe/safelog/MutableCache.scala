package safe.safelog

import scala.collection.mutable.{HashMap => MutableHashMap}

// initialCapacity, i.e., 16 statements in a context, which is the default
// override the initialSize to a large value if the expected number of statements are large to avoid runtime hash resizing.
class MutableCache[K, V](initialSize: Int = Config.config.initialContextSize) extends MutableHashMap[K, V]

