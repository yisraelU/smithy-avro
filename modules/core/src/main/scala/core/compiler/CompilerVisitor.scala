package core.compiler

import coalmine.logicalTypes.TimeTrait
import core.compiler.Extractors.{getAliases, tnFromShapeId}
import core.schema.Schema
import core.schema.Schema.{AvroEnum, TypeName,AvroUnion}
import core.schema.Schema.AvroPrimitive._
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes._

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}
import scala.jdk.OptionConverters.RichOptional

object CompilerVisitor {

  private def compileVisitor(model: Model): ShapeVisitor[Option[Schema]] =
    new ShapeVisitor.Default[Option[Schema]] {

      override def getDefault(shape: Shape): Option[Schema] = None

      override def bigIntegerShape(shape: BigIntegerShape): Option[Schema] = {
        Some(AvroInt)
      }

      // big decimal is not supported in avro , fixing to double
      override def bigDecimalShape(shape: BigDecimalShape): Option[Schema] = {
        Some(AvroDouble)
      }

      override def timestampShape(shape: TimestampShape): Option[Schema] = {
        shape.getTrait(classOf[TimeTrait]).toScala match {
          case Some(timeTrait) =>
            timeTrait.getTimeType.name() match {
              case "date"                   => Some(AvroInt)
              case "time-millis"            => Some(AvroInt)
              case "time-micros"            => Some(AvroLong)
              case "timestamp-millis"       => Some(AvroLong)
              case "timestamp-micros"       => Some(AvroLong)
              case "local-timestamp-millis" => Some(AvroLong)
              case "local-timestamp-micros" => Some(AvroLong)
            }
          case None => Some(AvroLong)
        }
      }

      override def booleanShape(shape: BooleanShape): Option[Schema] = {
        Some(AvroBoolean)
      }

      override def blobShape(shape: BlobShape): Option[Schema] = {
        Some(AvroBytes)
      }

      override def integerShape(shape: IntegerShape): Option[Schema] = {
        Some(AvroInt)
      }

      override def longShape(shape: LongShape): Option[Schema] = {
        Some(AvroLong)
      }

      override def doubleShape(shape: DoubleShape): Option[Schema] = {
        Some(AvroDouble)
      }

      // 16 bit not supported by avro
      override def shortShape(shape: ShortShape): Option[Schema] = {
        Some(AvroInt)
      }

      override def floatShape(shape: FloatShape): Option[Schema] = {
        Some(AvroFloat)
      }

      override def documentShape(shape: DocumentShape): Option[Schema] = {
        ???
      }

      override def stringShape(shape: StringShape): Option[Schema] = {
        Some(AvroString)
      }

      // AvroEnum is limited to Strings only
      override def enumShape(shape: EnumShape): Option[Schema] = {
        val aliases = getAliases(shape)
        val typeName = tnFromShapeId(shape.getId)
        val symbols: List[String] =
          shape.getAllMembers.asScala.toList
            .map { case (name, _) =>
              name
            }
        Some(AvroEnum(typeName,symbols, aliases,None))
      }

      override def intEnumShape(shape: IntEnumShape): Option[Schema] = {
        val aliases = getAliases(shape)
        val typeName = tnFromShapeId(shape.getId)
        val symbols: List[String] =
          shape.getAllMembers.asScala.toList
            .map { case (name, _) =>
              name
            }
        Some(AvroEnum(typeName, symbols, aliases, None))
      }

      override def unionShape(shape: UnionShape): Option[Schema] = {
        shape.getAllMembers.asScala.toList.map { m =>
          val targetShape = model.getShape(m).get
          targetShape.accept(this).get
        }
        )

        Some(AvroUnion())
      }

      override def structureShape(shape: StructureShape): Option[Schema] = {
        val name = shape.getId.getName
        val messageElements =
          shape.members.asScala.toList
            // using foldLeft to accumulate the field count when we fork to
            // process a union
            .foldLeft((List.empty[MessageElement], 0)) {
              case ((fields, fieldCount), m) =>
                val fieldName = m.getMemberName
                val fieldIndex = findFieldIndex(m).getOrElse(fieldCount + 1)
                // We assume the model is well-formed so the result should be non-null
                val targetShape = model.getShape(m.getTarget).get
                targetShape
                  .asUnionShape()
                  .toScala
                  .filter(unionShape => unionShouldBeInlined(unionShape))
                  .map { union =>
                    val field = MessageElement.OneofElement(
                      processUnion(fieldName, union, fieldIndex)
                    )
                    (fields :+ field, fieldCount + field.oneof.fields.size)
                  }
                  .getOrElse {
                    val isDeprecated = m.hasTrait(classOf[DeprecatedTrait])
                    val isBoxed = isRequired(m) || isRequired(targetShape)
                    val numType = extractNumType(m)
                    val fieldType =
                      targetShape
                        .accept(typeVisitor(model, isBoxed, numType))
                        .get
                    val field = MessageElement.FieldElement(
                      Field(
                        deprecated = isDeprecated,
                        fieldType,
                        fieldName,
                        fieldIndex
                      )
                    )
                    (fields :+ field, fieldCount + 1)
                  }
            }
            ._1

        val reserved = getReservedValues(shape)
        val message = Message(name, messageElements, reserved)
        List(TopLevelDef.MessageDef(message))
      }

      private def processUnion(
          name: String,
          shape: UnionShape,
          indexStart: Int
      ): Oneof = {
        val fields = shape.members.asScala.toList.zipWithIndex.map {
          case (m, fn) =>
            val fieldName = m.getMemberName
            val fieldIndex = findFieldIndex(m).getOrElse(indexStart + fn)
            // We assume the model is well-formed so the result should be non-null
            val targetShape = model.getShape(m.getTarget).get
            val numType = extractNumType(m)
            val fieldType =
              targetShape
                .accept(typeVisitor(model, isRequired = true, numType))
                .get
            val isDeprecated = m.hasTrait(classOf[DeprecatedTrait])
            Field(
              deprecated = isDeprecated,
              fieldType,
              fieldName,
              fieldIndex
            )
        }
        Oneof(name, fields)
      }
    }
}
