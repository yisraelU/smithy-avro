package core.compiler

import coalmine.AvroTime
import coalmine.logicalTypes.TimeTrait
import core.schema.Schema
import core.schema.Schema._
import core.schema.Schema.AvroPrimitive._
import core.schema.Schema.LogicalType._
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits.TimestampFormatTrait

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.OptionConverters.RichOptional

object CompilerVisitor {

  private def compileVisitor(model: Model): ShapeVisitor[Option[Schema]] =
    new ShapeVisitor.Default[Option[Schema]] {

      override def getDefault(shape: Shape): Option[Schema] = None

      override def bigIntegerShape(shape: BigIntegerShape): Option[Schema] = {
        Some(AvroInt)
      }

      // big decimal is not supported in avro
      override def bigDecimalShape(shape: BigDecimalShape): Option[Schema] = {
        // todo revisit
        Some(AvroDouble)
      }

      override def timestampShape(shape: TimestampShape): Option[Schema] = {
        // todo process trait to determine which logical type to use
        shape.getTrait(classOf[TimeTrait]).toScala match {
          case Some(formatTrait) =>
            formatTrait.getValue match {
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

      private def getSchemaForTimeStamp(shape: TimestampShape): Schema = {
        shape.getTrait(classOf[AvroTime]).toScala match {
          case Some(formatTrait) =>
            formatTrait.getValue match {
              case "date"                   => Date
              case "time-millis"            => TimeMillis
              case "time-micros"            => TimeMicros
              case "timestamp-millis"       => TimestampMillis
              case "timestamp-micros"       => TimestampMicros
              case "local-timestamp-millis" => LocalTimestampMillis
              case "local-timestamp-micros" => LocalTimestampMicros
            }
          case None => TimestampMillis
        }
      }

      def isAvroService(shape: ServiceShape): Boolean = ???

      // TODO: streaming requests and response types
      override def serviceShape(shape: ServiceShape): Option[Schema] =
        // TODO: is this the best place to do the filtering? or should it be done in a preprocessing phase
        if (isAvroService(shape)) {
          val operations = shape.getOperations.asScala.toList
            .map(model.expectShape(_))

          val defs = operations.flatMap(_.accept(this))
          val rpcs = operations.flatMap(_.accept(rpcVisitor))
          val service = Service(shape.getId.getName, rpcs)

          List(TopLevelDef.ServiceDef(service)) ++ defs
        } else Nil

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

      override def enumShape(shape: EnumShape): Option[Schema] = {
        val reserved: List[Reserved] = getReservedValues(shape)
        val elements: List[EnumValue] =
          shape.getAllMembers.asScala.toList.zipWithIndex
            .map { case ((name, member), edFieldNumber) =>
              val fieldIndex = findFieldIndex(member).getOrElse(edFieldNumber)
              EnumValue(name, fieldIndex)
            }
        List(
          TopLevelDef.EnumDef(
            Enum(shape.getId.getName, elements, reserved)
          )
        )
      }

      override def intEnumShape(shape: IntEnumShape): Option[Schema] = {
        val reserved: List[Reserved] = getReservedValues(shape)
        val elements = shape.getEnumValues.asScala.toList.map {
          case (name, value) =>
            EnumValue(name, value)
        }
        List(
          TopLevelDef.EnumDef(
            Enum(shape.getId.getName, elements, reserved)
          )
        )
      }

      private def unionShouldBeInlined(shape: UnionShape): Boolean = {
        shape.hasTrait(classOf[alloy.proto.ProtoInlinedOneOfTrait])
      }

      override def unionShape(shape: UnionShape): Option[Schema] = {
        if (!unionShouldBeInlined(shape)) {
          val element =
            MessageElement.OneofElement(processUnion("definition", shape, 1))
          val name = shape.getId.getName
          val reserved = getReservedValues(shape)
          val message = Message(name, List(element), reserved)
          List(TopLevelDef.MessageDef(message))
        } else {
          List.empty
        }
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
