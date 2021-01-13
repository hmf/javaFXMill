import java.io.File
import java.nio.file.attribute.PosixFilePermission

import mill._
import mill.scalalib.publish._
import scalalib._
import coursier.MavenRepository
import mill.define.{Command, Input, Sources, Target}
import os.{Path, PermSet, ProcessOutput}

import scala.util.matching.Regex
import $ivy.`org.typelevel::cats-core:2.0.0`
import $ivy.`org.typelevel::cats-effect:2.0.0`
import cats.effect.{Blocker, ContextShift, IO}
import mill.api.Loose

// Link hygiene: Problem with false positives
// https://github.com/scalameta/mdoc/issues/94
// https://github.com/scalameta/mdoc/blob/master/blog/2019-01-01-v1.1.1.md
import $ivy.`org.scalameta::mdoc:2.0.3`              // works but IDE gets confused, use below then back to this
//import $ivy.`org.scalameta:mdoc_2.12:2.0.3`        // use this, alt-enter, create jars, fix all Ammonite libraries
//import $ivy.`org.scalameta:::mdoc:2.0.3`           // does not work

import $ivy.`org.planet42::laika-io:0.14.0`
//import $ivy.`org.planet42::laika-core:0.14.0`
//import $ivy.`org.planet42:laika-core_2.12:0.14.0`
import laika.factory.{RenderFormat, TwoPhaseRenderFormat}
import laika.io.binary
import laika.io.text.ParallelRenderer
import laika.api.Transformer._
import laika.ast.MessageLevel
import laika.markdown.github.GitHubFlavor
import laika.io.model.RenderedTreeRoot
import laika.api.{MarkupParser, Renderer, Transformer}

import $ivy.`org.planet42::laika-pdf:0.14.0`
//import $ivy.`org.planet42:laika-pdf_2.12:0.14.0`
import laika.format.{Markdown, HTML, ReStructuredText, EPUB, PDF, XSLFO, AST}

import laika.io.implicits._
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.2.1`
import de.tobiasroeser.mill.integrationtest._


implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

val blocker = Blocker.liftExecutionContext(
  ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
)

//import cats.Parallel._
//import cats.implicits._

//import $file.`mdocs.PlotlyModifier.scala`
// https://github.com/lihaoyi/Ammonite/issues/315
// https://github.com/lihaoyi/Ammonite/issues/317

/* Global configuration */

val gScalaMillVersion = "2.12.10"

// Because of mill plug-ins
//val gScalaVersion = "2.13.1"
val gScalaVersion = "2.12.11"
val gMdocVersion = "2.1.5"
val gMdocVerbose = 1
val gLaikaVersion = "0.14.0"
val gLaikaVerbose = 1

val millVersion = mill.BuildInfo.millVersion // "0.5.2"
val utestVersion = "0.7.2"
val osLibVersion = "0.6.2"
val tableSawVersion = "0.36.0"
val plotlyScalaVersion = "0.7.3"  // IMPORTANT: this must be the same version as ivy for mdoc.PlotlyModifier
val betterFilesVersion = "3.8.0"
val graalvmVersion = "19.3.0"
val ujsonVersion = "0.8.0"
val json4sVersion = "3.7.0-M2"
val scalafmtVersion = "2.4.2"

// We need these for the mdoc.PlotlyModifier
import $ivy.`com.github.pathikrit::better-files:3.8.0`
import $ivy.`org.plotly-scala::plotly-render:0.7.3`  // IMPORTANT: this must be the same version as plotlyScalaVersion

//import $file.mdocs.src.utils.HeadlessWebKit
//interp.load.module(Path(os.pwd + "/mdocs/src/utils/HeadlessWebKit.scala"))
//import HeadlessWebKit

object Utils {

  /**
   * Combines 2 sets of module paths used as JVM arguments. This is used to ensure that
   * the JVM loads the required libraries via the JVM's parameter
   *
   * @param args1 first list of required modules
   * @param args2 second list of required modules
   * @return
   */
  def jvmModuleArgs(args1: Seq[String], args2: Seq[String]): Seq[String] = {
    val MODULE_PATH = "--module-path="
    val (module_path1, rest1) = args1.partition(_.replaceAll(" +", "").trim.toLowerCase.contains(MODULE_PATH))
    val (module_path2, rest2) = args2.partition(_.replaceAll(" +", "").trim.toLowerCase.contains(MODULE_PATH))
    if (module_path1.length > 1) throw new RuntimeException(s"args1:$args1 must only have one $MODULE_PATH")
    if (module_path2.length > 1) throw new RuntimeException(s"args2:$args2 must only have one $MODULE_PATH")
    //println(module_path1.head.drop(MODULE_PATH.length))
    //println(module_path2.head.drop(MODULE_PATH.length))
    val module_path = "--module-path=" +
      module_path1.head.drop(MODULE_PATH.length) +
      File.pathSeparator +
      module_path2.head.drop(MODULE_PATH.length)
    Seq(module_path) ++ rest1 ++ rest2
  }

}


trait OpenFXModules extends JavaModule {
  val monocleVersion = "jdk-11+26"
  val jfxVersion = "11.0.2"

  // Add JavaFX dependencies
  // https://github.com/scalafx/scalafx-hello-world/blob/master/build.sbt
  private lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  private lazy val jfxMonocle = ivy"org.testfx:openjfx-monocle:$monocleVersion"
  private lazy val javaFX = javaFXModules.map(m =>
    ivy"org.openjfx:javafx-$m:$jfxVersion"
    //ivy"org.openjfx:javafx-$m:11.0.2"
    //ivy"org.openjfx:javafx-$m:12.0.2"
    //ivy"org.openjfx:javafx-$m:13"
  ) ++ Seq(jfxMonocle)

  override def ivyDeps = T{ Agg(javaFX:_*) }

  def monocleToolsNoDeps: T[Agg[Dep]] = T {
    Agg(jfxMonocle.exclude(("*","*")))
  }

  def monocleLibClasspath: T[Agg[PathRef]] = T {
    resolveDeps(monocleToolsNoDeps)
  }

  // We use a T.input soi that no caching is used
  def openFXArgs: Input[Seq[String]] = T.input {
    // Get just the Monocle jar (no other dependencies)
    //val monocleDeps = monocleLibClasspath().map(_.path.toIO.getAbsolutePath).filter(_.toLowerCase.contains("monocle"))
    val monocleDeps = monocleLibClasspath().map(_.path.toIO.getAbsolutePath).toList.mkString(File.separator)
    // Get the Monocle library path
    val monocle = monocleLibClasspath().map(_.path.toNIO.getParent.toAbsolutePath).toList.head
    // Full module package names
    val fxModules = javaFXModules.map("javafx."+_)
    val monoclePackage = "org.testfx.monocle"
    val modules = (Seq(monoclePackage) ++ fxModules).mkString(",")
    Seq(
      // Monocle must also be included here
      s"--module-path=$monocleDeps:/usr/share/openjfx/lib", // TODO: auto create
      // javafx.graphics, not required
      s"--add-modules=$modules",
      // For javafx Monocle use
      s"--patch-module=javafx.graphics=$monocle",
      s"--add-exports=javafx.graphics/com.sun.glass.ui=$monoclePackage",
      //"--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
      "--add-reads=javafx.graphics=ALL-UNNAMED",
      "--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
      "--add-opens=javafx.controls/com.sun.javafx.charts=ALL-UNNAMED",
      "--add-opens=javafx.graphics/com.sun.javafx.iio=ALL-UNNAMED",
      "--add-opens=javafx.graphics/com.sun.javafx.iio.common=ALL-UNNAMED",
      "--add-opens=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED",
      "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
      "--add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED"
    )
  }

  override def forkArgs: Target[Seq[String]] = T { openFXArgs() }

}


trait GraalModules extends JavaModule {

  var useGraal = true

  override def ivyDeps = T {
    Agg(
      ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion", // https://github.com/graalvm/graal-js-jdk11-maven-demo
      ivy"org.graalvm.js:js:$graalvmVersion", // https://github.com/hmf/graal-js-jdk11-maven-demo
      ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
      ivy"org.graalvm.tools:profiler:$graalvmVersion",
      ivy"org.graalvm.tools:chromeinspector:$graalvmVersion",
      ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion" // graal-sdk.jar, NOT required - just for IDE
    )
  }

  def graalToolsDeps: T[Agg[Dep]] = T {
    Agg(
      ivy"org.graalvm.compiler:compiler:$graalvmVersion",    // compiler.jar
      ivy"org.graalvm.truffle:truffle-api:$graalvmVersion",  // truffle-api.jar
      ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion"         // graal-sdk.jar
    )
  }

  def graalToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(graalToolsDeps)
  }

  /**
   * Sets the correct JVM parameters in order to enable or disable the GraalVM
   * modules, including the compiler. Note that this value cannot be cached
   * because it is always reset by the `run`, `runBackground`,
   * `runMainBackground` and `runMain` commands. We must either use a `T.task`
   * or `T.input`. We opted for a `T.input` because, unlike the task, it is
   * not cached in a cached target and is accessible as Mill command.
   *
   * We use a T.input soi that no caching is used.
   *
   * @see https://github.com/TestFX/TestFX
   *      https://github.com/TestFX/Monocle
   * @return
   */
  def graalArgs: Input[Seq[String]] = T.input {
    // Path to Graal JDK tools
    val graalDeps = graalToolsClasspath().map(_.path.toIO.getAbsolutePath)
    val libPath = graalDeps.mkString(java.io.File.pathSeparator)
    val baseModuleConfig =
      Seq(
        // For Graal use
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        s"--module-path=$libPath"
      )

    if (useGraal) {
      val compiler = graalDeps.filter( _.matches(".+compiler.+\\.jar")).seq.toSeq.head
      Seq(s"--upgrade-module-path=$compiler") ++ baseModuleConfig
    } else {
      baseModuleConfig
    }
  }

  override def forkArgs: Target[Seq[String]] = T { graalArgs() }

  val NOGRAAL = "nograal"

  /**
   * The GraavVM is used by default. It may be deactivated by passing the
   * "noGraal" parameter to the run and test commands. These fork arguments
   * are also used by the test module. However if we pass an invalid flag
   * to the test framework such as JUnit, it will fail silently. So we
   * remove the Graal flag so that the test can execute correctly.
   *
   * @param args command arguments to run and test
   */
  def setForkArgs(args: Seq[String]): Seq[String] = {
    useGraal = true
    // Index the lowercase version of the arguments
    val trimmedArgs = args.zipWithIndex.map(e => (e._1.trim.toLowerCase, e._2))
    // Check if the flag has been set
    val idx = trimmedArgs.find( _._1.equals(NOGRAAL))
    // If not set leave the args as is
    idx.fold(args)( { e =>
      useGraal = false
      // get the original non-lowercase arguments
      val name = args(e._2)
      // If set, then remove it
      args.filterNot(_.equals(name))
    } )
  }
  override def run(args: String*): Command[Unit] = T.command {
    super.run( setForkArgs(args):_* )
  }

  override def runBackground(args: String*): Command[Unit] = T.command {
    super.runBackground( setForkArgs(args):_* )
  }

  override def runMainBackground(mainClass: String, args: String*): Command[Unit] = T.command {
    super.runMainBackground(mainClass, setForkArgs(args):_*)
  }

  override def runMain(mainClass: String, args: String*): Command[Unit] = T.command {
    super.runMain(mainClass, setForkArgs(args):_*)
  }

}


object webkit extends ScalaModule with OpenFXModules with PublishModule {
  override def scalaVersion: Target[String] = T{ gScalaVersion }

  override def scalacOptions = T{ Seq("-deprecation", "-feature") }

  override def forkArgs: Target[Seq[String]] = T {
    Seq("-Dprism.verbose = true", "-ea") ++ // JavaFX
      super[OpenFXModules].forkArgs() //  OpenFX
  }

  override def forkEnv: Target[Map[String, String]] =
    T{ Map("prism.verbose" -> "true") } // -Dprism.verbose = true, // JavaFX

  override def ivyDeps = T {
    Agg(
      ivy"com.lihaoyi::os-lib:$osLibVersion", // https://github.com/lihaoyi/os-lib
      ivy"com.github.pathikrit::better-files:$betterFilesVersion"
    ) ++
      super[OpenFXModules].ivyDeps()
  }

  def publishVersion = "0.1.0"

  def pomSettings = T {
    PomSettings(
      description = "Java WebKitView module used for image capture",
      organization = "com.gitlab.hmf",
      url = "https://gitlab.com/hmf/srllab",
      licenses = Seq(License.`MIT`),
      versionControl = VersionControl.github("hmf", "srllab"),
      developers = Seq(Developer("hmf", "Hugo Ferreira", "https://gitlab.com/hmf"))
    )
  }

  object test extends Tests {
    override def ivyDeps = T{ Agg(ivy"com.lihaoyi::utest::$utestVersion") }

    //def testFrameworks: Target[Seq[String]] = T{ Seq("utest.runner.Framework") }
    override def testFrameworks: Target[Seq[String]] = T{ Seq("webkit.CustomFramework") }

    override def forkArgs: Target[Seq[String]] = T{ plots.forkArgs() }
    override def forkEnv: Target[Map[String, String]] = T{ plots.forkEnv() }
  }

}


object plots extends ScalaModule with OpenFXModules with PublishModule {
  override def moduleDeps = Seq(webkit)

  override def scalaVersion: Target[String] = T{ gScalaVersion }

  override def scalacOptions = T{ Seq("-deprecation", "-feature") }

  /**
   * Note that the GraalVM recalculate the arguments because the run and test
   * commands may change them.
   *
   * @return
   */
  override def forkArgs: Target[Seq[String]] = T {
    Seq("-Dprism.verbose = true", "-ea") ++ // JavaFX
      super[OpenFXModules].forkArgs() //  OpenFX
  }

  override def forkEnv: Target[Map[String, String]] =
    T{ Map("prism.verbose" -> "true") } // -Dprism.verbose = true, // JavaFX

  override def ivyDeps = T {
    Agg(
      ivy"com.lihaoyi::os-lib:$osLibVersion", // https://github.com/lihaoyi/os-lib
      ivy"com.lihaoyi::ujson:$ujsonVersion", // https://github.com/lihaoyi/upickle
      ivy"com.lihaoyi::upickle:$ujsonVersion",
      ivy"com.lihaoyi::ujson-argonaut:$ujsonVersion",
      ivy"com.lihaoyi::ujson-circe:$ujsonVersion",
      ivy"com.lihaoyi::ujson-play:$ujsonVersion",
      ivy"com.lihaoyi::ujson-json4s:$ujsonVersion",
      ivy"com.lihaoyi::upack:$ujsonVersion",
      ivy"com.lihaoyi::utest:$utestVersion",                 // share test utilities
      ivy"org.json4s::json4s-jackson:$json4sVersion",        // share test utiities
      ivy"org.scalameta::scalafmt-dynamic:$scalafmtVersion"
    ) ++
    super[OpenFXModules].ivyDeps()
  }

  def publishVersion = "0.1.0"

  def pomSettings = T {
    PomSettings(
      description = "Plotly.js wrapper module",
      organization = "com.gitlab.hmf",
      url = "https://gitlab.com/hmf/srllab",
      licenses = Seq(License.`MIT`),
      versionControl = VersionControl.github("hmf", "srllab"),
      developers = Seq(Developer("hmf", "Hugo Ferreira", "https://gitlab.com/hmf"))
    )
  }

  object test extends Tests {
    //override def moduleDeps = super.moduleDeps ++ Seq(webkit)

    override def ivyDeps = T {
      Agg(
        ivy"com.lihaoyi::utest:$utestVersion",
        ivy"org.json4s::json4s-jackson:$json4sVersion"
      )
    } // ++ plots.ivyDeps() }
    //def testFrameworks: Target[Seq[String]] = T{ Seq("utest.runner.Framework") }
    override def testFrameworks: Target[Seq[String]] = T{ Seq("splotly.CustomFramework") }

    override def forkArgs: Target[Seq[String]] = T{ plots.forkArgs() }
    override def forkEnv: Target[Map[String, String]] = T{ plots.forkEnv() }
  }

}

// TODO: add comments
/**
 * https://github.com/oracle/graal/issues/651
 * https://medium.com/graalvm/graalvms-javascript-engine-on-jdk11-with-high-performance-3e79f968a819
 * https://github.com/graalvm/graal-js-jdk11-maven-demo
 *
 * mill mill.scalalib.GenIdea/idea
 *
 * mill mdocs.compile
 * mill mdocs.run
 * mill mdocs.{compile, run}
 * mill --watch mdocs.run
 *
 * mill -i mdocs.console
 * mill -i mdocs.repl
 *
 * mill -i show mdocs.graalArgs
 * mill -i sdk.runMain mdocs.TestNashorn
 * mill --watch sdk.runMain mdocs.TestNashorn
 *
 * mill -i sdk.runMain mdocs.TestGraalJS
 * mill --watch sdk.runMain mdocs.TestGraalJS
 *
 * mill -i mdocs.runMain mdocs.TestNashorn
 * mill --watch mdocs.runMain mdocs.TestNashorn
 *
 * mill -i mdocs.runMain mdocs.TestGraalJS
 * mill --watch mdocs.runMain mdocs.TestGraalJS
 *
 * mill -i sdk.laika
 * Currently does not support GraavVM
 * mill -i sdk.laikaLocal
 * mill -i sdk.mDoc
 * Currently does not support GraavVM
 * mill -i sdk.mDocLocal
 *
 * mill -i mdocs.test
 * mill -i mdocs.test.testLocal
 * mill -i mdocs.test HeadlessWebKitSpec
 * mill -i mdocs.test mdocs.HeadlessWebKitSpec.testGraalPolyglotSpeed
 *
 * ./jfxmill.sh -i mdocs.test mdocs.HeadlessWebKitSpec
 * ./jfxmill.sh -i sdk.mDoc
 * ./jfxmill.sh -i sdk.mDocLocal
 *
 */
object markdoc extends ScalaModule with GraalModules with OpenFXModules with PublishModule  {
  override def moduleDeps = Seq(plots)

  override def scalaVersion: Target[String] = T{ gScalaVersion }

  override def scalacOptions = T{ Seq("-deprecation", "-feature") }

  /**
   * Note that the GraalVM recalculate the arguments because the run and test
   * commands may change them.
   *
   * @return
   */
  override def forkArgs: Target[Seq[String]] = T {
      Seq("-Dprism.verbose = true", "-ea") ++ // JavaFX
      Utils.jvmModuleArgs(super[GraalModules].forkArgs(), super[OpenFXModules].forkArgs()) // GraavVM + OpenFX
  }

  override def forkEnv: Target[Map[String, String]] =
    T{ Map("prism.verbose" -> "true") } // -Dprism.verbose = true, // JavaFX

  def destinationDirectory: T[os.Path] = T { T.ctx().dest }

  override def ivyDeps = T {
    Agg(
      ivy"org.scalameta::mdoc:$gMdocVersion", // https://scalameta.org/mdoc/
      ivy"com.github.pathikrit::better-files:$betterFilesVersion",
      ivy"com.lihaoyi::os-lib:$osLibVersion", // https://github.com/lihaoyi/os-lib
      ivy"org.plotly-scala::plotly-core:$plotlyScalaVersion", // https://github.com/alexarchambault/plotly-scala
      ivy"org.plotly-scala::plotly-render:$plotlyScalaVersion" // https://github.com/alexarchambault/plotly-scala (http://plotly-scala.org)
    ) ++
      super[GraalModules].ivyDeps() ++
      super[OpenFXModules].ivyDeps()
  }

  def publishVersion = "0.1.0"

  def pomSettings = T {
    PomSettings(
      description = "MDoc module",
      organization = "com.gitlab.hmf",
      url = "https://gitlab.com/hmf/srllab",
      licenses = Seq(License.`MIT`),
      versionControl = VersionControl.github("hmf", "srllab"),
      developers = Seq(Developer("hmf", "Hugo Ferreira", "https://gitlab.com/hmf"))
    )
  }

  trait uTests extends TestModule {
    //override def moduleDeps = super.moduleDeps //++ Seq(webkit)

    override def ivyDeps = T{ Agg(ivy"com.lihaoyi::utest::$utestVersion") } // ++ mdocs.ivyDeps() }
    //def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")
    override def testFrameworks: Target[Seq[String]] = T{ Seq("mdocs.CustomFramework") }

    override def forkArgs: Target[Seq[String]] = T{ markdoc.forkArgs() }
    override def forkEnv: Target[Map[String, String]] = T{ markdoc.forkEnv() }

    override def test(oargs: String*): Command[(String, Seq[TestRunner.Result])] = T.command {
      val args = setForkArgs(oargs)
      super.test(args: _*)
    }
  }

  object test extends Tests with uTests

}


/**
 * MDoc is a documentation tool which compiles and evaluates Scala code in
 * documentation files and provides various options for configuring how the
 * results will be displayed in the compiled documentation.
 *
 * Extending this trait declares a Scala module which compiles markdown (`md`),
 * HTML, structured (`rst`) and .txt` files in the `mdoc` folder of the
 * module with MDoc.
 *
 * By default the resulting documents are simply placed in the Mill build
 * output folder but they can be placed elsewhere by overriding the
 * [[mill.contrib.mdoc.MdocModule#tMdocTargetDirectory]] task.
 *
 * For example:
 *
 * {{{
 * // build.sc
 * import mill._, scalalib._, contrib.mdoc.__
 *
 * object example extends MDocModule {
 *   def scalaVersion = "2.12.6"
 *   def mdocVersion = "0.6.7"
 * }
 * }}}
 *
 * This defines a project with the following layout:
 *
 * {{{
 * build.sc
 * example/
 *     src/
 *     docs/mdoc/
 *     resources/
 * }}}
 *
 * In order to compile documentation we can execute the `mdoc` task in the module:
 *
 * {{{
 * sh> mill example.mdoc
 * }}}
 */
trait MDocModule extends ScalaModule {
  //override def moduleDeps = Seq(plots)

  /**
   * The version of MDoc to use.
   */
  def mdocVersion: T[String]
  def mdocVerbose: Int = 0

  private def printVerbose[A](a:A, l:Int = 0): Unit = if (mdocVerbose > l) print(a)
  private def printlnVerbose[A](a:A, l:Int = 0): Unit = if (mdocVerbose > l) println(a)

  /**
   * A task which determines how to fetch the MDoc jar file and all of the
   * dependencies required to compile documentation for the module and
   * returns the resulting files.
   */
  def mdocIvyDeps: T[Agg[PathRef]] = T {
    Lib.resolveDependencies(
      //repositories :+ MavenRepository(s"https://dl.bintray.com/tpolecat/maven"),
      repositories,
      Lib.depToDependency(_, scalaVersion()),
      compileIvyDeps() ++ transitiveIvyDeps() ++ Seq(
        ivy"org.scalameta::mdoc:${mdocVersion()}"
      )
    )
  }

  /**
   * A task which determines the scalac plugins which will be used when
   * compiling code examples with MDoc. The default is to use the
   * [[mill.contrib.mdoc.MDocModule#scalacPluginIvyDeps]] for the module.
   */
  def mdocScalacPluginIvyDeps: T[Agg[Dep]] = scalacPluginIvyDeps()

  /**
   * This task determines where documentation files must be placed in order to
   * be compiled with MDoc. By default this is the `mdoc` folder at the root
   * of the module.
   */
  def mdocSourceDirectory: Sources = T.sources { millSourcePath / 'docs / 'mdoc }

  /**
   * A task which determines where the compiled documentation files will be
   * placed. By default this is simply the Mill build's output folder for this
   * task, but this can be reconfigured so that documentation goes to the root
   * of the module (e.g. `millSourcePath`) or to a dedicated folder (e.g.
   * `millSourcePath / 'docs`)
   */
  def mdocTargetDirectory: T[os.Path] = T { T.ctx().dest }

  /**
   * A [[scala.util.matching.Regex]] task which will be used to determine
   * which files should be compiled with MDoc. The default pattern is as
   * follows: `.*\.(md|markdown|txt|htm|html)`.
   */
  def mdocNameFilter: T[Regex] = T { """.*\.(md|markdown|rst|txt|htm|html)""".r }

  /**
   * A task which determines what classpath is used when compiling
   * documentation. By default this is configured to use the same inputs as
   * the [[mill.contrib.mdoc.MDocModule#runClasspath]], except for using
   * [[mill.contrib.mdoc.MDocModule#mdocIvyDeps]] rather than the module's
   * [[mill.contrib.mdoc.MDocModule#runIvyDeps]].
   */
  def mdocClasspath: T[Agg[PathRef]] = T {
    // Same as runClasspath but with mdoc added to ivyDeps from the start
    // This prevents duplicate, differently versioned copies of scala-library
    // ending up on the classpath which can happen when resolving separately
    transitiveLocalClasspath() ++
      resources() ++
      localClasspath() ++
      unmanagedClasspath() ++
      mdocIvyDeps()
  }

  /**
   * The CLI options for the MDoc processor.
   * @see https://scalameta.org/mdoc/
   */
  def mdocOpts: T[Seq[String]] = T { List[String]() }

  /**
   * The scalac options which will be used when compiling code examples with
   * MDoc. The default is to use the [[mill.contrib.mdoc.MDoc#scalacOptions]]
   * for the module, but filtering out options which are problematic in the
   * REPL, e.g. `-Xfatal-warnings`, `-Ywarn-unused-imports`.
   */
  def mdocScalacOptions: T[Seq[String]] = scalaDocOptions()  // ++ List("-Ylog-classpath")
  /*
    scalacOptions().filterNot(Set(
      "-Ywarn-unused:imports",
      "-Ywarn-unused-import",
      "-Ywarn-dead-code",
      "-Xfatal-warnings"
    ))*/


  /**
   * A task which performs the dependency resolution for the scalac plugins to
   * be used with MDoc.
   */
  def mdocPluginJars: T[Agg[PathRef]] = resolveDeps(mdocScalacPluginIvyDeps)()


  /**
   * This task is used to collect the `mdocs` project paths to its resource
   * and its compiled classes. This was created so that MDoc's post modifier
   * can be implemented and compiled before the mdoc tasks are called.
   *
   * This should optimally be defined and obtained via the Mill (Ammonite)
   * script, however there does not seem to be a way to compile a class in
   * a Mill script to a file and then obtain the path to this class.
   *
   * The hack is to make mdocs a dependency of the sdk module. Once that has
   * been compiled, we can get the resources and class destination paths
   * and add that to the MDoc command line.
   *
   * **NOTE:** this is ony required when using the embedded version of MDoc.
   * In the case of the spawning MDoc the mdocs path seems to be already
   * accessible.
   *
   * @see https://scalameta.org/mdoc/docs/modifiers.html#postmodifier
   *
   * @return
   */
  def mdDocPostModifierPaths: Target[Seq[PathRef]] = T {
    val postModifier: PathRef = compile().classes
    val postModifierResources = resources()
    printlnVerbose(s"MDoc postModifier = ${postModifier.path.toIO.getAbsolutePath}")

    val resource = postModifierResources.map(_.path.toIO.getAbsolutePath).mkString(java.io.File.pathSeparator)
    printlnVerbose(s"MDoc postModifierResources = $resource")

    postModifierResources ++ List(postModifier)
  }

  import upickle.default
  import upickle.default.{macroRW, ReadWriter => RW}
  import ujson.{IncompleteParseException, ParseException, Readable}
  import ujson.{BytesRenderer, Value, StringRenderer}
  import upickle.core.{NoOpVisitor, Visitor}
  import upickle.default._

  case class mDocCtx(
                      // Resource path the MDoc PostModifiers
                      postModifierPaths: Seq[PathRef],
                      // Classpath to MDoc's libraries
                      paths: Agg[PathRef],
                      // Classpath to MDoc's libraries (string to pass as JVM parameter)
                      libPaths: String,
                      // Source path to documents
                      in: Path,
                      // Destination path were the processed files are placed
                      out: Path,
                      // Regular expression of includd files (not used)
                      re: List[String],
                      // Scala compiler parameters used by MDoc
                      scalaOpts: List[String],
                      // Options used ny MDoc (see MDoc documentation on command line)
                      mdocOpt: Seq[String],
                      // Plug-in options - not used
                      pOpts: mill.api.Loose.Agg[String]
                    )

  object mDocCtx {
    implicit val rw: RW[mDocCtx] = macroRW
  }

  // https://github.com/lihaoyi/mill/issues/598
  def initmDocCtx: T[mDocCtx] = T {
    val postModifierPaths: Seq[PathRef] = mdDocPostModifierPaths()
    val paths: Agg[PathRef] = mdocClasspath() //++ postModifierPaths
    val libPaths: String = paths.map(_.path.toIO.getAbsolutePath).mkString(java.io.File.pathSeparator)
    printlnVerbose(s"MDoc libPaths = $libPaths", 2)
    val in: Path = mdocSourceDirectory().head.path
    printlnVerbose(s"MDoc in = ${in.toIO.getAbsolutePath}")
    val out: Path = mdocTargetDirectory()
    printlnVerbose(s"MDoc out = ${out.toIO.getAbsolutePath}")
    // TODO: fix
    val re: List[String] = if (mdocNameFilter().regex != "") List("--include", mdocNameFilter().regex) else List("")
    printlnVerbose(s"MDoc filter = $re")
    val scalaOpts: List[String] = if (mdocScalacOptions().nonEmpty) List("--scalac-options") ++ mdocScalacOptions() else List[String]()
    printlnVerbose(s"MDoc ScalacOptions = $scalaOpts")
    val mdocOpt: Seq[String] = mdocOpts()
    printlnVerbose(s"MDoc Options = $mdocOpt")
    val pOpts: mill.api.Loose.Agg[String] = mdocPluginJars().map(pathRef => "-Xplugin:" + pathRef.path.toIO.getAbsolutePath)
    printlnVerbose(s"MDoc plugin options = $pOpts")

    val local: Loose.Agg[PathRef] = transitiveLocalClasspath()
    val localPaths: String = local.map(_.path.toIO.getAbsolutePath).mkString(java.io.File.pathSeparator)
    printlnVerbose(s"MDoc localLibPaths = $localPaths")

    mDocCtx(postModifierPaths, paths, libPaths, in, out, re, scalaOpts, mdocOpt, pOpts)
  }

  /**
   * Run MDoc using the configuration specified in this module. The working
   * directory used is the [[mill.contrib.mdoc.MDocModule#millSourcePath]].
   */
  def mDoc: T[os.CommandResult] = T {
    val ctx = initmDocCtx()

    // Correct format form options format
    val scalaOptions: List[String] = List(ctx.scalaOpts.head, ctx.scalaOpts.drop(1).mkString(" "))

    // Because I am spawning a JVM to execute `mdoc.Main.java` so naturally I
    // cannot see the console's output because it is being redirected by
    // MDoc. If we used the API we could use the `Reporter`to see the output
    // Here we simple collect the output and print it at the end
    val args:List[String] = List(
      "--in", ctx.in.toIO.getAbsolutePath,
      "-out", ctx.out.toIO.getAbsolutePath,
      "--report-relative-paths") ++
      scalaOptions ++
      ctx.mdocOpt // ++ ctx.re
    val res = os.proc(
      'java,
      "-cp", ctx.libPaths,
      "mdoc.Main.java",
      args
    ).call(os.Path(ctx.in.toIO.getAbsolutePath))
    // Ths does not work. It has two issues.
    // The fist is that we get a java.io.IOException: Stream closed
    // The second is that the output of the PlotlyModifier does not show up

    // (Always) print out the collected output
    printlnVerbose(res.out)
    //println(res.out)
    res
  }


  /**
   * MDoc has its own class loader that is used to load the PostModifier
   * implementations. We first access this class loader and instantiate
   * it as an Ammonite class loader (it has been subverted by Ammonite).
   * This class loader will be used to dynamically (at run-time) add the
   * PostModifier implementations to the MDoc resource path.
   *
   * We are also forced to add all local paths of code that the PostModifier
   * depends on for the same reason above. For example WebKit and Plots.
   * This may pose problems for use via a library.
   *
   * We first check and print the current MDoc resource path. We then take
   * the resource path to the (compiled) PostModifier classes and add those
   * to the MDoc resource path. Note that this will repeat the PostModifier
   * paths.
   *
   * Notes: "Not 100% sure I understand your question, but
   * `repl.sess.frames.flatMap(_.classloader.inMemoryClasses)`
   * gives a list of class names / class byte code generated by the repl."
   *
   * @see https://stackoverflow.com/questions/1010919/adding-files-to-java-classpath-at-runtime
   *      https://stackoverflow.com/questions/19414453/how-to-get-resources-directory-path-programmatically
   *      https://github.com/lihaoyi/Ammonite/blob/master/amm/runtime/src/main/scala/ammonite/runtime/ClassLoaders.scala
   *
   */
  def addPathToLoader: Target[Unit] = T {
    import scala.collection.JavaConverters._

    // Get MDoc's class loader
    val cl = mdoc.Main.getClass.getClassLoader
    // Make sure we have Ammonite's class loader - it provides the add(URL) methods
    val cla = cl.asInstanceOf[ammonite.runtime.SpecialClassLoader]

    // Find all of the available MDoc PostModifier implementations using the Java service loader
    // This will be empty if have not added the correct resource path
    val post = java.util.ServiceLoader.load(classOf[mdoc.PostModifier], cl).iterator().asScala.toList
    // println(post.mkString(";\n"))
    printlnVerbose(s"""Checking for PostModifier (1): ${post.mkString("n")}""",2)

    // Same as above
    // Find all of the available MDoc PostModifier implementations using the Java service loader
    // This will be empty if have not added the correct resource path
    // Already available in the PostModifier
    val posts = mdoc.PostModifier.default()
    printlnVerbose(s"""Checking for PostModifier (2): ${posts.mkString("n")}""",2)

    val postModifierResources = markdoc.resources()
    val resources = postModifierResources.map(_.path.toIO.getAbsolutePath)
    //val resource = new java.io.File(resources.head).toURI.toURL
    printlnVerbose(s"""Existing resources paths: ${resources.mkString("n")}""",2)

    // This is an example of how to invoke the private method of the class
    // loader that allows adding a resource or library path during run-time.
    // This does not work with Ammonite's classloader - it does not have this method
    /*
    val parameters = classOf[java.net.URL]
    val method: java.lang.reflect.Method = cl.getClass.getDeclaredMethod("addURL", parameters)
    method.setAccessible(true)
    val inputs = Array[Object](resource)
    method.invoke(cl, inputs)
    */

    // Add the resource paths of the project module where the PostModifiers are defined
    // Add transitive paths of the module project which will also include the PostModifiers dependencies
    val mods: Seq[PathRef] = mdDocPostModifierPaths() ++ transitiveLocalClasspath()
    // Add each of these paths to the class loader used by MDoc
    mods.foreach{e =>
      val f = e.path.toIO
      val resource = f.toURI.toURL
      //println("addPathToLoader URL ="+resource)
      cla.add(resource)
    }

  }

  /**
   * Run MDoc using the configuration specified in the Ammomite script. The
   * working directory used is the [[mill.contrib.mdoc.MDocModule#millSourcePath]].
   * This uses a fixed version of the plug-in and runs within the Mill JVM.
   *
   * NOTE: MDoc needs to have its class loader updated with the compile
   * PostModifier implementations. This is done in the `addPathToLoader` call.
   * This is only required for the local API call because in the case of
   * the command line we add the resources path to the class path.
   *
   */
  def mDocLocal: T[Int] = T {
    val ctx = initmDocCtx()

    val args = List() ++ ctx.mdocOpt //++ ctx.re

/* java.lang.UnsupportedClassVersionError: com/sun/glass/ui/monocle/MonoclePlatformFactory has been compiled by a more recent version of the Java Runtime (class file version 54.0), this version of the Java Runtime only recognizes class file versions up to 52.0
    java.lang.ClassLoader.defineClass1(Native Method)
    java.lang.ClassLoader.defineClass(ClassLoader.java:763)
    java.security.SecureClassLoader.defineClass(SecureClassLoader.java:142)
    java.net.URLClassLoader.defineClass(URLClassLoader.java:468)
    java.net.URLClassLoader.access$100(URLClassLoader.java:74)
    java.net.URLClassLoader$1.run(URLClassLoader.java:369)
    java.net.URLClassLoader$1.run(URLClassLoader.java:363)
    java.security.AccessController.doPrivileged(Native Method)
    java.net.URLClassLoader.findClass(URLClassLoader.java:362)
    ammonite.runtime.SpecialClassLoader.findClass(ClassLoaders.scala:241)
    java.lang.ClassLoader.loadClass(ClassLoader.java:424)
    java.lang.ClassLoader.loadClass(ClassLoader.java:357)
    java.lang.Class.forName0(Native Method)
    java.lang.Class.forName(Class.java:264)
    utils.HeadlessWebKit$.assignMonoclePlatform(HeadlessWebKit.scala:616)
    utils.HeadlessWebKit$.initMonocleHeadless(HeadlessWebKit.scala:598)
    utils.HeadlessWebKit$.<init>(HeadlessWebKit.scala:127)
    utils.HeadlessWebKit$.<clinit>(HeadlessWebKit.scala)
    mdocs.PlotlyModifier$.initWebKit(PlotlyModifier.scala:30)
    mdocs.PlotlyModifier.<init>(PlotlyModifier.scala:59)
 */
// https://stackoverflow.com/questions/29116819/javafx-maven-testfx-monocle-dont-work-together

    // We cannot add the resources manually via the classpath parameter of the JVM
    // as we did with the Laika spawn. So here we add the path to the classloader
    // at run-time. This allows MDoc to load the PostModifiers when parsing a user
    // defined mode
    addPathToLoader()

    // 0.5.2-10-d7591b
    // JAVA_OPTS="-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080 -DsocksProxyVersion=5" mill -i core.console
    // TODO: how do we pass the forkArgs of jFX and to the Mill JVM?
    // TODO: to have HeadlessWebKit we need to define it at the Mill level
    //import utils.{HeadlessWebKit, Utils}
    //HeadlessWebKit.launchBackground(Array[String]())
    //HeadlessWebKit.waitStart()

    val settings = mdoc.MainSettings()
      .withArgs(args) // for CLI only
      .withSiteVariables(Map("VERSION" -> "1.0.0"))
      .withIn( ctx.in.toIO.toPath )
      .withOut( ctx.out.toIO.toPath )
      .withScalacOptions( ctx.scalaOpts.drop(1).mkString(" ") ) // remove the flag name
      .withClasspath( ctx.libPaths )
      .withReportRelativePaths(true )
      // https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns
      //java.nio.file.PathMatcher
      // https://www.programcreek.com/scala/java.util.regex.Pattern
      //.withIncludePath(mdocNameFilter())
    // generate out/readme.md from working directory
    val exitCode = mdoc.Main.process(settings)
    // (optional) exit the main function with exit code 0 (success) or 1 (error)
    //if (exitCode != 0) println(s"error = $exitCode")
    exitCode
  }

}


/**
 * Laika utilities.
 */
object LaikaUtils {

  // https://github.com/planet42/Laika/blob/master/sbt/src/main/scala/laika/sbt/Tasks.scala#L139
  // https://github.com/planet42/Laika/blob/master/core/src/main/scala/laika/ast/elements.scala
  /**
   * @see https://github.com/planet42/Laika/blob/master/core/src/main/scala/laika/ast/elements.scala
   * @param level logging level
   * @return MessageLevel
   */
  def messageLevel(level:String): MessageLevel = {
    val s = level.trim.toLowerCase
    s match {
      case "debug" => MessageLevel.Debug
      case "info" =>  MessageLevel.Info
      case "warning" => MessageLevel.Warning
      case "error" => MessageLevel.Error
      case "fatal" => MessageLevel.Fatal
      case _ => throw new IllegalArgumentException(s"Unsupported laika.ast.MessageLevel: $s")
    }
  }

  /**
   * Converts a string describing the document type to the appropriate type.
   *
   * @see https://github.com/planet42/Laika/blob/master/sbt/src/main/scala/laika/sbt/Tasks.scala#L288
   * @param format document types such as "md", "markdown"(Markdown), "rst"
   *               (ReStructuredText), "html", "epub", "pdf", "fo", "xslfo",
   *               "xsl-fo" (XSLFO) and "formatted-ast", "ast"
   * @return A Laika format type
   */
  def outputFormat(format: String): Any = {
    val s = format.trim.toLowerCase
    s match {
      case "md" | "markdown" => Markdown
      case "rst" => ReStructuredText
      case "html" => HTML
      case "epub" => EPUB
      case "pdf" => PDF
      case "fo" | "xslfo" | "xsl-fo" => XSLFO
      case "formatted-ast" | "ast" => AST
      case _ => throw new IllegalArgumentException(s"Unsupported format: $format")
    }
  }

  /**
   * Converts a string describing the document type to the appropriate type's name.
   * @param format document types such as "md", "markdown"(Markdown), "rst"
   *               (ReStructuredText), "html", "epub", "pdf", "fo", "xslfo",
   *               "xsl-fo" (XSLFO) and "formatted-ast", "ast"
   * @return the laika format type's name slightly altered)
   */
  def outputFormatName(format: String): String = {
    val s = format.trim.toLowerCase
    s match {
      case "md" | "markdown" => Markdown.getClass.getName.dropRight(1)
      case "rst" => ReStructuredText.getClass.getName.dropRight(1)
      case "html" => HTML.getClass.getName.dropRight(1)
      case "epub" => EPUB.getClass.getName.dropRight(1)
      case "pdf" => PDF.getClass.getName.dropRight(1)
      case "fo" | "xslfo" | "xsl-fo" => XSLFO.getClass.getName.dropRight(1)
      case "formatted-ast" | "ast" => AST.getClass.getName.dropRight(1)
      case _ => throw new IllegalArgumentException(s"Unsupported format: $format")
    }
  }

  /**
   * This a Markup parser that uses the `GitHubFlavor` and allows for the
   * embedding of the HTML code (via `withRawContent`). We also allow for the
   * parsing of ReStructured documents. Care must be taken not to use embedded
   * HTML when generating PDF documents.
   * */
  val parser = MarkupParser
    .of(Markdown)
    .withRawContent
    .using(GitHubFlavor)
    .io(blocker)
    .parallel[IO]
    .withAlternativeParser(MarkupParser.of(ReStructuredText).withRawContent)
    .build

  /**
   * Generates a renderer for a specific output.
   *
   * @param format Output format such as HTML, PDF and EPUB
   * @param level Level of message output (debug, warning, error)
   * @tparam FMT render type
   * @return a parallel render for faster processing
   */
  def renderer[FMT](format: RenderFormat[FMT], level:MessageLevel): ParallelRenderer[IO] = {
    val renderer = Renderer
      .of(format)
      .withConfig(LaikaUtils.parser.config)
      .withMessageLevel(level)
      .io(blocker)
      .parallel[IO]
      .build
    renderer
  }

  def renderer[FMT, PP](format: TwoPhaseRenderFormat[FMT, PP], level:MessageLevel): binary.ParallelRenderer[IO] = {
    val renderer = Renderer
      .of(EPUB)
      .withConfig(LaikaUtils.parser.config)
      .withMessageLevel(level)
      .io(blocker)
      .parallel[IO]
      .build
    renderer
  }


  /**
   * Goes through the source files contained in the directory `in` and
   * renders these to the output directory `out`. If a static document (a
   * file that is not a Markdown or ReStructured source file) is found, it
   * is copied as-is to the destination directory.
   *
   * @param renderer renderer that will take the AST describing the input
   *                 source and convert it to an output file of a given format.
   * @param in source documents input directory
   * @param out destination output directory
   * @return returns a description of the parsed sources
   */
  def render(renderer: ParallelRenderer[IO], in: String, out: String): RenderedTreeRoot[IO] = {
    val op = LaikaUtils.parser.fromDirectory(in).parse.flatMap { tree =>
      renderer
        .from(tree.root)
        .copying(tree.staticDocuments)
        .toDirectory(out)
        .render
    }
    op.unsafeRunSync()
  }

  /**
   * Goes through the source files contained in the directory `in` and
   * renders these to a single `out` file (for example PDF). If a static
   * document (a file that is not a Markdown or ReStructured source file) is
   * found, it is copied as-is to the destination directory.
   *
   * @param renderer renderer that will take the AST describing the input
   *                 source and convert it to an output file of a given format.
   * @param in source documents input directory
   * @param out destination output file
   * @return returns a description of the parsed sources
   */
  def render(renderer: binary.ParallelRenderer[IO], in: String, out: String): Unit = {
    val op = LaikaUtils.parser.fromDirectory(in).parse.flatMap { tree =>
      renderer
        .from(tree.root)
        .copying(tree.staticDocuments)
        .toFile(out)
        .render
    }
    op.unsafeRunSync()
  }


  def useOutputFormat[A](format: String, level:MessageLevel, in:String, out:String): Unit = {
    val s = format.trim.toLowerCase

    s match {
      //case "md" | "markdown" => t.to(Markdown)
      //case "rst" => t.to(ReStructuredText)
      case "html" =>
        val r = renderer(HTML, level)
        render(r, in, out)

      case "epub" =>
        val outFilePath = os.Path(out) / "all.epub"
        val outFile = outFilePath.toIO.getAbsolutePath
        val r = LaikaUtils.renderer(EPUB, level)
        render(r, in, outFile)

      case "pdf" =>
        val outFilePath = os.Path(out) / "all.pdf"
        val outFile = outFilePath.toIO.getAbsolutePath
        val r = LaikaUtils.renderer(PDF, level)
        render(r, in, outFile)

      case "fo" | "xslfo" | "xsl-fo" =>
        val r = LaikaUtils.renderer(XSLFO, level)
        render(r, in, out)

      case "formatted-ast" | "ast" =>
        val r = LaikaUtils.renderer(AST, level)
        render(r, in, out)

      case _ => throw new IllegalArgumentException(s"Unsupported format: $format")
    }
  }

}

/**
 *
 * @see https://github.com/planet42/Laika
 */
trait LaikaModule extends ScalaModule with MDocModule {


  /**
   * The version of Laika to use.
   */
  def laikaVersion: T[String]
  def laikaVerbose: Int = 0

  private def printVerbose[A](a:A, l:Int = 0): Unit = if (laikaVerbose > l) print(a)
  private def printlnVerbose[A](a:A, l:Int = 0): Unit = if (laikaVerbose > l) println(a)

  /**
   * A task which determines how to fetch the Laika jar file and all of the
   * dependencies required to compile documentation for the module and
   * returns the resulting files.
   */
  def laikaIvyDeps: T[Agg[PathRef]] = T {
    Lib.resolveDependencies(
      //repositories :+ MavenRepository(s"https://dl.bintray.com/tpolecat/maven"),
      repositories,
      Lib.depToDependency(_, scalaVersion()),
      compileIvyDeps() ++ transitiveIvyDeps() ++ Seq(
        ivy"org.planet42::laika-core:${laikaVersion()}",
        ivy"org.planet42::laika-io:${laikaVersion()}",
        ivy"org.planet42::laika-pdf:${laikaVersion()}"
      )
    )
  }

  /**
   * A task which determines the scalac plugins which will be used when
   * compiling code examples with Laika. The default is to use the
   * [[mill.contrib.mdoc.MDocModule#scalacPluginIvyDeps]] for the module.
   */
  def laikaScalacPluginIvyDeps: T[Agg[Dep]] = scalacPluginIvyDeps()

  /**
   * This task determines where documentation files must be placed in order to
   * be compiled with Laika. By default this is the `mdoc` folder at the root
   * of the module.
   */
  def laikaSourceDirectory: Sources = T.sources { mdocTargetDirectory() }

  /**
   * A task which determines where the compiled documentation files will be
   * placed. By default this is simply the Mill build's output folder for this
   * task, but this can be reconfigured so that documentation goes to the root
   * of the module (e.g. `millSourcePath`) or to a dedicated folder (e.g.
   * `millSourcePath / 'docs`)
   */
  def laikaTargetDirectory: T[os.Path] = T { T.ctx().dest }

  /**
   * A task which determines what classpath is used when compiling
   * documentation. By default this is configured to use the same inputs as
   * the [[mill.contrib.mdoc.MDocModule#runClasspath]], except for using
   * [[mill.contrib.mdoc.MDocModule#mdocIvyDeps]] rather than the module's
   * [[mill.contrib.mdoc.MDocModule#runIvyDeps]].
   */
  def laikaClasspath: T[Agg[PathRef]] = T {
    // Same as runClasspath but with mdoc added to ivyDeps from the start
    // This prevents duplicate, differently versioned copies of scala-library
    // ending up on the classpath which can happen when resolving separately
    transitiveLocalClasspath() ++
      resources() ++
      localClasspath() ++
      unmanagedClasspath() ++
      laikaIvyDeps()
  }

  /**
   * A Task only checks its input `source` to see if execution is required.
   * In order to compile based on the output of another tasks
   *
   * @return
   */
  override def generatedSources = T {
    val dest: Seq[PathRef] = super.generatedSources() ++ laikaSourceDirectory()
    dest
  }

  import scala.reflect.runtime.currentMirror
  import scala.tools.reflect.ToolBox
  val toolbox = currentMirror.mkToolBox()
  import toolbox.u._

  def prepRemoteLaika(out: String, outFormat:String, msgLevel:String = "fatal"): toolbox.u.Symbol = {

    val fileName =
      if (outFormat.toLowerCase.contains("pdf")) Some("all.pdf")
      else if (outFormat.toLowerCase.contains("epub")) Some("all.epub")
      else None
    val output = fileName match {
      case Some(file) =>
        val outFilePath = os.Path(out) / file
        val outFile = outFilePath.toIO.getAbsolutePath
        s"""toFile("$outFile")"""
      case None =>
        s"""toDirectory("$out")"""
    }

    val tree = toolbox.parse(s"""
    object W {
      import cats.effect.{Blocker, ContextShift, IO}
      import laika.ast.MessageLevel
      import laika.markdown.github.GitHubFlavor
      import laika.io.model.RenderedTreeRoot
      import laika.api.{MarkupParser, Renderer, Transformer}
      import laika.format.{AST, EPUB, HTML, Markdown, PDF, ReStructuredText, XSLFO}

      import laika.io.implicits._
      import scala.concurrent.ExecutionContext
      import java.util.concurrent.Executors
      implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

      val blocker = Blocker.liftExecutionContext(
        ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
      )

      import LaikaUtils._

      def transform(in: String) = {

         val transformer = Transformer
           .from(Markdown)
           .to($outFormat)
           .using(GitHubFlavor)
           .withRawContent
           .io(blocker)
           .parallel[IO]
           .build

         val res = transformer
           .fromDirectory(in)
           .$output
           .transform

         res.unsafeRunSync()
      }
    }
    """)
    val symbol = toolbox.define(tree.asInstanceOf[toolbox.u.ImplDef])
    symbol
  }

  /**
   * In order to execute an arbitrary library version, we have to: a) create a
   * class loader with the required path from the dependencies; b) load the
   * main class and c) get the class's main method. We can use this method to
   * invoke the library's API (see example referenced below). Alternatively we
   * could also avoid al this and simple make a process call (`os.proc`) to
   * invoke the main method.
   *
   * However Laika does not have such a command line like API. As such we must
   * find another way to invoke the API. We do this by dynamically compiling
   * code that uses the Laika API. This is not cached. It cannot be cached
   * because we use the output format, message level and output directory
   * to set up the parser and renderer.
   *
   * However the API (see [[LaikaUtils]]) is complex so we have opted for a
   * simpler version. Unfortunately this means that the ReStructure source
   * is not supported.
   *
   * IMPORTANT: This version does not handle ReStructure documents.
   *
   * @see https://github.com/lefou/mill-aspectj/blob/master/aspectj/src/de/tobiasroeser/mill/aspectj/AspectjInJvmWorker.scala#L18
   *
   * @param format Output format to be generated
   * @param msgLevel Message level (debug, warnings, error)
   * @return Mill command
   */
  def laika(format:String = "HTML", msgLevel:String = "fatal") = T.command {
    val libPaths = laikaClasspath().map(_.path.toIO.getAbsolutePath).mkString(java.io.File.pathSeparator)
    printlnVerbose(s"Laika libPaths = $libPaths", 2)
    val in = laikaSourceDirectory().head.path.toIO.getAbsolutePath
    printlnVerbose(s"Laika in = $in")
    val out = laikaTargetDirectory().toIO.getAbsolutePath
    printlnVerbose(s"Laika out = $out")
    val level = LaikaUtils.messageLevel(msgLevel)
    printlnVerbose(s"messageLevel = $level")
    val outFormat = LaikaUtils.outputFormatName(format)
    printlnVerbose(s"format = $outFormat")

    // See notes in generatedSources
    mDoc()

    lazy val symbol = prepRemoteLaika(out, outFormat, msgLevel)
    val x = toolbox.eval(q"$symbol.transform($in)")
  }

  // TODO: https://gist.github.com/wfaler/498e61d0f4879b983434
  // https://users.scala-lang.org/t/toolbox-function-doesnt-execute-eval-ed-code/1146
  // https://stackoverflow.com/questions/34612322/dynamically-parse-a-string-and-return-a-function-in-scala-using-reflection-and-i
  /**
   * Run laika using the configuration specified in this module. The working
   * directory used is the [[mill.contrib.mdoc.MDocModule#millSourcePath]].
   */
  def laikaLocal(format:String = "HTML", msgLevel:String = "fatal") = T.command {
    val libPaths = laikaClasspath().map(_.path.toIO.getAbsolutePath).mkString(java.io.File.pathSeparator)
    printlnVerbose(s"Laika libPaths = $libPaths", 2)
    val in = laikaSourceDirectory().head.path.toIO.getAbsolutePath
    printlnVerbose(s"Laika in = $in")
    val out = laikaTargetDirectory().toIO.getAbsolutePath
    printlnVerbose(s"Laika out = $out")
    val level = LaikaUtils.messageLevel(msgLevel)
    printlnVerbose(s"messageLevel = $level")
    val outFormat = LaikaUtils.outputFormat(format)
    printlnVerbose(s"format = $outFormat")

    // See notes in generatedSources
    mDocLocal()

    LaikaUtils.useOutputFormat(format, level, in, out)
  }
}


object loom extends ScalaModule with GraalModules with OpenFXModules with PublishModule {
  override def moduleDeps = Seq(plots)

  override def scalaVersion: Target[String] = T{ gScalaVersion }
  def mdocVersion: T[String] = T{ gMdocVersion }

  override def ivyDeps = T {
    Agg(
      ivy"org.scalameta::mdoc:${mdocVersion()}",                   // https://scalameta.org/mdoc/
      // PostModifier
      ivy"com.github.pathikrit::better-files:$betterFilesVersion",
      ivy"org.plotly-scala::plotly-core:$plotlyScalaVersion",      // https://github.com/alexarchambault/plotly-scala
      ivy"org.plotly-scala::plotly-render:$plotlyScalaVersion"     // (http://plotly-scala.org)

      // TODO: remove ?
      /*
      ivy"org.planet42::laika-io:${laikaVersion()}",
      ivy"org.planet42::laika-pdf:${laikaVersion()}",
      ivy"com.lihaoyi::os-lib:$osLibVersion", // https://github.com/lihaoyi/os-lib
      ivy"tech.tablesaw:tablesaw-core:$tableSawVersion", // https://github.com/jtablesaw/tablesaw
      ivy"tech.tablesaw:tablesaw-aggregate:$tableSawVersion",
       */
    ) ++
      super[GraalModules].ivyDeps() ++
      super[OpenFXModules].ivyDeps()
  }

  // Used for the core Mill components
  override def compileIvyDeps = T{ Agg(
    ivy"com.lihaoyi::mill-main:$millVersion",
    ivy"com.lihaoyi::mill-scalalib:$millVersion"
  ) }

  /**
   * Note that the GraalVM recalculate the arguments because the run and test
   * commands may change them.
   *
   * @return
   */
  override def forkArgs: Target[Seq[String]] = T {
    Seq("-Dprism.verbose = true", "-ea") ++ // JavaFX
      Utils.jvmModuleArgs(super[GraalModules].forkArgs(), super[OpenFXModules].forkArgs()) // GraavVM + OpenFX
  }

  override def forkEnv: Target[Map[String, String]] =
   T{ Map("prism.verbose" -> "true") } // -Dprism.verbose = true, // JavaFX

  def destinationDirectory: T[os.Path] = T { T.ctx().dest }


  def publishVersion = "0.1.0"

  def pomSettings = T {
    PomSettings(
      description = "Mill module to generate sites with Laika",
      organization = "com.gitlab.hmf",
      url = "https://gitlab.com/hmf/srllab",
      licenses = Seq(License.`MIT`),
      versionControl = VersionControl.github("hmf", "srllab"),
      developers = Seq(Developer("hmf", "Hugo Ferreira", "https://gitlab.com/hmf"))
    )
  }

  trait uTests extends TestModule {
    override def ivyDeps = T{ Agg(ivy"com.lihaoyi::utest::$utestVersion") }
    //def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")
    def testFrameworks: Target[Seq[String]] = T{ Seq("loom.CustomFramework") }

    override def forkArgs: Target[Seq[String]] = T{ loom.forkArgs() }
    override def forkEnv: Target[Map[String, String]] = T{ loom.forkEnv() }

    override def test(oargs: String*): Command[(String, Seq[TestRunner.Result])] = T.command {
      val args = setForkArgs(oargs)
      super.test(args: _*)
    }
  }

  object test extends Tests with uTests

}

object it extends MillIntegrationTestModule {

  def millTestVersion = millVersion

  def pluginsUnderTest = Seq(loom)
  def temporaryIvyModules = Seq(plots, webkit)
}

/** Run tests. */
def test() = T.command {
  loom.test.test()()
  it.test()()
}

def testIt() = T.command {
  println("TestIT ---------------")
  it.test()()
}


/**
 * Check for all targets in the sdk
 * ./mill -i resolve sdk._
 *
 * Clean all the project's modules or just the sdk module
 * ./mill clean
 * ./mill clean sdk
 *
 * Generate the scaladoc documentation
 * ./mill -i sdk.docJar
 *
 * Execute the MDoc parser. It processes all code fences. `show` shows projects
 * dependencies by making a dry run. The *local* version executes the task using
 * the embedded fixed version of the Ivy library. The standard version uses a
 * downloaded version. The download version can be set via the trait's member.
 *
 * ./mill show sdk.mDoc
 * ./mill show sdk.mDocLocal
 * ./mill -i sdk.mDoc
 * ./mill -i sdk.mDocLocal
 *
 * ./mill show sdk.laikaLocal
 * ./mill -i sdk.laikaLocal
 *
 * `jfxmill.sh`
 * ./mill -i sdk.genJFXMill
 *
 * ./jfxmill.sh -i sdk.mDoc
 * ./jfxmill.sh -i sdk.mDocLocal
 */
object sdk extends MDocModule with LaikaModule with GraalModules with OpenFXModules {
  override def moduleDeps: Seq[JavaModule] = Seq(plots, markdoc)

  val appVersion = "0.1.0"

  override def scalaVersion = gScalaVersion

  // PlugIns

  // Markdown documentation: Scala code parsing
  override def mdocVersion: Target[String] = gMdocVersion
  override def mdocVerbose: Int = gMdocVerbose
  override def mdocScalacOptions: T[Seq[String]] = super.mdocScalacOptions()
                                                        // ++ List("-Ylog-classpath") // prints the classpath
  override def mdocOpts: T[Seq[String]] = super.mdocOpts() // ++ List("--verbose")

  // HTML Site generation
  override def laikaVersion: Target[String] = gLaikaVersion
  override def laikaVerbose: Int = gLaikaVerbose

  override def scalacOptions = Seq("-deprecation", "-feature")

  /**
   * Note that the GraalVM recalculate the arguments because the run and test
   * commands may change them.
   *
   * @return
   */
  override def forkArgs: Target[Seq[String]] = T {
    Seq("-Dprism.verbose = true", "-ea") ++ // JavaFX
      Utils.jvmModuleArgs(super[GraalModules].forkArgs(), super[OpenFXModules].forkArgs()) // GraavVM + OpenFX
  }


  override def forkEnv: Target[Map[String, String]] =
    Map("prism.verbose" -> "true") // -Dprism.verbose = true, // JavaFX


  // https://github.com/cardillo/joinery
  // https://github.com/zavtech/morpheus-core
  // https://deeplearning4j.org/docs/latest/datavec-overview
  // libraryDependencies += "tech.tablesaw" % "tablesaw-core" % "0.34.1"
  // libraryDependencies += "tech.tablesaw" % "tablesaw-beakerx" % "0.34.1"
  // libraryDependencies += "tech.tablesaw" % "tablesaw-excel" % "0.34.1"
  // libraryDependencies += "tech.tablesaw" % "tablesaw-html" % "0.34.1"
  // libraryDependencies += "tech.tablesaw" % "tablesaw-json" % "0.34.1"
  // libraryDependencies += "tech.tablesaw" % "tablesaw-jsplot" % "0.34.1"

  // repositories :+ MavenRepository(s"https://oss.sonatype.org/content/repositories/releases"),
  override def repositories = super.repositories ++ Seq(
    MavenRepository("https://dl.bintray.com/cibotech/public")
  )

  override def ivyDeps = T {
    Agg(
      ivy"com.github.pathikrit::better-files:$betterFilesVersion",
      // TODO: do we need this
      //ivy"org.typelevel::cats-core:2.0.0",
      //ivy"org.typelevel::cats-effect:2.0.0",
      ivy"org.scalameta::mdoc:${mdocVersion()}", // https://scalameta.org/mdoc/
      ivy"org.planet42::laika-io:${laikaVersion()}",
      ivy"org.planet42::laika-pdf:${laikaVersion()}",
      //ivy"com.lihaoyi:mill:$millVersion",                     // https://github.com/lihaoyi/mill
      ivy"com.lihaoyi::os-lib:$osLibVersion", // https://github.com/lihaoyi/os-lib
      ivy"tech.tablesaw:tablesaw-core:$tableSawVersion", // https://github.com/jtablesaw/tablesaw
      ivy"tech.tablesaw:tablesaw-aggregate:$tableSawVersion",
      ivy"org.plotly-scala::plotly-core:$plotlyScalaVersion", // https://github.com/alexarchambault/plotly-scala (http://plotly-scala.org)
      ivy"org.plotly-scala::plotly-render:$plotlyScalaVersion"
    ) ++
      super[GraalModules].ivyDeps() ++
      super[OpenFXModules].ivyDeps()
  }

  /*
  override def compileIvyDeps = Agg(
    ivy"org.scala-lang:scala-library:2.12.8"
  )

  override def scalacPluginIvyDeps = Agg{
    ivy"org.scala-lang:scala-library:2.12.8"
  }*/

  def genJFXMill: Target[Unit] = T {
    val params = forkArgs()
    println(params)
    println(os.pwd)
    val script = "jfxmill.sh"
    val wd = os.pwd
    val header = "#!/bin/bash"
    val content =
       s"""
        |$header
        |JAVA_OPTS="${params.mkString(" ")}"
        |#echo $$JAVA_OPTS
        |./mill "$$@"
        |""".stripMargin

    os.write.over(wd / script, content, "rwxrw-r--")
  }

  trait uTests extends TestModule {
    override def ivyDeps = Agg(ivy"com.lihaoyi::utest::$utestVersion")
    def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")

    override def forkArgs: Target[Seq[String]] = sdk.forkArgs()
    override def forkEnv: Target[Map[String, String]] = sdk.forkEnv()

    override def test(oargs: String*): Command[(String, Seq[TestRunner.Result])] = T.command {
      val args = setForkArgs(oargs)
      super.test(args: _*)
    }
  }

  object test extends Tests with uTests

}

