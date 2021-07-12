/* cspell: disable-next-line */
package managed

// cSpell:ignore splotly, munit
import gg.data
import gg.map
import gg.widget
import gg.compiler
import gg.Experiment4._

/**
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i managed.test
 * ./mill -i managed.test.testLocal
 * ./mill -i managed.test splotly.PlotSpec.*
 * ./mill -i managed.test splotly.PlotSpec.test1
 *
 * Extending `TestCase` to get access to `setUp`
 *
 * https://github.com/sbt/junit-interface#junit-interface
 * ./mill -i managed.test --tests=test1           Only matches test names (not classes)
 *
 *
 */
class PlotSpec extends munit.FunSuite {

  test("hello") {
    val obtained = 42
    val expected = 43
    assertEquals(obtained, expected)
  }

}


