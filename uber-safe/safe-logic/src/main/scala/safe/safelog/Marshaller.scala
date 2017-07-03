package safe.safelog

import java.nio.Buffer

trait Marshaller {
  type A
  def marshall(value: A, buffer: Buffer): Unit
  def unmarshall(buffer: Buffer): A
}

