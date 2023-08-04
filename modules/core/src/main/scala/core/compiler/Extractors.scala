package core.compiler

import coalmine.AvroAlias
import core.schema.AvroValue
import core.schema.Schema.{AvroAliases, TypeName}
import software.amazon.smithy.model.node.{NullNode, NumberNode}
import software.amazon.smithy.model.shapes._

import scala.jdk.CollectionConverters.CollectionHasAsScala
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

  def tnFromShapeId(shapeId: ShapeId):TypeName = {
    val name = shapeId.getName
    val namespace = shapeId.getNamespace.split("\\.").toList
    TypeName(namespace, name)
  }

  def getDefaultValue(shape: Shape): Option[AvroValue] = {
    shape
      .getTrait(classOf[coalmine.AvroDefault])
      .toScala
    .map(_.toNode).map{
      case node: NullNode => AvroValue.NullV
      //todo we need to determine which numerical value to return based upon the target shape
      case node: NumberNode =>  extractNumber(shape)(node)
    }
  }

  private def extractNumber(shape: Shape):NumberNode => AvroValue = {
    shape match {
      case _:IntegerShape => (node) => AvroValue.IntV(node.getValue.intValue())
      case _:DoubleShape => (node) => AvroValue.DoubleV(node.getValue.doubleValue())
      case _:FloatShape => (node) => AvroValue.FloatV(node.getValue.floatValue())
      case _:LongShape => (node) => AvroValue.LongV(node.getValue.longValue())
      case _:BigIntegerShape => (node) => AvroValue.LongV(node.getValue.longValue())
      case _:BigDecimalShape => (node) => AvroValue.DoubleV(node.getValue.doubleValue())

      case _ => (node) => AvroValue.LongV(node.getValue.longValue())
    }
  }
}
