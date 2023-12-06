package core.compiler

import coalmine.{AvroAlias, AvroOrderTrait}
import core.schema.AvroValue
import core.schema.Schema.AvroRecord.Order
import core.schema.Schema.{AvroAliases, TypeName}
import software.amazon.smithy.model.node.{
  ArrayNode,
  BooleanNode,
  Node,
  NullNode,
  NumberNode,
  ObjectNode,
  StringNode
}
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits.{DefaultTrait, DocumentationTrait}

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}
import scala.jdk.OptionConverters.RichOptional

object Extractors {
  def getAliases(shape: Shape): AvroAliases = {
    shape
      .getTrait(classOf[AvroAlias])
      .toScala
      .map {
        _.getAliases.asScala.toList
          .map(TypeName.parseFullName)
      }
      .map(AvroAliases(_))
      .getOrElse(AvroAliases.empty)
  }

  def getDocumentation(shape: Shape): Option[String] = {
    shape.getTrait(classOf[DocumentationTrait]).toScala.map(_.getValue)
  }
  def tnFromShapeId(shapeId: ShapeId): TypeName = {
    val name = shapeId.getName
    val namespace = shapeId.getNamespace.split("\\.").toList
    TypeName(namespace, name)
  }

  def getDefaultValue(shape: Shape): Option[AvroValue] = {
    val node = shape.getTrait(classOf[DefaultTrait]).toScala.map(_.toNode)
    def loop(shape: Shape, node: Node): AvroValue = {
      node match {
        case _: NullNode => AvroValue.NullV
        case node: ArrayNode =>
          AvroValue.ArrayV(node.getElements.asScala.map(loop(shape, _)).toList)
        case node: ObjectNode =>
          AvroValue.ObjectV(node.getMembers.asScala.map { case (key, value) =>
            (key.getValue, loop(shape, value))
          }.toMap)
        case node: StringNode  => AvroValue.StringV(node.getValue)
        case node: BooleanNode => AvroValue.BooleanV(node.getValue)
        case node: NumberNode  => extractNumber(shape)(node)
      }
    }

    node.map(loop(shape, _))
  }

  private def extractNumber(shape: Shape): NumberNode => AvroValue = {
    shape match {
      case _: IntegerShape => (node) => AvroValue.IntV(node.getValue.intValue())
      case _: DoubleShape =>
        (node) => AvroValue.DoubleV(node.getValue.doubleValue())
      case _: FloatShape =>
        (node) => AvroValue.FloatV(node.getValue.floatValue())
      case _: LongShape => (node) => AvroValue.LongV(node.getValue.longValue())
      case _: BigIntegerShape =>
        (node) => AvroValue.LongV(node.getValue.longValue())
      case _: BigDecimalShape =>
        (node) => AvroValue.DoubleV(node.getValue.doubleValue())

      case _ => (node) => AvroValue.LongV(node.getValue.longValue())
    }
  }

  def getAvroOrder(shape: Shape): Option[Order] = {
    shape
      .getTrait(classOf[AvroOrderTrait])
      .toScala
      .map(_.getOrder.name())
      .flatMap(Order.fromName)
  }
}
