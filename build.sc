import mill._, scalalib._

val javaFXVersion = "13.0.2"

object javafx extends JavaModule {
  override def mainClass = Some("Main")

  /**
   * From https://github.com/guilgaly/itunes-dap-sync/blob/master/dependencies.sc
   * @param dep
   */
  implicit class WithOsClassifier(dep: Dep) {
    def withOsClassifier =
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

  // Classifier is correct
  //println(javaFXDeps.mkString(",\n"))

  override def ivyDeps = T{ Agg(javaFXDeps:_*) }
}