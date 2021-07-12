package button;

/**
 *
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i managed.run
 * ./mill -i managed.runMain helloworld.HelloWorld
 * ./mill -i --watch managed.run
 */
public class Main {

    public static void main(String[] args) {
        ButtonApp.main(args);
    }
}