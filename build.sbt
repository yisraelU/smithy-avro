ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

Global / onChangedBuildSource := ReloadOnSourceChanges

addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")

val modulePrefix = "smithy-avro"

lazy val root = (project in file("."))
  .settings(
    name := modulePrefix
  )
  .aggregate(core, traits, cli)

lazy val core = (project in file("modules/core"))
  .settings(
    name := s"$modulePrefix-core",
    libraryDependencies ++= Seq(
      Dependencies.avro,
      Dependencies.circeCore,
      Dependencies.munit,
      Dependencies.Smithy.build
    )
  )
  .dependsOn(traits)

lazy val traits = (project in file("modules/traits")).settings(
  name := s"$modulePrefix-traits",
  libraryDependencies ++= Seq(
    Dependencies.Smithy.model
  )
)

lazy val cli = (project in file("modules/cli"))
  .settings(
    name := s"$modulePrefix-cli",
    libraryDependencies ++= Seq(
      Dependencies.decline
    )
  )
  .dependsOn(core)

