package button

/**
 *
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i managed.runMain button.Main
 * ./mill -i --watch managed.runMain button.Main
 *
 * @see https://stackoverflow.com/questions/12124657/getting-started-on-scala-javafx-desktop-application-development
 */
object Main {
  def main(args: Array[String]) =
    val app = new ButtonApp
    app.launchIt()

}