package core.compiler.transitive

import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.build.transforms.FilterSuppressions
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer

import scala.jdk.CollectionConverters._

// In order to have nice comparisons from munit reports.
class ModelWrapper(val model: Model) {
  override def equals(obj: Any): Boolean = obj match {
    case wrapper: ModelWrapper =>
      model == wrapper.model
    case _ => false
  }

  private def filter(model: Model): Model = {
    val filterSuppressions: Model => Model = m =>
      new FilterSuppressions().transform(
        TransformContext
          .builder()
          .model(m)
          .settings(
            ObjectNode.builder().withMember("removeUnused", true).build()
          )
          .build()
      )
    (filterSuppressions)(model)
  }

  override def toString() =
    SmithyIdlModelSerializer
      .builder()
      .build()
      .serialize(filter(model))
      .asScala
      .map(in => s"${in._1.toString.toUpperCase}:\n\n${in._2}")
      .mkString("\n")
}

object ModelWrapper {
  def apply(model: Model): ModelWrapper =
    new ModelWrapper(model)
}
