package hu.ppke.itk.pakge2;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {
    /**
     * Elkészíti a grafikus kezelői felületet
     * @param primaryStage Maga az ablak
     */
    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();
        root.setAlignment(Pos.TOP_CENTER);
        LabyrinthView labyrinthView = new LabyrinthView(root);
        primaryStage.setTitle("Labyrinth");
        primaryStage.setScene(new Scene(root, 700, 1000));
        primaryStage.setMinHeight(1000);
        primaryStage.setMinWidth(700);
        primaryStage.setResizable(true);
        primaryStage.setOnCloseRequest(windowEvent -> {
            labyrinthView.terminate();
        });
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
