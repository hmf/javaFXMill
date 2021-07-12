package button;

/**
 *
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i unmanaged.run
 * ./mill -i unmanaged.runMain helloworld.HelloWorld
 * ./mill -i --watch unmanaged.run
 */
public class Main {

    public static void main(String[] args) {
        ButtonApp.main(args);
    }
}