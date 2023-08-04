package core.schema

sealed trait AvroValue

object AvroValue {
  case object NullV extends AvroValue
  case class BooleanV(value: Boolean) extends AvroValue
  case class IntV(value: Int) extends AvroValue
  case class LongV(value: Long) extends AvroValue
  case class FloatV(value: Float) extends AvroValue
  case class DoubleV(value: Double) extends AvroValue
  case class StringV(value: String) extends AvroValue
  case class UniCodePointStr(value: String) extends AvroValue
  case class ArrayV(value: List[AvroValue]) extends AvroValue

  case class ObjectV(value: Map[String, AvroValue]) extends AvroValue
}
