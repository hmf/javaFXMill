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

  // TODO: after version 0.10.0 iof Mill put test in the managed/unmanaged classes
  val ivyMunit = ivy"org.scalameta::munit::$mUnitVersion"
  val ivyMunitInterface = "munit.Framework"

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
    Seq(
        "--module-path", s.iterator.mkString( pathSeparator ), 
        "--add-modules", modulesNames.iterator.mkString(","),
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    ) ++
      // add standard parameters
      Seq("-Dprism.verbose = true", "-ea")
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
object allOS extends OpenJFX with ScalaModule {


  def scalaVersion = T{ ScalaVersion }

  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  // TODO: remove
  //override def ivyDeps = Agg( ivy"org.scala-lang.modules:scala-async_2.12:1.0.1" )
  //override def ivyDeps = Agg( ivy"org.scala-lang.modules::scala-async:1.0.1" )

  import coursier._
  import coursier.parse.DependencyParser
  import coursier.core.{Activation, Publication, Resolution}

  val macOSx64 =Activation.Os(
    Some("x86_64"),       // same as amd64 or x86-64
    Set("mac", "unix"),
    Some("mac os x"),
    None
  )

  /*

        val windowsOs = core.Activation.Os.fromProperties(Map(
          "os.name"        -> "Windows 10",
          "os.arch"        -> "amd64",
          "os.version"     -> "10.0",
          "path.separator" -> ";"
        ))

  */
  val winX64 =Activation.Os(
    Some("x86_64"),
    Set("windows"),
    Some("windows"),
    None
  )

  /*
              .withOsInfo(coursier.core.Activation.Os.fromProperties(Map(
                "os.name"        -> "Linux",
                "os.arch"        -> "amd64",
                "os.version"     -> "4.9.125",
                "path.separator" -> ":"
              ))))

       OS( arch : scala.Option[_root_.scala.Predef.String],
          families : _root_.scala.Predef.Set[_root_.scala.Predef.String],
          name : scala.Option[_root_.scala.Predef.String],
          version : scala.Option[_root_.scala.Predef.String]) : coursier.core.Activation.Os =
   val current = coursier.core.Activation.Os.fromProperties(sys.props.toMap)
   Os(Some(amd64), HashSet(unix), Some(linux), Some(5.13.0-40-generic))
  */
  val linuxX64 =Activation.Os(
    Some("x86_64"),
    Set("unix"),
    Some("linux"),
    None
  )

  // TODO: testing
  val resolveWin = Resolve()
    //.noMirrors
    //.withCache(cache)
    // .withResolutionParams(
    //   ResolutionParams()
    //     .withOsInfo {
    //       Activation.Os(
    //         Some("x86_64"),
    //         Set("mac", "unix"),
    //         Some("mac os x"),
    //         Some("10.15.1")
    //       )
    //     }
    //     //.withJdkVersion("1.8.0_121")
    // )
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          winX64
        }
      //.withJdkVersion("1.8.0_121")
    )

  val resolveMac = Resolve()
    //.noMirrors
    //.withCache(cache)
    // .withResolutionParams(
    //   ResolutionParams()
    //     .withOsInfo {
    //       Activation.Os(
    //         Some("x86_64"),
    //         Set("mac", "unix"),
    //         Some("mac os x"),
    //         Some("10.15.1")
    //       )
    //     }
    //     //.withJdkVersion("1.8.0_121")
    // )
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          macOSx64
        }
      //.withJdkVersion("1.8.0_121")
    )

  val resolveLinux = Resolve()
    //.noMirrors
    //.withCache(cache)
    // .withResolutionParams(
    //   ResolutionParams()
    //     .withOsInfo {
    //       Activation.Os(
    //         Some("x86_64"),
    //         Set("mac", "unix"),
    //         Some("mac os x"),
    //         Some("10.15.1")
    //       )
    //     }
    //     //.withJdkVersion("1.8.0_121")
    // )
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          linuxX64
        }
      //.withJdkVersion("1.8.0_121")
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

    import coursier.core.{Activation, Configuration, Extension, Reconciliation}
    import coursier.error.ResolutionError
    import coursier.ivy.IvyRepository
    import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
    import coursier.util.ModuleMatchers

    //implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    // Get the name of the current (host) OS
    val props = sys.props.toMap
    val osName = props("os.name")

    // Extra OpenFX library
    // Coursier: only a single String literal is allowed here, so cannot decouple version
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    val controlsFXModule = dep"org.controlsfx:controlsfx:11.1.0"

    // Generate the dependencies
    val javaFXModules = javaFXModuleNames.map(
      m => Dependency(Module(org"org.openjfx", ModuleName(s"javafx-$m")), javaFXVersion)
    ) ++
      Seq(controlsFXModule)

    val filesWin =
      if (osName != winX64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams( ResolutionParams().withOsInfo{ winX64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set()

    val filesMac =
      if (osName != macOSx64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams(ResolutionParams().withOsInfo { macOSx64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set()

    val filesLinux =
      if (osName != linuxX64.name.get) {
        Fetch()
        .addDependencies(javaFXModules: _*)
        .withResolutionParams(ResolutionParams().withOsInfo { linuxX64 })
        .addArtifactTypes(Type.all)
        .run()
        .toSet
      } else Set()

    val allOS = filesWin ++ filesMac ++ filesLinux

    val files = Fetch()
                  .addDependencies(javaFXModules: _*)
                  .addArtifactTypes(Type.all)
                  .run()
    // Return the list of libraries
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

  def osClasspath/*: Target[Loose.Agg[PathRef]]*/ = T{
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    import scala.async.Async.{async, await}
    import scala.collection.compat._

    // Extra OpenFX library
    // Coursier: only a single String literal is allowed here, so cannot decouple version
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    val controlsFXModule = dep"org.controlsfx:controlsfx:11.1.0"

    val current = coursier.core.Activation.Os.fromProperties(sys.props.toMap)
    println(s"Curremt ---------------> $current")

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
//    println("?????????????????????????????")
//    println(urls.mkString("\n,?"))

//    val t = async {
//      val deps = javaFXModules
//      val resWin: Resolution = await {
//        resolveWin
//          .addDependencies(deps: _*)
//          .future()
//      }
//      val resMac: Resolution = await {
//        resolveMac
//          .addDependencies(deps: _*)
//          .future()
//      }
//
//      val urls1 = resWin.dependencyArtifacts().map(_._3.url).toSet
//      println("?????????????????????????????")
//      println(urls1.mkString("\n,?"))
//
//      val urls2 = resMac.dependencyArtifacts().map(_._3.url).toSet
//      println("?????????????????????????????")
//      println(urls2.mkString("\n,?"))
//
//      urls1 ++ urls2
//    }

   urls
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

