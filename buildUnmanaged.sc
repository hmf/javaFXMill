// cSpell:ignore scalalib, helloworld, coursier, Deps, unmanaged, classpath
// cSpell:ignore javafx, controlsfx, openjfx

import mill._
import mill.api.Loose
import mill.define.Target
import scalalib._

//val javaFXVersion = "11.0.2"
//val javaFXVersion = "12"
//val javaFXVersion = "13.0.2"
val javaFXVersion = "16"

val controlsFXVersion = "11.1.0"

/**
 * When working with JavaFX/OpenFX in JDK 1.9 and later, the libraries are
 * not included in the JDK. They may be installed manually in the OS or
 * automatically via Mill. The latter method hss the advantage of acquiring
 * the paths of the libraries automatically and also setting up build the file
 * automatically. The easiest way to do this is to to use Mill's automatic
 * library dependency management (see #775# link below). Here we example the
 * use of Mill's unmanaged library dependency setup. It has the advantage
 * of being able to set-up module visibility and even overriding certain
 * modules on boot-up. This allows for example the use the TestFX for use
 * in headless UI testing.
 *
 * @see https://github.com/com-lihaoyi/mill/pull/775#issuecomment-826091576
 */
object javafx extends JavaModule {
  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  // OpenFX/JavaFX libraries
  private lazy val javaFXModuleNames = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  // Extra OpenFX library
  private lazy val controlsFXModuleName = "org.controlsfx.controls"

  /**
   * Here we manually download the modules' jars. No need to install them
   * separately in the OS. This allows us to determine the paths to the
   * libraries so they can be used later. Note that this is a Mill command
   * that is cached, so it can be called repeatedly.
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
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

  /**
   * Here we setup the Java modules so that they can be loaded prior to
   * application boot. Here we can indicate which modules are visible and
   * even opt to substitute some of those. For example using TestFX to allow
   * for headless testing.
   *
   * @return the list of parameters for the JVM
   */
  override def forkArgs: Target[Seq[String]] = T {
    // get the unmanaged libraries
    val unmanaged: Loose.Agg[PathRef] = unmanagedClasspath()
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
    )
  }
}