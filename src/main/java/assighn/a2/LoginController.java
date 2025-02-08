package assighn.a2;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label welcomeText;

    @FXML
    protected void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = roleComboBox.getValue();

        if (username.isEmpty() || password.isEmpty() || role == null) {
            welcomeText.setText("Please enter all fields.");
            return;
        }

        if (username.equals("admin") && password.equals("admin123")) {
            welcomeText.setText("Login successful as " + role);
            // TODO: Redirect to Dashboard
        } else {
            welcomeText.setText("Invalid login credentials!");
        }
    }
}
