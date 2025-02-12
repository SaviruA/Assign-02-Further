module assighn.a2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens assighn.a2 to javafx.fxml;
    exports assighn.a2;
}