package core

package object schema {

  case class ByteString(value: Array[Byte]) extends AnyVal {
    def toByteArray: Array[Byte] = value
  }
  object ByteString {
    def copyFromUSAscii(value: String): ByteString = ByteString(
      value.getBytes("US-ASCII")
    )
  }

  def toByteString(value: String): ByteString =
    ByteString.copyFromUSAscii(value)
}
