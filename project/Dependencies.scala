import sbt._

object Dependencies {

  val avro = "org.apache.avro" % "avro" % "1.11.0"
  val circeCore = "io.circe" %% "circe-core" % "0.14.1"
  val munit = "org.scalameta" %% "munit" % "0.7.29" % Test
  val decline = "com.monovore" %% "decline" % "2.4.1"
  object Smithy {
    val version = "1.31.0"
    val model = "software.amazon.smithy" % "smithy-model" % version
    val build = "software.amazon.smithy" % "smithy-build" % version
  }

}
