package button;

/**
 *
 * ./mill mill.scalalib.GenIdea/idea
 *
 * ./mill -i javafx.runMain button.Main
 * ./mill -i --watch javafx.runMain button.Main
 *
 * @see https://stackoverflow.com/questions/12124657/getting-started-on-scala-javafx-desktop-application-development
 */
public class Main {

    public static void main(String[] args) {
        ButtonApp.main(args);
    }
}