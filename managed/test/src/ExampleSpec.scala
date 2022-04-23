/* cspell: disable-next-line */
package managed

// cSpell:ignore splotly, munit

/**
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i managed.test
 * ./mill -i managed.test.testLocal
 * ./mill -i managed.test managed.ExampleSpec.*
 * ./mill -i managed.test managed.ExampleSpec.test1
 *
 * Extending `TestCase` to get access to `setUp`
 *
 * https://github.com/sbt/junit-interface#junit-interface
 * ./mill -i managed.test --tests=test1           Only matches test names (not classes)
 *
 *
 */
class ExampleSpec extends munit.FunSuite {

  test("test_ok") {
    val obtained = 42
    val expected = 42
    assertEquals(obtained, expected)
  }

  test("test_fails") {
    val obtained = 42
    val expected = 43
    assertEquals(obtained, expected)
  }

}


