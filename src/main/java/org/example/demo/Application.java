package org.example.demo;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.demo.client.ClientNetworkService;

/**
 * Entry point for the simplified QQ Farm demo.
 */
public class Application extends javafx.application.Application {
    private ClientNetworkService networkService;

    @Override
    public void start(Stage stage) throws Exception {

        networkService = new ClientNetworkService();
        networkService.connect("localhost", 8888);

        FXMLLoader loader = new FXMLLoader(Application.class.getResource("board.fxml"));
        Parent root = loader.load();

        Controller controller = loader.getController();
        controller.init(networkService);

        Scene scene = new Scene(root);
        stage.setTitle("QQ Farm Demo - Player");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            controller.shutdown();
            networkService.disconnect();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}