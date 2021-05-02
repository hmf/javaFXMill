package button;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;

public class ButtonApp extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override public void start(Stage stage) {
        System.out.println("STARTED!");
        var button = new Button("Please Click Me!");
        button.setOnAction(e -> stage.close());
        var box = new VBox();
        box.getChildren().add(button);
        var scene = new Scene(box);
        stage.setScene(scene);
        stage.setTitle("Just a button");
        stage.show();
    }
}