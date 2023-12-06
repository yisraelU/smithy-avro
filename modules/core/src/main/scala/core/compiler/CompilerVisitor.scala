package core.compiler

import coalmine.logicalTypes.AvroTimeTrait
import coalmine.{AvroDecimalTrait, ScaleTrait}
import core.compiler.Extractors.{getAliases, getAvroOrder, getDefaultValue, getDocumentation, tnFromShapeId}
import core.schema.Schema
import core.schema.Schema.{AvroArray, AvroEnum, AvroMap, AvroRecord, AvroUnion, LogicalType, TypeName}
import core.schema.Schema.AvroPrimitive._
import core.schema.Schema.AvroRecord.AvroField
import core.schema.Schema.LogicalType.Decimal
import core.schema.Schema.LogicalType.Decimal.UnderlyingType
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits.{DefaultTrait, DocumentationTrait}

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}
import scala.jdk.OptionConverters.RichOptional

object CompilerVisitor {

  def schemaVisitor(model: Model): ShapeVisitor[Schema] =
    new ShapeVisitor[Schema] {

      override def bigDecimalShape(shape: BigDecimalShape): Schema = {
        shape
          .getTrait(classOf[AvroDecimalTrait])
          .toScala
          .map { decimalTrait =>
            LogicalType.Decimal(
              decimalTrait.getPrecision,
              decimalTrait.getScale,
              UnderlyingType.fromName(decimalTrait.getUnderlyingType.name())
            )
          }
          .getOrElse(AvroDouble)
      }

      override def bigIntegerShape(shape: BigIntegerShape): Schema = {
        AvroLong
      }

      override def timestampShape(shape: TimestampShape): Schema = {
        shape
          .getTrait(classOf[AvroTimeTrait])
          .toScala
          .map { timeTrait =>
            {
              timeTrait.getTimeType.name() match {
                case "date"             => LogicalType.Date
                case "time-millis"      => LogicalType.TimeMillis
                case "time-micros"      => LogicalType.TimeMicros
                case "timestamp-millis" => LogicalType.TimestampMillis
                case "timestamp-micros" => LogicalType.TimestampMicros
                case "local-timestamp-millis" =>
                  LogicalType.LocalTimestampMillis
                case "local-timestamp-micros" =>
                  LogicalType.LocalTimestampMicros
              }
            }
          }
          .getOrElse(AvroLong)
      }

      override def booleanShape(shape: BooleanShape): Schema = {
        AvroBoolean
      }

      override def blobShape(shape: BlobShape): Schema = {
        AvroBytes
      }

      override def integerShape(shape: IntegerShape): Schema = {
        AvroInt
      }

      override def longShape(shape: LongShape): Schema = {
        AvroLong
      }

      override def doubleShape(shape: DoubleShape): Schema = {
        AvroDouble
      }

      // 16 bit not supported by avro
      override def shortShape(shape: ShortShape): Schema = {
        AvroInt
      }

      override def byteShape(shape: ByteShape): Schema = {
        AvroInt
      }

      override def floatShape(shape: FloatShape): Schema = {
        AvroFloat
      }

      override def documentShape(shape: DocumentShape): Schema = {
        AvroString
      }

      override def stringShape(shape: StringShape): Schema = {
        AvroString
      }

      // AvroEnum is limited to Strings only
      override def enumShape(shape: EnumShape): Schema = {
        val aliases = getAliases(shape)
        val typeName = tnFromShapeId(shape.getId)
        val symbols: List[String] =
          shape.getAllMembers.asScala.toList
            .map { case (name, _) =>
              name
            }
        AvroEnum(typeName, symbols, aliases, None)
      }

      override def intEnumShape(shape: IntEnumShape): Schema = {
        val aliases = getAliases(shape)
        val typeName = tnFromShapeId(shape.getId)
        val symbols: List[String] =
          shape.getAllMembers.asScala.toList
            .map { case (name, _) =>
              name
            }
        AvroEnum(typeName, symbols, aliases, None)
      }

      override def unionShape(shape: UnionShape): Schema = {
        // todo default value
        //  val defaultValue = shape.getTrait(classOf[DefaultTrait]).toScala.map(_.toNode)

        val schemas: List[Schema] = shape.getAllMembers.asScala.toList.map {
          case (_, member) =>
            val targetShape = model.getShape(member.getId).get
            targetShape.accept(this)
        }

        AvroUnion(schemas)
      }

      override def structureShape(shape: StructureShape): Schema = {
        val TypeName = tnFromShapeId(shape.getId)
        val aliases = getAliases(shape)
        val doc = getDocumentation(shape)
        val fields =
          shape.members.asScala.toList.map { memberShape: MemberShape =>
            val fieldName = memberShape.getMemberName
            val fieldType =
              model.getShape(memberShape.getTarget).get.accept(this)
            val aliases = getAliases(memberShape)
            val doc = getDocumentation(memberShape)
            val default = getDefaultValue(memberShape)
            val order = getAvroOrder(memberShape)
            AvroField(
              fieldName,
              fieldType,
              aliases,
              doc,
              default,
              order
            )
          }
        AvroRecord(TypeName, fields, aliases, doc)
      }

      override def listShape(shape: ListShape): Schema = {
        val TypeName = tnFromShapeId(shape.getId)
        val aliases = getAliases(shape)
        val doc = getDocumentation(shape)
        val default = getDefaultValue(shape)
        val elementType = model.getShape(shape.getMember.getTarget).get.accept(
          this
        )
        AvroArray(elementType,default)
      }

      override def mapShape(shape: MapShape): Schema = {
        val default = getDefaultValue(shape)
        val valueType = model.getShape(shape.getValue.getTarget).get.accept(
          this
        )
        AvroMap(valueType, default)
      }

      override def operationShape(shape: OperationShape): Schema = ???

      override def resourceShape(shape: ResourceShape): Schema = ???

      override def serviceShape(shape: ServiceShape): Schema = ???

      override def memberShape(shape: MemberShape): Schema = ???
    }
}
