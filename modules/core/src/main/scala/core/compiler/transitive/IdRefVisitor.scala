package core.compiler.transitive

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits.{IdRefTrait, Trait}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

final class IdRefVisitor(
    model: Model,
    value: Node,
    captureTraits: Boolean,
    isInsideIdRefMember: Boolean = false,
    visitedShapes: mutable.Set[Shape]
) extends ShapeVisitor.Default[List[Shape]] {
  override def getDefault(_shape: Shape): List[Shape] = List.empty

  private def visitSeqShape(member: MemberShape): List[Shape] =
    value.asArrayNode().toScala match {
      case None => List.empty
      case Some(value) =>
        value
          .getElements()
          .asScala
          .toList
          .flatMap { value =>
            member.accept(
              new IdRefVisitor(
                model = model,
                value = value,
                captureTraits = captureTraits,
                isInsideIdRefMember = false,
                visitedShapes = visitedShapes
              )
            )
          }
    }

  override def listShape(shape: ListShape): List[Shape] =
    visitSeqShape(shape.getMember())

  override def mapShape(shape: MapShape): List[Shape] =
    visitNamedMembersShape(shape.getAllMembers.asScala.toMap)

  override def stringShape(shape: StringShape): List[Shape] = {
    if (isInsideIdRefMember || shape.hasTrait(classOf[IdRefTrait])) {
      value.asStringNode().toScala match {
        case None => List.empty
        case Some(stringNode) =>
          val shape = model.expectShape(ShapeId.from(stringNode.getValue))
          val stringNodeShapes = if (visitedShapes.contains(shape)) {
            Nil
          } else {
            TransitiveModel
              .computeWithVisited(
                model = model,
                entryPoints = List(shape.getId()),
                captureTraits = captureTraits,
                visitedShapes = visitedShapes
              )
          }
          stringNodeShapes ++ List(shape)
      }
    } else {
      List.empty
    }
  }

  private def visitNamedMembersShape(
      members: Map[String, MemberShape]
  ): List[Shape] =
    value.asObjectNode().toScala.toList.flatMap { obj =>
      val entries: Map[String, Node] = obj.getStringMap().asScala.toMap
      entries.flatMap { case (name, node) =>
        members.get(name) match {
          case None => List.empty
          case Some(member) =>
            member.accept(
              new IdRefVisitor(
                model = model,
                value = node,
                captureTraits = captureTraits,
                isInsideIdRefMember = false,
                visitedShapes = visitedShapes
              )
            )
        }
      }
    }

  override def structureShape(shape: StructureShape): List[Shape] =
    visitNamedMembersShape(shape.getAllMembers().asScala.toMap)

  override def unionShape(shape: UnionShape): List[Shape] =
    visitNamedMembersShape(shape.getAllMembers().asScala.toMap)

  override def memberShape(shape: MemberShape): List[Shape] = {
    val newVisitor = new IdRefVisitor(
      model = model,
      value = value,
      captureTraits = captureTraits,
      isInsideIdRefMember = shape.hasTrait(classOf[IdRefTrait]),
      // IdRefs have a selector of :test(string, member > string)
      // so we need to check for the trait in both of those places
      visitedShapes = visitedShapes
    )
    model
      .getShape(shape.getTarget())
      .toScala
      .toList
      .flatMap(_.accept(newVisitor))
  }
}

object IdRefVisitor {
  def visit(
      model: Model,
      captureTraits: Boolean,
      trt: Trait,
      visitedShapes: mutable.Set[Shape]
  ): List[Shape] = {
    model
      .getShape(trt.toShapeId())
      .toScala
      .toList
      .flatMap { s0 =>
        s0.accept(
          new IdRefVisitor(
            model = model,
            value = trt.toNode,
            captureTraits = captureTraits,
            isInsideIdRefMember = false,
            visitedShapes = visitedShapes
          )
        )
      }
  }
}
