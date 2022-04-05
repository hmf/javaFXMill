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

object javafx extends JavaModule {
  override def mainClass: T[Option[String]] = Some("helloworld.HelloWorld")

  /**
   * From https://github.com/guilgaly/itunes-dap-sync/blob/master/dependencies.sc
   * @param dep
   */
  implicit class WithOsClassifier(dep: Dep) {
    def withOsClassifier: Dep =
      dep.configure(
        coursier.Attributes(
          classifier = coursier.Classifier(WithOsClassifier.osName),
        ),
      )
  }

  /**
   * From https://github.com/guilgaly/itunes-dap-sync/blob/master/dependencies.sc
   */
  object WithOsClassifier {
    val osName = System.getProperty("os.name") match {
      case name if name.startsWith("Linux")   => "linux"
      case name if name.startsWith("Mac")     => "mac"
      case name if name.startsWith("Windows") => "win"
      case _                                  => throw new Exception("Unknown platform")
    }
  }

  // Does not help
  //System.setProperty("javafx.platform", WithOsClassifier.osName)

  private lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  private lazy val javaFXDeps = javaFXModules.map{ m =>
    ivy"org.openjfx:javafx-$m:$javaFXVersion".withOsClassifier
  }

  //override def ivyDeps = T{ Agg(javaFXDeps:_*) }

  // Classifier is correct
  //println(javaFXDeps.mkString(",\n"))

  // https://github.com/com-lihaoyi/mill/pull/775#issuecomment-826091576
  override def unmanagedClasspath = T{
    import coursier._
    import coursier.parse.DependencyParser
    val javaFXModuleNames = List("base", "controls", "fxml", "graphics", "media", "swing", "web")
    //val controlsFXModuleName: Dependency = dep"org.controlsfx:controlsfx:11.1.0"
    // https://github.com/coursier/coursier/blob/d5ad55d1dcb025084ba9bd994ea47ceae0608a8f/modules/coursier/shared/src/main/scala/coursier/util/StringInterpolators.scala#L54
    // https://github.com/coursier/coursier/blob/d5ad55d1dcb025084ba9bd994ea47ceae0608a8f/modules/coursier/shared/src/main/scala/coursier/util/StringInterpolators.scala#L65
    // https://github.com/coursier/coursier/blob/d5ad55d1dcb025084ba9bd994ea47ceae0608a8f/modules/coursier/shared/src/main/scala/coursier/util/StringInterpolators.scala#L90
    // Only a single String literal is allowed here
    val controlsFXModuleName = dep"org.controlsfx:controlsfx:$controlsFXVersion"
    //val controlsFXModuleName = s"org.controlsfx:controlsfx:$controlsFXVersion"
    //val controlsFXModuleName = "org.controlsfx:controlsfx:"
    val javaFXModules = javaFXModuleNames.map(
                            m => Dependency(Module(org"org.openjfx", ModuleName(s"javafx-$m")), javaFXVersion)
                         ) ++
                         //Seq(dep"org.controlsfx:controlsfx:11.0.2")
                         Seq(controlsFXModuleName)
                         //Seq(StringContext(controlsFXModuleName).dep(controlsFXVersion))

    val files = Fetch().addDependencies(javaFXModules: _*).run()
    files.foreach( f => println(f))
    val pathRefs = files.map(f => PathRef(os.Path(f)))
    Agg(pathRefs : _*)
  }

/*
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-base/12/javafx-base-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-controls/12/javafx-controls-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-fxml/12/javafx-fxml-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-graphics/12/javafx-graphics-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-media/12/javafx-media-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-swing/12/javafx-swing-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-web/12/javafx-web-12.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0/controlsfx-11.1.0.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-base/12/javafx-base-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-controls/12/javafx-controls-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-fxml/12/javafx-fxml-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-graphics/12/javafx-graphics-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-media/12/javafx-media-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-swing/12/javafx-swing-12-linux.jar
/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-web/12/javafx-web-12-linux.jar
*/

  val files = Seq(
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-base/12/javafx-base-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-controls/12/javafx-controls-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-fxml/12/javafx-fxml-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-graphics/12/javafx-graphics-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-media/12/javafx-media-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-swing/12/javafx-swing-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-web/12/javafx-web-12.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0/controlsfx-11.1.0.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-base/12/javafx-base-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-controls/12/javafx-controls-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-fxml/12/javafx-fxml-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-graphics/12/javafx-graphics-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-media/12/javafx-media-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-swing/12/javafx-swing-12-linux.jar",
               "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx/javafx-web/12/javafx-web-12-linux.jar"
             )

  override def forkArgs: Target[Seq[String]] = T {
    // get the unmanaged libraries
    val unmanaged: Loose.Agg[PathRef] = unmanagedClasspath()
    // get the OpenJFX unmanaged libraries
    val s: Loose.Agg[String] = unmanaged.map(_.path.toString())
                                        .filter{s =>
                                          val t= s.toLowerCase()
                                          t.contains("javafx") || t.contains("controlsfx")
                                        }
    println(s.items.mkString(";\n"))

    /*val unmanaged: Target[Loose.Agg[PathRef]] = unmanagedClasspath()
    val t = unmanaged.map{
      e =>
        val t = e.map(_.path.toString())
        println(t)
        t
    }
    println("!!!!!!!!!!!!!!!!!!!!!!")
    //println(t)
    t()
    t.map{
      p =>
        println("?????")
        println(p.indexed.toList.mkString(";\n"))
    }*/
    Seq(
    //"--module-path", "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/openjfx" + ":" + "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0/controlsfx-11.1.0.jar",
    // "--module-path", files.mkString(":") + ":" + "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0/controlsfx-11.1.0.jar",
    "--module-path", s.mkString(":") + ":" + "/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0/controlsfx-11.1.0.jar",
    "--add-modules", "javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web,org.controlsfx.controls",
    "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
    "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls", 
    "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
    )
  }

  /*
  def forkArgs = Seq(
    "--module-path", sys.env("JAVAFX_HOME") + ":" + "/Users/ajr/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/controlsfx/controlsfx/11.1.0",
    "--add-modules", "javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web,org.controlsfx.controls",
    "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=org.controlsfx.controls",
    "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=org.controlsfx.controls", 
    "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls"
  )*/ 
}