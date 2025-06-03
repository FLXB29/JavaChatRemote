package app;

import app.util.DatabaseKeyManager;
import app.util.EmailUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {


    @Override
    public void start(Stage stage) throws Exception {
        ServiceLocator.init(false);      // khởi tạo Service cho client
        // In main method or initialization block
        DatabaseKeyManager.initialize();
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(fxml.load());
        scene.getStylesheets().addAll(
                getClass().getResource("/css/style.css").toExternalForm(),
                getClass().getResource("/css/auth.css").toExternalForm());


        stage.setTitle("MyMessenger");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        ServiceLocator.shutdown();
    }

    public static void main(String[] args) {
//        System.out.println(
//                EmailUtil.sendOTPEmail("phuclethanh29062@gmail.com", "123456"));
        launch(args);
    }
}
