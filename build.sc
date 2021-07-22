// cSpell:ignore scalalib, helloworld, coursier, Deps, unmanaged, classpath
// cSpell:ignore javafx, controlsfx, openjfx

import coursier.core.Resolution
import mill._
import mill.api.Loose
import mill.define.{Target, Task}
import scalalib._

val ScalaVersion = "3.0.1"

//val javaFXVersion = "11.0.2"
//val javaFXVersion = "12"
//val javaFXVersion = "13.0.2"
val javaFXVersion = "16"

val controlsFXVersion = "11.1.0"
val mUnitVersion = "0.7.27"

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
 * ./mill mill.scalalib.GenIdea/idea
 *
 * @see https://github.com/com-lihaoyi/mill/pull/775#issuecomment-826091576
 */
object managed extends ScalaModule with JavaModule {
  def scalaVersion = T{ ScalaVersion }

  val ivyMunit = ivy"org.scalameta::munit::0.7.27"
  val ivyMunitInterface = "munit.Framework"


  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

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

  /**
   * We setup JavaFX using managed libraries pretty much as any other library.
   * However, we must use the [[resolutionCustomizer]] to ensure that the proper
   * OS dependent libraries are correctly downloaded. In order to ise JavaFX or
   * OpenJFX we must include these libraries in the module path and module names
   * to the JVM parameters. We can automate this via [[forkArgs]].
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
                              /*ivy"org.openjfx:javafx-base:$javaFXVersion",
                              ivy"org.openjfx:javafx-controls:$javaFXVersion",
                              ivy"org.openjfx:javafx-fxml:$javaFXVersion",
                              ivy"org.openjfx:javafx-graphics:$javaFXVersion",
                              ivy"org.openjfx:javafx-media:$javaFXVersion",
                              ivy"org.openjfx:javafx-swing:$javaFXVersion",
                              ivy"org.openjfx:javafx-web:$javaFXVersion",*/
                              ivy"org.openjfx:javafx-controls:$javaFXVersion",
                              ivy"org.controlsfx:controlsfx:$controlsFXVersion"
                             )


  // OpenFX/JavaFX libraries
  //private lazy val javaFXModuleNames = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  // Extra OpenFX library
  private lazy val controlsFXModuleName = "org.controlsfx.controls"

  /**
   * Here we setup the Java modules so that they can be loaded prior to
   * application boot. We can indicate which modules are visible and even opt
   * to substitute some of those. For example using TestFX to allow for headless
   * testing.
   *
   * @return the list of parameters for the JVM
   */
  override def forkArgs: Target[Seq[String]] = T {
    // get the managed libraries
    val unmanaged: Loose.Agg[PathRef] = runClasspath()
    // get the OpenJFX and related managed libraries
    val s: Loose.Agg[String] = unmanaged.map(_.path.toString())
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
                          Seq(controlsFXModuleName) // no standard convention, so add it manually

    // Add to the modules list
    Seq(
        "--module-path", s.iterator.mkString(":"),
        "--add-modules", modulesNames.iterator.mkString(","),
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    ) ++
      // add standard parameters
      Seq("-Dprism.verbose = true", "-ea")
  }


  object test extends Tests {

    override def resolutionCustomizer: Task[Option[Resolution => Resolution]] = T.task {
      Some((_: coursier.core.Resolution).withOsInfo(coursier.core.Activation.Os.fromProperties(sys.props.toMap)))
    }
    
    //override def ivyDeps = Agg(ivyMunit)
    // sse https://github.com/com-lihaoyi/mill/issues/1406
    override def ivyDeps = Agg(
                              ivy"org.openjfx:javafx-controls:$javaFXVersion",
                              ivy"org.controlsfx:controlsfx:$controlsFXVersion",
                              ivyMunit)

    // https://github.com/com-lihaoyi/mill#097---2021-05-14
    //def testFrameworks = Seq(ivyMunitInterface)
    def testFramework = ivyMunitInterface
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
object unmanaged extends JavaModule {
  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  // OpenFX/JavaFX libraries
  private lazy val javaFXModuleNames = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  // Extra OpenFX library
  private lazy val controlsFXModuleName = "org.controlsfx.controls"

  /**
   * Here we manually download the modules' jars. No need to install them
   * separately in the OS. This allows us to determine the paths to the
   * libraries so they can be used later in the JVM parameters. Note that this
   * is a Mill command that is cached, so it can be called repeatedly.
   *
   * @return List of path references to the libraries
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
    val files = Fetch().addDependencies(javaFXModules: _*).run()
    // Return the list f libraries
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

  /**
   * Here we setup the Java modules so that they can be loaded prior to
   * application boot. We can indicate which modules are visible and
   * even opt to substitute some of those. For example using TestFX to allow
   * for headless testing.
   *
   * @return the list of parameters for the JVM
   */
  override def forkArgs: Target[Seq[String]] = T {
    // get the unmanaged libraries
    val unmanaged: Loose.Agg[PathRef] = runClasspath() // unmanagedClasspath()
    // get the OpenJFX unmanaged libraries
    val s: Loose.Agg[String] = unmanaged.map(_.path.toString())
      .filter{
        s =>
          val t= s.toLowerCase()
          t.contains("javafx") || t.contains("controlsfx")
      }
    val modulesNames = javaFXModuleNames.map( m => s"javafx.$m") ++ Seq(controlsFXModuleName)
    Seq(
      "--module-path", s.iterator.mkString(":"),
      "--add-modules", modulesNames.iterator.mkString(","),
      "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
      "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls",
      "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    ) ++
      // add standard parameters
      Seq("-Dprism.verbose = true", "-ea")
  }
}


/**
  * Same as the `managed`target, but here we just use Java. 
  */
  object javafx extends JavaModule {
    override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

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

  /**
   * We setup JavaFX using managed libraries pretty much as any other library.
   * However, we must use the [[resolutionCustomizer]] to ensure that the proper
   * OS dependent libraries are correctly downloaded. In order to ise JavaFX or
   * OpenJFX we must include these libraries in the module path and module names
   * to the JVM parameters. We can automate this via [[forkArgs]].
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
                              /*ivy"org.openjfx:javafx-base:$javaFXVersion",
                              ivy"org.openjfx:javafx-controls:$javaFXVersion",
                              ivy"org.openjfx:javafx-fxml:$javaFXVersion",
                              ivy"org.openjfx:javafx-graphics:$javaFXVersion",
                              ivy"org.openjfx:javafx-media:$javaFXVersion",
                              ivy"org.openjfx:javafx-swing:$javaFXVersion",
                              ivy"org.openjfx:javafx-web:$javaFXVersion",*/
                              ivy"org.openjfx:javafx-controls:$javaFXVersion",
                              ivy"org.controlsfx:controlsfx:$controlsFXVersion"
                             )


  // OpenFX/JavaFX libraries
  //private lazy val javaFXModuleNames = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  // Extra OpenFX library
  private lazy val controlsFXModuleName = "org.controlsfx.controls"

  /**
   * Here we setup the Java modules so that they can be loaded prior to
   * application boot. We can indicate which modules are visible and even opt
   * to substitute some of those. For example using TestFX to allow for headless
   * testing.
   *
   * @return the list of parameters for the JVM
   */
  override def forkArgs: Target[Seq[String]] = T {
    // get the managed libraries
    val unmanaged: Loose.Agg[PathRef] = runClasspath()
    // get the OpenJFX and related managed libraries
    val s: Loose.Agg[String] = unmanaged.map(_.path.toString())
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
                          Seq(controlsFXModuleName) // no standard convention, so add it manually

    // Add to the modules list
    Seq(
        "--module-path", s.iterator.mkString(":"),
        "--add-modules", modulesNames.iterator.mkString(","),
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    ) ++
      // add standard parameters
      Seq("-Dprism.verbose = true", "-ea")
  }

}
