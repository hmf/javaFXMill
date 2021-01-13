import mill._, scalalib._

val javaFXVersion = "13.0.2"

object javafx extends JavaModule {
  def mainClass = Some("Main")
  def ivyDeps = Agg(ivy"org.openjfx:javafx-controls:$javaFXVersion")
}