// cSpell:ignore scalalib, helloworld, coursier, Deps, unmanaged, classpath
// cSpell:ignore javafx, controlsfx, openjfx

import coursier.{Dependency, Module, ModuleName, Resolve}
import mill._
import mill.api.Loose
import mill.define.{Target, Task}
import scalalib._

import java.io.File
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import coursier.cache.{Cache, FileCache}
import coursier.core.Resolution
import coursier.error.CoursierError
import coursier.internal.FetchCache
import coursier.params.{Mirror, ResolutionParams}
import coursier.util.{Artifact, Sync, Task => CTask}
import coursier.util.Monad.ops._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

val ScalaVersion = "3.1.1"

//val javaFXVersion = "11.0.2"
//val javaFXVersion = "12"
//val javaFXVersion = "13.0.2"
val javaFXVersion = "16"

val mUnitVersion         = "1.0.0-M3" //"0.7.27"
val controlsFXVersion    = "11.1.0"
val hanSoloChartsVersion = "16.0.12"

val ivyMunit = ivy"org.scalameta::munit::$mUnitVersion"
val ivyMunitInterface = "munit.Framework"

/**
 * When working with JavaFX/OpenFX in JDK 1.9 and later, the libraries are
 * not included in the JDK. They may be installed manually in the OS or
 * automatically via Mill. The latter method has the advantage of acquiring
 * the paths of the libraries automatically and also setting up build the file
 * automatically. The easiest way to do this is to to use Mill's automatic
 * library dependency management (see #775# link below). Here we have an example
 * of using of Mill's managed library dependency setup.
 *
 * Note that in the case of the JavaFX libraries we must use set the JVM's
 * parameters to include the module path and module names. Other libraries, even
 * though provided as module may not require this. Most of the JVM parameter
 * set-up is automatic. It also allows to set-up module visibility and even
 * overriding certain modules on boot-up. This allows for example the use the
 * TestFX for use in headless UI testing.
 *
 * To add other libraries as modules see `controlsFXModule` as an example. 
 * 
 * ./mill mill.scalalib.GenIdea/idea
 * 
 * TODO: https://stackoverflow.com/questions/46616520/list-modules-in-jar-file
 *
 * @see https://github.com/com-lihaoyi/mill/pull/775#issuecomment-826091576
 */
trait OpenJFX extends JavaModule {

  // Modules 

  val BASE_       = s"base"
  val CONTROLS_   = s"controls"
  val FXML_       = s"fxml"
  val GRAPHICS_   = s"graphics"
  val MEDIA_      = s"media"
  val SWING_      = s"swing"
  val WEB_        = s"web"
  val CONTROLSFX_ = s"controlsfx"

  // Extra modules
  // Note that the module name and the library name are not the same
  val controlsFXModule = "org.controlsfx.controls"

  // Module libraries 
  val BASE       = s"org.openjfx:javafx-${BASE_}:$javaFXVersion"
  val CONTROLS   = s"org.openjfx:javafx-${CONTROLS_}:$javaFXVersion"
  val FXML       = s"org.openjfx:javafx-${FXML_}:$javaFXVersion"
  val GRAPHICS   = s"org.openjfx:javafx-${GRAPHICS_}:$javaFXVersion"
  val MEDIA      = s"org.openjfx:javafx-${MEDIA_}:$javaFXVersion"
  val SWING      = s"org.openjfx:javafx-${SWING_}:$javaFXVersion"
  val WEB        = s"org.openjfx:javafx-${WEB_}:$javaFXVersion"
  val CONTROLSFX = s"org.controlsfx:${CONTROLSFX_}:$controlsFXVersion"

  // OpenFX/JavaFX libraries
  val javaFXModuleNames = Seq(BASE_, CONTROLS_, FXML_, GRAPHICS_, MEDIA_, SWING_, WEB_)


  /* TODO: we need a better way to identify modules in the JARs
  see: https://stackoverflow.com/questions/46616520/list-modules-in-jar-file
  see: https://www.daniweb.com/programming/software-development/threads/291837/best-way-executing-jar-from-java-code-then-killing-parent-java-code
  see: https://in.relation.to/2017/12/06/06-calling-jdk-tools-programmatically-on-java-9/
  see: https://www.pluralsight.com/guides/creating-opening-jar-files-java-programming-language
  see: https://stackoverflow.com/questions/320510/viewing-contents-of-a-jar-file
  see: https://www.baeldung.com/java-compress-and-uncompress
  see: https://github.com/srikanth-lingala/zip4j
  see: https://mkyong.com/java/how-to-compress-files-in-zip-format/
  // List of modules (note that a single Jar may have ore than one module)
  val modules = javaFXModuleNames.map(n => n -> s"org.openjfx:javafx-$n:$javaFXVersion") // OpenFX
                                  .toMap 
                ++  // Other modules
                Map( "controlsfx" -> s"org.controlsfx:controlsfx:$controlsFXVersion")    // ControlsFX
  println(modules)
  */

  // The osAll module downloads these OS native versons of the libraries
  val supported = Set("mac", "linux", "win")
  // Get the name of the current (host) OS
  val osName = coursier.core.Activation.Os.fromProperties(sys.props.toMap).name.get.toLowerCase
  // Filter for removing incompatible native OS libraries
  // If we have several narive libraries for diffremt OS, JavaFX cannot select the correct one
  val tag = osName match {
    case "linux" => "linux"
    case "mac os x" => "mac"
    case "windows" => "win"
  }
  val remove = supported - tag

  def validOS(artifact: String): Boolean = {
    if (supported.exists(s => artifact.contains(s))) {
      // Not a native OS Jar for this OS
      println(s"?????????????????? => $artifact")
      //val t = remove.exists(s => artifact.contains(s))
      val t = artifact.contains( tag )
      println(t)
      t
    } else {
      // Not a native OS Jar
      true
    }
  }

  /**
   * In order to use OS specific libraries (such as JavaFX or OpenJFX), we
   * must set-up the OS flags appropriately for Maven download via Coursier.
   * This is only available **after** version **0.9.6** of Mill.
   *
   * @see https://github.com/com-lihaoyi/mill/pull/775 (commit ab4d61a)
   * @return OS specific resolution mapping
   */
   override def resolutionCustomizer: Task[Option[Resolution => Resolution]] = T.task {
     Some((_: coursier.core.Resolution).withOsInfo(coursier.core.Activation.Os.fromProperties(sys.props.toMap)))
   }

  val pathSeparator= File.pathSeparator

  /**
   * Here we setup the Java modules so that they can be loaded prior to
   * application boot. We can indicate which modules are visible and even opt
   * to substitute some of those. For example using TestFX to allow for headless
   * testing.
   *
   * Note that with managed libraries, we may pull in additional modules. So we
   * attempt here to identify (via naming convention), which libraries are modules.
   * These corresponding modules are then added to the JVM command line. 
   * 
   * @return the list of parameters for the JVM
   */
  override def forkArgs: Target[Seq[String]] = T {
    // get the managed libraries
    val allLibs: Loose.Agg[PathRef] = runClasspath()
    // get the OpenJFX and related managed libraries
    val s: Loose.Agg[String] = allLibs.map(_.path.toString())
                                      .filter{
                                         s =>
                                           val t= s.toLowerCase()
                                           t.contains("javafx") || t.contains("controlsfx")
                                        }

    // Create the JavaFX module names (convention is amenable to automation)
    import scala.util.matching.Regex

    // First get the javaFX only libraries
    val javaFXLibs = raw".*javafx-(.+?)-.*".r
    val javaFXModules = s.iterator.map(m => javaFXLibs.findFirstMatchIn(m).map(_.group(1)) )
                      .toSet
                      .filter(_.isDefined)
                      .map(_.get)
    // Now generate the module names
    val modulesNames = javaFXModules.map( m => s"javafx.$m") ++
                          Seq(controlsFXModule) // no standard convention, so add it manually

    // Add to the modules list
    val t = Seq(
        "--module-path", s.filter( validOS ).iterator.mkString( pathSeparator ),
        "--add-modules", modulesNames.iterator.mkString(","), // "javafx.controls,javafx.graphics,javafx.base,org.controlsfx.controls",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    ) ++
      // add standard parameters
      Seq("-Dprism.verbose = true", "-ea")
    println(t.mkString(";\n"))
    t
  }


  // // TODO: after version 0.10.0 of Mill put test in the managed/unmanaged classes
  // object test extends Tests {

  //   // // TODO: after version 0.10.0 of Mill remove this
  //   // // sse https://github.com/com-lihaoyi/mill/issues/1406
  //   // override def resolutionCustomizer: Task[Option[Resolution => Resolution]] = T.task {
  //   //   Some((_: coursier.core.Resolution).withOsInfo(coursier.core.Activation.Os.fromProperties(sys.props.toMap)))
  //   // }

  //   // https://github.com/com-lihaoyi/mill#097---2021-05-14
  //   //def testFrameworks = Seq(ivyMunitInterface)
  //   def ivyDeps = Agg(ivyMunit)
  //   def testFramework = ivyMunitInterface
  // }

}


object managed extends OpenJFX with ScalaModule {
  def scalaVersion = T{ ScalaVersion }

  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  /**
   * We setup JavaFX using managed libraries pretty much as any other library.
   * However, we must use the [[resolutionCustomizer]] to ensure that the proper
   * OS dependent libraries are correctly downloaded. In order to use JavaFX or
   * OpenJFX we must include these libraries in the module path and module names
   * to the JVM parameters. This has been automate via [[forkArgs]].
   *
   * Here we list the dependencies via the Mill `ivy` macro (uses Coursier). We
   * could automate this too because the naming of the libraries and models uses
   * a consistent convention. We leave that as an exercise to the reader.
   *
   * Note that any dependencies are loaded automatically so no need to add
   * all the JavaFX libraries. We have these here as an example.
   *
   * @return an aggregation of the dependencies
   */
  override def ivyDeps = Agg(
                              ivy"$CONTROLS",
                              ivy"$CONTROLSFX"
                              //ivy"${modules(CONTROLS_)}",
                              //ivy"${modules(CONTROLSFX_)}",
                             )

    object test extends Tests {
      override def ivyDeps = Agg(ivyMunit)
      override def testFramework = ivyMunitInterface
    }

}



/**
 * When working with JavaFX/OpenFX in JDK 1.9 and later, the libraries are
 * not included in the JDK. They may be installed manually in the OS or
 * automatically via Mill. The latter method has the advantage of acquiring
 * the paths of the libraries automatically and also setting up build the file
 * automatically. The easiest way to do this is to to use Mill's automatic
 * library dependency management (see #775# link below). Here we exemplify the
 * use of Mill's unmanaged library dependency setup. Any other libraries
 * may still be used via Mill's managed library setup.
 *
 * Note that in the case of the JavaFX libraries we must use/set the JVM's
 * parameters to include the module path and module names. Other libraries, even
 * though provided as module may not require this. Most of the JVM parameter
 * set-up is automatic. It also allows to set-up module visibility and even
 * overriding certain modules on boot-up. This allows for example the use the
 * TestFX for use in headless UI testing.
 *
 * @see https://github.com/com-lihaoyi/mill/pull/775#issuecomment-826091576
 */
object unmanaged extends OpenJFX with ScalaModule {


  def scalaVersion = T{ ScalaVersion }

  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  /**
   * Here we manually download the modules' jars. No need to install them
   * separately in the OS. This allows us to determine the paths to the
   * libraries so they can be used later in the JVM parameters. Note that this
   * is a Mill command that is cached, so it can be called repeatedly.
   *
   * Managed libraries can also be used by overriding `ivyDeps`
   * 
   * @return List of path references to the libraries
   * 
   * @see https://github.com/coursier/coursier/discussions/2401
   * @see https://github.com/com-lihaoyi/mill/discussions/1842
   */
  override def unmanagedClasspath: Target[Loose.Agg[PathRef]] = T{
    import coursier._
    import coursier.parse.DependencyParser

    // Extra OpenFX library
    // Coursier: only a single String literal is allowed here, so cannot decouple version
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    val controlsFXModule = dep"org.controlsfx:controlsfx:11.1.0"

    // Generate the dependencies
    val javaFXModuleNames = Seq( CONTROLS_ )
    val javaFXModules = javaFXModuleNames.map(
      m => Dependency(Module(org"org.openjfx", ModuleName(s"javafx-$m")), javaFXVersion)
    ) ++
      Seq(controlsFXModule)
      
    // Check if the libraries exist and download if they don't
    val files = Fetch()
                  .addDependencies(javaFXModules: _*)
                  .addArtifactTypes(Type.all)
                  .run()
    // Return the list of libraries
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

    object test extends Tests {
      override def ivyDeps = Agg(ivyMunit)
      override def testFramework = ivyMunitInterface
    }

}



/**
 * This module show how one can download the native OS binaries for multiple
 * operating systems. This allows one to create "fat" Jars so that the resulting
 * application is cross platform. In other words it supports multple plartforms.
 * Be aware that this signficiantly increases the applicaton's size.
 * Alterantivelly use something like Coursier to make your application available.
 *
 * @see https://get-coursier.io/
 *      https://github.com/coursier/coursier
 */
object allOS extends OpenJFX with ScalaModule {


  def scalaVersion = T{ ScalaVersion }

  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  override def ivyDeps = Agg( ivy"$CONTROLS", ivy"$CONTROLSFX" )

  import coursier._
  import coursier.core.{Activation, Resolution}

  /**
   * Resolution parameter for macOS operating system
   */
  val macOSx64 =Activation.Os(
    Some("x86_64"),       // same as amd64 or x86-64
    Set("mac", "unix"),
    Some("mac os x"),
    None
  )

  /**
   * Resolution parameter for Windows operating system.
   * Alternate form of defining this parameter:
   *
   * {{
   *     val windowsOs = core.Activation.Os.fromProperties(Map(
   *       "os.name"        -> "Windows 10",
   *       "os.arch"        -> "amd64",
   *       "os.version"     -> "10.0",
   *       "path.separator" -> ";"
   *     ))
   * }}
   *
   */
  val winX64 =Activation.Os(
    Some("x86_64"),
    Set("windows"),
    Some("windows"),
    None
  )

  /**
   * Resolution parameter for Windows operating system.
   * Alternate form of defining this parameter:
   *
   * {{
   *     val windowsOs = coursier.core.Activation.Os.fromProperties(Map(
   *             "os.name"        -> "Linux",
   *             "os.arch"        -> "amd64",
   *             "os.version"     -> "4.9.125",
   *             "path.separator" -> ":"
   *         ))
   * }}
   *
   * [[coursier.core.Activation.Os]] defintion:
   * OS( arch : Option[String],
   *     families : Set[String],
   *     name : Option[String],
   *     version : Option[String]) :
   *
   * Determining the full OS member values:
   *
   * {{
   *    val current = coursier.core.Activation.Os.fromProperties(sys.props.toMap)
   * }}
   *
   * Output example:
   *   Os(Some(amd64), HashSet(unix), Some(linux), Some(5.13.0-40-generic))
   */
  val linuxX64 =Activation.Os(
    Some("x86_64"),
    Set("unix"),
    Some("linux"),
    None
  )

  /**
   * Setup the Ivy resolution for the Maven artifacts for windows.
   * Here is an example for MacOS:
   *
   * {{
   *   val resolveMac = Resolve()
   *     .noMirrors
   *     .withCache(cache)
   *      .withResolutionParams(
   *        ResolutionParams()
   *          .withOsInfo {
   *            Activation.Os(
   *              Some("x86_64"),
   *              Set("mac", "unix"),
   *              Some("mac os x"),
   *              Some("10.15.1")
   *            )
   *          }
   *          .withJdkVersion("1.8.0_121")
   *      )
   * }}
   *
   *
   */
  val resolveWin = Resolve()
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          winX64
        }
    )

  /**
   * Setup the Ivy resolution for the Maven artifacts for MacOS.
   */
  val resolveMac = Resolve()
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          macOSx64
        }
    )

  /**
   * Setup the Ivy resolution for the Maven artifacts for Linux.
   */
  val resolveLinux = Resolve()
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          linuxX64
        }
    )

  /**
   * Here we manually download the modules' jars. No need to install them
   * separately in the OS. This allows us to determine the paths to the
   * libraries so they can be used later in the JVM parameters. Note that this
   * is a Mill command that is cached, so it can be called repeatedly.
   *
   * Managed libraries can also be used by overriding `ivyDeps`
   * 
   * @return List of path references to the libraries
   * 
   * @see https://github.com/coursier/coursier/discussions/2401
   * @see https://github.com/com-lihaoyi/mill/discussions/1842
   */
  override def unmanagedClasspath: Target[Loose.Agg[PathRef]] = T{

    import coursier.params.ResolutionParams

    // Get the name of the current (host) OS
    //val props = sys.props.toMap
    //val osName = props("os.name")
    val osName = coursier.core.Activation.Os.fromProperties(sys.props.toMap).name.get

    // Extra OpenFX library
    // Coursier: only a single String literal is allowed here, so cannot decouple version
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    val controlsFXModule = dep"org.controlsfx:controlsfx:11.1.0"

    // Generate the dependencies
    val javaFXModuleNames = Seq( CONTROLS_ )
    val javaFXModules = javaFXModuleNames.map(
      m => Dependency(Module(org"org.openjfx", ModuleName(s"javafx-$m")), javaFXVersion)
    ) ++
      Seq(controlsFXModule)

    // Setup resolution Windows downloads (if not current OS)
    val filesWin =
      if (osName != winX64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams( ResolutionParams().withOsInfo{ winX64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set[File]()

    // Setup resolution MacOS downloads (if not current OS)
    val filesMac =
      if (osName != macOSx64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams(ResolutionParams().withOsInfo { macOSx64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set[File]()

    // Setup resolution Linux downloads (if not current OS)
    val filesLinux =
      if (osName != linuxX64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams(ResolutionParams().withOsInfo { linuxX64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set[File]()

    val allOS = filesWin ++ filesMac ++ filesLinux
    val files = allOS.toSeq

    // Return the list of libraries
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

  def osClasspath: Target[Seq[String]] = T{
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    // Extra OpenFX library
    // Coursier: only a single String literal is allowed here, so cannot decouple version
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    val controlsFXModule = dep"org.controlsfx:controlsfx:11.1.0"

    val current = coursier.core.Activation.Os.fromProperties(sys.props.toMap)
    // Generate the dependencies
    val javaFXModules = javaFXModuleNames.map(
      m => Dependency(Module(org"org.openjfx", ModuleName(s"javafx-$m")), javaFXVersion)
    ) ++
      Seq(controlsFXModule)

    val deps = javaFXModules
    val resWin: Future[Resolution] =
                          resolveWin
                              .addDependencies(deps: _*)
                              .future()
    val resMac: Future[Resolution] =
                          resolveMac
                              .addDependencies(deps: _*)
                              .future()
   val resLinux: Future[Resolution] =
      resolveLinux
        .addDependencies(deps: _*)
        .future()
   val res = Future.sequence( List(resWin, resMac, resLinux) )
   val result = Await.result(res, Duration.Inf)
   val urls = result.map(_.dependencyArtifacts().map(_._3.url).toSet).reduceLeft((acc,s) => acc ++ s)

   s"Current = $current" :: urls.toList
  }

  object test extends Tests {
    override def ivyDeps = Agg(ivyMunit)
    override def testFramework = ivyMunitInterface
  }

}



/**
  * Same as the `managed`target, but here we just use Java. 
  */
object javafx extends OpenJFX {
  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  /**
   * We setup JavaFX using managed libraries pretty much as any other library.
   * However, we must use the [[resolutionCustomizer]] to ensure that the proper
   * OS dependent libraries are correctly downloaded. In order to use JavaFX or
   * OpenJFX we must include these libraries in the module path and module names
   * to the JVM parameters. This has been automate via [[forkArgs]].
   *
   * Here we list the dependencies via the Mill `ivy` macro (uses Coursier). We
   * could automate this too because the naming of the libraries and models uses
   * a consistent convention. We leave that as an exercise to the reader.
   *
   * Note that any dependencies are loaded automatically so no need to add
   * all the JavaFX libraries. We have these here as an example.
   *
   * @return an aggregation of the dependencies
   */
  override def ivyDeps = Agg(
                              ivy"$CONTROLS",
                              ivy"$CONTROLSFX"
                              //ivy"${modules(CONTROLS_)}",
                              //ivy"${modules(CONTROLSFX_)}",
                             )

}

