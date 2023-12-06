package core.compiler

case class CompilationUnit(ns: Option[String], imports: Seq[core.schema.Schema])
