/* cspell: disable-next-line */
package unmanaged

// cSpell:ignore splotly, munit

/**
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i unmanaged.test
 * ./mill -i unmanaged.test.testLocal
 * ./mill -i unmanaged.test unmanaged.PlotSpec.*
 * ./mill -i unmanaged.test unmanaged.PlotSpec.test1
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


