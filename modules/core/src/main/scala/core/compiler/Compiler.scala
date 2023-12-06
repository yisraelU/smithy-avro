package core.compiler

import core.compiler.CompilerVisitor.schemaVisitor
import core.compiler.transitive.ModelOps.ModelOps
import core.schema.Schema
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.Prelude
import software.amazon.smithy.model.shapes.{Shape, ShapeId}
import software.amazon.smithy.model.traits.TraitDefinition

object Compiler {

  object ShapeFiltering {

    private val passthroughShapeIds: Set[ShapeId] =
      Set(
        "BigInteger",
        "BigDecimal",
        "Timestamp",
        "UUID"
      ).map(name => ShapeId.fromParts("coalmine", name))

    private def excludeInternal(shape: Shape): Boolean = {
      val excludeNs = Set("alloy.proto", "alloy", "smithytranslate", "coalmine")
      excludeNs.contains(shape.getId().getNamespace()) &&
      !passthroughShapeIds(shape.getId())
    }

    def traitShapes(s: Shape): Boolean = {
      s.hasTrait(classOf[TraitDefinition])
    }

    def exclude(s: Shape): Boolean =
      excludeInternal(s) || Prelude.isPreludeShape(s) || traitShapes(s)
  }

  def compile(model: Model): List[OutputFile] = {

    model.toShapeSet.toList
      .filterNot(ShapeFiltering.exclude)
      .groupBy(_.getId().getNamespace())
      .flatMap { case (ns, shapes) =>
        val mappings: Seq[Schema] = shapes.map { shape =>
          shape
            .accept(schemaVisitor(model))
        }
        if (mappings.nonEmpty) {
          val unit = CompilationUnit(Some(ns), mappings)
          List(OutputFile(???, unit))
        } else Nil
      }
      .toList

  }
}
