package button;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;

class ButtonApp extends Application {

  override def start(stage: Stage) = {
    System.out.println("STARTED un-managed Scala!")
    var button = new Button("Please un-managed Scala Click Me!")
    button.setOnAction(e => stage.close())
    var box = new VBox()
    box.getChildren().add(button)
    var scene = new Scene(box)
    stage.setScene(scene)
    stage.setTitle("Just an un-managed Scala button")
    stage.show();
  }

  def launchIt():Unit={
    Application.launch()
  }

}