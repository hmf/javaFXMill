import os.Path

/**
 * Use this to experiment with code to add to the `build.sc` file.
 * Run this as an Ammomite script.
 */

//import $ivy.`org.scalameta::mdoc:1.3.1`
//import $ivy.`org.scalameta:::mdoc:1.3.1`
import $ivy.`org.scalameta:mdoc_2.12:1.3.1`
import mdoc._


// https://stackoverflow.com/questions/16329566/why-does-sbt-build-fail-with-missingrequirementerror-object-scala-runtime-in-c
// https://stackoverflow.com/questions/18150961/scala-runtime-in-compiler-mirror-not-found-but-working-when-started-with-xboo/18152249

import $ivy.`org.scala-lang:scala-library:2.12.8`
import $ivy.`org.scala-lang:scala-reflect:2.12.8`
import $ivy.`org.scala-lang:scala-compiler:2.12.8`


val sources = os.pwd / "sdk" / "docs" / "mdoc"
val sink = os.pwd / "out" / "sdk" / "mDoc" / "dest"
println(s"sources = $sources")
println(s"sink = $sink")

val args = Array("--verbose",
  "--in", sources.toString,
  "-out", sink.toString)
//val workingDirectory = sources.head.path.toNIO
val settings = mdoc.MainSettings()
  .withSiteVariables(Map("VERSION" -> "1.0.0"))
  //.withIn(workingDirectory)
  //.withOut(T.ctx().dest.toNIO)
  .withArgs(args.toList) // for CLI only
// generate out/readme.md from working directory
val exitCode = mdoc.Main.process(settings)
// (optional) exit the main function with exit code 0 (success) or 1 (error)
if (exitCode != 0) println(s"error = $exitCode")
