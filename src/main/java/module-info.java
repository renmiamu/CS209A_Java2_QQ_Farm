module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.demo to javafx.fxml;
    exports org.example.demo;
    exports org.example.demo.server;
    opens org.example.demo.server to javafx.fxml;
    exports org.example.demo.client;
}