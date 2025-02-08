package assighn.a2;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javafx.scene.layout.VBox;
import sqlitedb.DatabaseConnection;


public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private ListView<String> venueList;
    @FXML private TextField searchField;
    @FXML private Label venueTitle; // New: Separate label for title
    @FXML private Label venueDetails;
    @FXML private TextField venueNameField;
    @FXML private TextField venueCapacityField;
    @FXML private TextField venueCategoryField;
    @FXML private TextField venueBookingPriceField;
    @FXML private TextField venueSuitableForField;
    @FXML private Button addVenueButton;
    @FXML private Button removeVenueButton;
    @FXML private Button addUserButton;
    @FXML private Button removeUserButton;
    @FXML private VBox userManagementSection;

    private String userRole;

    /**
     * Set the role and initialize the dashboard accordingly.
     */
    public void setWelcomeMessage(String role) {
        this.userRole = role;
        welcomeLabel.setText("Welcome " + role + " Dashboard");

        if (role.equals("Staff")) {
            userManagementSection.setVisible(false);  // Hide the whole section for staff users
        }

        venueTitle.setText("Display Window");
        venueDetails.setText("Select an action to see details here.");

        loadVenues();
    }

    private void clearDisplayWindow(String message) {
        venueDetails.setText(message);
    }

    /**
     * Load venues from the database into the ListView.
     */
    private void loadVenues() {
        ObservableList<String> venues = FXCollections.observableArrayList();
        String query = "SELECT name FROM venues";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                venues.add(rs.getString("name"));
            }

            venueList.setItems(venues);
        } catch (SQLException e) {
            System.out.println("Error loading venues: " + e.getMessage());
        }
    }

    /**
     * Search for a venue by name.
     */
    @FXML
    private void searchVenue() {
        String searchQuery = searchField.getText().trim();
        if (searchQuery.isEmpty()) return;

        clearDisplay();
        String query = "SELECT * FROM venues WHERE name LIKE ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, "%" + searchQuery + "%");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                venueTitle.setText(rs.getString("name"));
                venueDetails.setText(
                        "Capacity: " + rs.getInt("capacity") + "\n" +
                                "Suitable for: " + rs.getString("suitable_for") + "\n" +
                                "Category: " + rs.getString("category") + "\n" +
                                "Booking Price: $" + rs.getInt("booking_price") + "/hr"
                );
            } else {
                venueDetails.setText("No venue found.");
            }

        } catch (SQLException e) {
            venueDetails.setText("Search Error: " + e.getMessage());
        }
    }


    /**
     * Display selected venue details in a formatted way.
     */
    @FXML
    private void displayVenueDetails() {
        String selectedVenue = venueList.getSelectionModel().getSelectedItem();
        if (selectedVenue == null) return;

        String query = "SELECT * FROM venues WHERE name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, selectedVenue);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                clearDisplay(); // Clear previous details
                venueTitle.setText(rs.getString("name"));
                venueDetails.setText(
                        "Capacity: " + rs.getInt("capacity") + "\n" +
                                "Suitable for: " + rs.getString("suitable_for") + "\n" +
                                "Category: " + rs.getString("category") + "\n" +
                                "Booking Price: $" + rs.getInt("booking_price") + "/hr"
                );
            } else {
                venueDetails.setText("No details found.");
            }

        } catch (SQLException e) {
            venueDetails.setText("Error fetching details: " + e.getMessage());
        }
    }

    /**
     * Add a new venue to the database.
     */
    @FXML
    private void addVenue() {
        clearDisplay(); // Clear previous info
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Venue");
        dialog.setHeaderText("Enter Venue Details");

        // Ask for venue name
        dialog.setContentText("Venue Name:");
        String name = dialog.showAndWait().orElse("");
        if (name.isEmpty()) return;

        // Ask for capacity
        dialog.setContentText("Capacity:");
        String capacityStr = dialog.showAndWait().orElse("");
        if (capacityStr.isEmpty()) return;

        // Ask for suitable_for
        dialog.setContentText("Suitable For:");
        String suitableFor = dialog.showAndWait().orElse("");
        if (suitableFor.isEmpty()) return;

        // Ask for category
        dialog.setContentText("Category:");
        String category = dialog.showAndWait().orElse("");
        if (category.isEmpty()) return;

        // Ask for booking price
        dialog.setContentText("Booking Price:");
        String bookingPriceStr = dialog.showAndWait().orElse("");
        if (bookingPriceStr.isEmpty()) return;

        try {
            int capacity = Integer.parseInt(capacityStr);
            int bookingPrice = Integer.parseInt(bookingPriceStr);

            String query = "INSERT INTO venues (name, capacity, suitable_for, category, booking_price) VALUES (?, ?, ?, ?, ?)";
            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(query)) {

                pstmt.setString(1, name);
                pstmt.setInt(2, capacity);
                pstmt.setString(3, suitableFor);
                pstmt.setString(4, category);
                pstmt.setInt(5, bookingPrice);
                pstmt.executeUpdate();

                loadVenues();
                venueTitle.setText(name);
                venueDetails.setText("✅ Venue added successfully.");
            }

        } catch (NumberFormatException e) {
            venueDetails.setText("❌ Capacity and Booking Price must be numbers.");
        } catch (SQLException e) {
            venueDetails.setText("❌ Error adding venue: " + e.getMessage());
        }
    }



    /**
     * Remove a selected venue.
     */
    @FXML
    private void removeVenue() {
        String selectedVenue = venueList.getSelectionModel().getSelectedItem();
        if (selectedVenue == null) {
            venueDetails.setText("Please select a venue to remove.");
            return;
        }

        String query = "DELETE FROM venues WHERE name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, selectedVenue);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                loadVenues();
                venueDetails.setText("Venue removed successfully.");
            } else {
                venueDetails.setText("Failed to remove venue.");
            }
        } catch (SQLException e) {
            venueDetails.setText("Error removing venue: " + e.getMessage());
        }
    }

    /**
     * Add a new user (Manager Only).
     */
    @FXML
    private void addUser() {
        if (!userRole.equals("Manager")) {
            venueDetails.setText("❌ Only Managers can add users.");
            return;
        }

        clearDisplay(); // Clear previous display before proceeding

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New User");
        dialog.setHeaderText("Enter New User Details");

        // Ask for Username
        dialog.setContentText("Enter Username:");
        String username = dialog.showAndWait().orElse("").trim();
        if (username.isEmpty()) return;

        // Ask for Password
        dialog.setContentText("Enter Password:");
        String password = dialog.showAndWait().orElse("").trim();
        if (password.isEmpty()) return;

        // Ask for First Name
        dialog.setContentText("Enter First Name:");
        String firstName = dialog.showAndWait().orElse("").trim();
        if (firstName.isEmpty()) return;

        // Ask for Last Name
        dialog.setContentText("Enter Last Name:");
        String lastName = dialog.showAndWait().orElse("").trim();
        if (lastName.isEmpty()) return;

        // Ask for Role (Dropdown)
        ChoiceDialog<String> roleDialog = new ChoiceDialog<>("Staff", "Manager", "Staff");
        roleDialog.setTitle("Select User Role");
        roleDialog.setHeaderText("Choose a Role for the User");
        roleDialog.setContentText("Role:");

        String role = roleDialog.showAndWait().orElse("").trim();
        if (role.isEmpty()) return;

        // If role is "Manager", ask for authorization PIN
        if (role.equals("Manager")) {
            TextInputDialog pinDialog = new TextInputDialog();
            pinDialog.setTitle("Authorization Required");
            pinDialog.setHeaderText("Enter the Secret PIN to Create a Manager Account");
            pinDialog.setContentText("Enter PIN:");

            String enteredPin = pinDialog.showAndWait().orElse("").trim();
            if (!enteredPin.equals("909")) {
                venueDetails.setText("❌ Incorrect Authorization PIN. Manager account creation denied.");
                return;
            }
        }

        // Insert user into the database
        String query = "INSERT INTO users (username, password, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, firstName);
            pstmt.setString(4, lastName);
            pstmt.setString(5, role);
            pstmt.executeUpdate();

            venueTitle.setText("✅ User Added Successfully");
            venueDetails.setText("User '" + username + "' (" + firstName + " " + lastName + ") added as " + role);

        } catch (SQLException e) {
            venueDetails.setText("❌ Error adding user: " + e.getMessage());
        }
    }


    /**
     * Remove a user (Manager Only).
     */
    @FXML
    private void removeUser() {
        if (!userRole.equals("Manager")) {
            venueDetails.setText("❌ Only Managers can remove users.");
            return;
        }

        clearDisplay(); // Clear previous display before proceeding

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Remove User");
        dialog.setHeaderText("Enter Username to Remove");
        dialog.setContentText("Username:");

        String username = dialog.showAndWait().orElse("").trim();
        if (username.isEmpty()) return;

        String query = "DELETE FROM users WHERE username = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, username);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                venueTitle.setText("✅ User Removed Successfully");
                venueDetails.setText("User '" + username + "' has been removed.");
            } else {
                venueDetails.setText("❌ User not found.");
            }

        } catch (SQLException e) {
            venueDetails.setText("❌ Error removing user: " + e.getMessage());
        }
    }

    /**
     * Clear Display Section
     */
    private void clearDisplay() {
        venueTitle.setText("Display Window");
        venueDetails.setText("Select an action to see details here.");
    }

}


