package assighn.a2;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import sqlitedb.DatabaseConnection;


public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private ListView<String> venueList;
    @FXML private TextField searchField;
    @FXML private Label venueTitle; // New: Separate label for title
    @FXML private Label venueDetails;
    @FXML private Button addVenueButton;
    @FXML private Button removeVenueButton;
    @FXML private Button addUserButton;
    @FXML private Button removeUserButton;
    @FXML private VBox userManagementSection;
    @FXML private Button importCSVButton;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> eventTypeFilter;
    @FXML private TextField minCapacityFilter;
    @FXML private TextField maxCapacityFilter;
    @FXML private Button filterButton;
    @FXML private ComboBox<String> availabilityFilter; // New filter for venue availability
    @FXML private VBox staffAccountSection;
    @FXML private Button updateUserButton;
    @FXML private Button staffUserButton;



    private String userRole;
    private static final String CSV_FILE_PATH = "src/main/resources/venues.csv";

    /**
     * Set the role and initialize the dashboard accordingly.
     */
    public void setWelcomeMessage(String role) {
        System.out.println("setWelcomeMessage() called for role: " + role);

        this.userRole = role;
        welcomeLabel.setText("Welcome " + role + " Dashboard");

        if (role.equals("Staff")) {
            userManagementSection.setVisible(false); // Hide Admin Controls

        } else {
            if (staffAccountSection != null) {
                staffAccountSection.setVisible(false); // Hide for Managers
            }
        }

        venueTitle.setText("Display Window");
        venueDetails.setText("Select an action to see details here.");

        // Load venues based on role (default availability filter)
        loadVenues(null, null, "Available", null, null);
    }





    private void clearDisplayWindow(String message) {
        venueDetails.setText(message);
    }

    /**
     * Load venues from the database into the ListView.
     */
    private void loadVenues(String category, String eventType, String availability, Integer minCapacity, Integer maxCapacity) {
        System.out.println("loadVenues() called!");

        ObservableList<String> venues = FXCollections.observableArrayList();
        venueList.getItems().clear();  // Prevent duplication

        StringBuilder query = new StringBuilder("SELECT name FROM venues WHERE 1=1");

        if (availability != null) query.append(" AND availability = ?");
        if (category != null) query.append(" AND category = ?");
        if (eventType != null) query.append(" AND suitable_for = ?");
        if (minCapacity != null) query.append(" AND capacity >= ?");
        if (maxCapacity != null) query.append(" AND capacity <= ?");

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query.toString())) {

            int index = 1;
            if (availability != null) pstmt.setString(index++, availability);
            if (category != null) pstmt.setString(index++, category);
            if (eventType != null) pstmt.setString(index++, eventType);
            if (minCapacity != null) pstmt.setInt(index++, minCapacity);
            if (maxCapacity != null) pstmt.setInt(index++, maxCapacity);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                venues.add(rs.getString("name"));
            }

            venueList.setItems(venues);
        } catch (SQLException e) {
            System.out.println("❌ Error loading venues: " + e.getMessage());
        }
    }



    @FXML
    private void applyFilter() {
        String category = categoryFilter.getValue();
        String eventType = eventTypeFilter.getValue();
        String availability = availabilityFilter.getValue(); // Get selected availability
        Integer minCapacity = null, maxCapacity = null;

        try {
            minCapacity = minCapacityFilter.getText().isEmpty() ? null : Integer.parseInt(minCapacityFilter.getText());
            maxCapacity = maxCapacityFilter.getText().isEmpty() ? null : Integer.parseInt(maxCapacityFilter.getText());
        } catch (NumberFormatException e) {
            venueDetails.setText("❌ Capacity values must be numbers.");
            return;
        }

        // Apply filters, including availability
        loadVenues(category, eventType, availability, minCapacity, maxCapacity);
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
        String name = dialog.showAndWait().orElse("").trim();
        if (name.isEmpty()) return;

        // Ask for capacity
        dialog.setContentText("Capacity:");
        String capacityStr = dialog.showAndWait().orElse("").trim();
        if (capacityStr.isEmpty()) return;

        // Ask for suitable_for (Event Type)
        dialog.setContentText("Suitable For (Event Type):");
        String suitableFor = dialog.showAndWait().orElse("").trim();
        if (suitableFor.isEmpty()) return;

        // Ask for category
        dialog.setContentText("Category:");
        String category = dialog.showAndWait().orElse("").trim();
        if (category.isEmpty()) return;

        // Ask for booking price
        dialog.setContentText("Booking Price:");
        String bookingPriceStr = dialog.showAndWait().orElse("").trim();
        if (bookingPriceStr.isEmpty()) return;

        // Ask for availability (Dropdown: Available / Booked)
        ChoiceDialog<String> availabilityDialog = new ChoiceDialog<>("Available", "Available", "Booked");
        availabilityDialog.setTitle("Set Venue Availability");
        availabilityDialog.setHeaderText("Choose Availability for the Venue");
        availabilityDialog.setContentText("Availability:");

        String availability = availabilityDialog.showAndWait().orElse("").trim();
        if (availability.isEmpty()) return;

        try {
            int capacity = Integer.parseInt(capacityStr);
            int bookingPrice = Integer.parseInt(bookingPriceStr);

            // Insert new venue with selected availability
            String query = "INSERT INTO venues (name, capacity, suitable_for, category, booking_price, availability) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(query)) {

                pstmt.setString(1, name);
                pstmt.setInt(2, capacity);
                pstmt.setString(3, suitableFor);
                pstmt.setString(4, category);
                pstmt.setInt(5, bookingPrice);
                pstmt.setString(6, availability);  // Store selected availability
                pstmt.executeUpdate();

                // Reload venues with current filters
                loadVenues(categoryFilter.getValue(), eventTypeFilter.getValue(), availabilityFilter.getValue(), getMinCapacity(), getMaxCapacity());

                venueTitle.setText(name);
                venueDetails.setText("✅ Venue added successfully with status: " + availability);
            }

        } catch (NumberFormatException e) {
            venueDetails.setText("❌ Capacity and Booking Price must be numbers.");
        } catch (SQLException e) {
            venueDetails.setText("❌ Error adding venue: " + e.getMessage());
        }
    }


    private Integer getMinCapacity() {
        try {
            return minCapacityFilter.getText().isEmpty() ? null : Integer.parseInt(minCapacityFilter.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getMaxCapacity() {
        try {
            return maxCapacityFilter.getText().isEmpty() ? null : Integer.parseInt(maxCapacityFilter.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Remove a selected venue.
     */
    @FXML
    private void removeVenue() {
        String selectedVenue = venueList.getSelectionModel().getSelectedItem();
        if (selectedVenue == null) {
            venueDetails.setText("❌ Please select a venue to remove.");
            return;
        }

        // Confirmation dialog before deletion
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Venue Removal");
        confirmDialog.setHeaderText("Are you sure you want to remove this venue?");
        confirmDialog.setContentText("Venue: " + selectedVenue);

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.CANCEL) {
            venueDetails.setText("⛔ Venue removal cancelled.");
            return;
        }

        String query = "DELETE FROM venues WHERE name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, selectedVenue);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                venueTitle.setText("✅ Venue Removed");
                venueDetails.setText("Venue '" + selectedVenue + "' has been removed successfully.");

                // Reload venues using current filters
                applyFilter();
            } else {
                venueDetails.setText("⚠ Venue not found or already removed.");
            }
        } catch (SQLException e) {
            venueDetails.setText("❌ Error removing venue: " + e.getMessage());
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

    private static String loggedInUsername; // Store logged-in username

    public static String getLoggedInUsername() {
        return loggedInUsername;
    }


    public void loginUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                loggedInUsername = username;  // Store logged-in user
                userRole = rs.getString("role");  // Store their role
                loadVenues(null, null, "Available", null, null);  // Load dashboard

            } else {
                venueDetails.setText("❌ Invalid username or password.");
            }
        } catch (SQLException e) {
            venueDetails.setText("❌ Error logging in: " + e.getMessage());
        }
    }

    @FXML
    private void updateUser() {
        clearDisplay(); // Reset messages before proceeding

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Update User");
        dialog.setHeaderText("Enter Username to Update");
        dialog.setContentText("Username:");

        String username = dialog.showAndWait().orElse("").trim();
        if (username.isEmpty()) return;

        String query = "SELECT * FROM users WHERE username = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                venueDetails.setText("❌ User not found.");
                return;
            }

            // Retrieve current user details
            String currentPassword = rs.getString("password");
            String currentFirstName = rs.getString("first_name");
            String currentLastName = rs.getString("last_name");
            String currentRole = rs.getString("role");

            // Prevent staff from updating other accounts
            if (userRole.equals("Staff") && !username.equals(getLoggedInUsername())) {
                venueDetails.setText("❌ Staff can only update their own profile.");
                return;
            }

            // Pop-up dialog for updating user details
            Dialog<ButtonType> updateDialog = new Dialog<>();
            updateDialog.setTitle("Update User Details");
            updateDialog.setHeaderText("Modify details for user: " + username);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField passwordField = new TextField(currentPassword);
            TextField firstNameField = new TextField(currentFirstName);
            TextField lastNameField = new TextField(currentLastName);
            ComboBox<String> roleBox = new ComboBox<>();

            // Role selection only for Managers
            if (userRole.equals("Manager")) {
                roleBox.getItems().addAll("Staff", "Manager");
                roleBox.setValue(currentRole);
                grid.add(new Label("Role:"), 0, 3);
                grid.add(roleBox, 1, 3);
            }

            grid.add(new Label("Password:"), 0, 0);
            grid.add(passwordField, 1, 0);
            grid.add(new Label("First Name:"), 0, 1);
            grid.add(firstNameField, 1, 1);
            grid.add(new Label("Last Name:"), 0, 2);
            grid.add(lastNameField, 1, 2);

            updateDialog.getDialogPane().setContent(grid);
            updateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = updateDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String newPassword = passwordField.getText().trim();
                String newFirstName = firstNameField.getText().trim();
                String newLastName = lastNameField.getText().trim();
                String newRole = userRole.equals("Manager") ? roleBox.getValue() : currentRole; // Staff cannot change role

                // Validate input
                if (newPassword.isEmpty() || newFirstName.isEmpty() || newLastName.isEmpty()) {
                    venueDetails.setText("❌ All fields must be filled.");
                    return;
                }

                // Update user in database
                String updateQuery = "UPDATE users SET password = ?, first_name = ?, last_name = ?, role = ? WHERE username = ?";
                try (PreparedStatement updatePstmt = con.prepareStatement(updateQuery)) {
                    updatePstmt.setString(1, newPassword);
                    updatePstmt.setString(2, newFirstName);
                    updatePstmt.setString(3, newLastName);
                    updatePstmt.setString(4, newRole);
                    updatePstmt.setString(5, username);

                    int rowsAffected = updatePstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        venueTitle.setText("✅ User Updated");
                        venueDetails.setText("User '" + username + "' updated successfully.");
                    } else {
                        venueDetails.setText("❌ Failed to update user.");
                    }
                }
            }

        } catch (SQLException e) {
            venueDetails.setText("❌ Error updating user: " + e.getMessage());
        }
    }



    /**
     * Imports venue data from a fixed CSV file.
     */
    @FXML
    private void importVenuesFromCSV() {
        final String CSV_FILE_PATH = "src/main/resources/venues.csv"; // Ensure correct file path
        String insertQuery = "INSERT INTO venues (name, capacity, suitable_for, category, booking_price, availability) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection();
             BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH));
             PreparedStatement pstmt = con.prepareStatement(insertQuery)) {

            String line;
            boolean firstLine = true; // Skip CSV header

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                String[] values = line.split(",(?![^\\[]*])");
                if (values.length < 5) {
                    continue;
                }

                String venueName = values[0].trim();
                String capacityString = values[1].trim();
                String suitableFor = values[2].trim();
                String category = values[3].trim();
                String bookingPriceString = values[4].trim();

                try {
                    int capacity = Integer.parseInt(capacityString);
                    int bookingPrice = Integer.parseInt(bookingPriceString);

                    // ** Set default availability to "Available" **
                    String availability = "Available";

                    pstmt.setString(1, venueName);
                    pstmt.setInt(2, capacity);
                    pstmt.setString(3, suitableFor);
                    pstmt.setString(4, category);
                    pstmt.setInt(5, bookingPrice);
                    pstmt.setString(6, availability); // Store default availability
                    pstmt.addBatch();
                } catch (NumberFormatException e) {
                    System.out.println("Skipping invalid line: " + line);
                }
            }

            pstmt.executeBatch();
            venueTitle.setText("✅ Venues Imported");
            venueDetails.setText("Venues imported successfully from CSV!");

            // ** Refresh venue list with all current filters (including availability) **
            loadVenues(categoryFilter.getValue(), eventTypeFilter.getValue(), availabilityFilter.getValue(), getMinCapacity(), getMaxCapacity());

        } catch (IOException | SQLException e) {
            venueDetails.setText("❌ Error importing venues: " + e.getMessage());
        }
    }



    private void populateFilterOptions(ComboBox<String> categoryBox, ComboBox<String> eventTypeBox) {
        ObservableList<String> categories = FXCollections.observableArrayList();
        ObservableList<String> eventTypes = FXCollections.observableArrayList();

        String query = "SELECT DISTINCT category, suitable_for FROM venues";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String category = rs.getString("category");
                String eventType = rs.getString("suitable_for");

                if (!categories.contains(category)) categories.add(category);
                if (!eventTypes.contains(eventType)) eventTypes.add(eventType);
            }

            categoryBox.setItems(categories);
            eventTypeBox.setItems(eventTypes);

        } catch (SQLException e) {
            System.out.println("❌ Error loading filters: " + e.getMessage());
        }
    }

    private void applyFilter(String category, String eventType, String availability, String minCapacityStr, String maxCapacityStr) {
        Integer minCapacity = null, maxCapacity = null;

        try {
            if (!minCapacityStr.isEmpty()) minCapacity = Integer.parseInt(minCapacityStr);
            if (!maxCapacityStr.isEmpty()) maxCapacity = Integer.parseInt(maxCapacityStr);
        } catch (NumberFormatException e) {
            venueDetails.setText("❌ Capacity values must be numbers.");
            return;
        }

        // Apply filters
        loadVenues(category, eventType, availability, minCapacity, maxCapacity);

        // Display the applied filters in the Display Window
        venueTitle.setText("Filtered Venues");
        venueDetails.setText("Showing results for:\n" +
                (category != null ? "Category: " + category + "\n" : "") +
                (eventType != null ? "Event Type: " + eventType + "\n" : "") +
                (availability != null ? "Availability: " + availability + "\n" : "") +
                (minCapacity != null ? "Min Capacity: " + minCapacity + "\n" : "") +
                (maxCapacity != null ? "Max Capacity: " + maxCapacity : ""));
    }




    @FXML
    private void openFilterPopup() {
        Dialog<ButtonType> filterDialog = new Dialog<>();
        filterDialog.setTitle("Filter Venues");
        filterDialog.setHeaderText("Apply filters to find specific venues");

        // Create UI elements for filter options
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> categoryBox = new ComboBox<>();
        ComboBox<String> eventTypeBox = new ComboBox<>();
        ComboBox<String> availabilityBox = new ComboBox<>();
        TextField minCapacityField = new TextField();
        TextField maxCapacityField = new TextField();

        categoryBox.setPromptText("Select Category");
        eventTypeBox.setPromptText("Select Event Type");
        availabilityBox.setPromptText("Select Availability");

        // Define availability options
        availabilityBox.getItems().addAll("Available", "Booked");

        minCapacityField.setPromptText("Min Capacity");
        maxCapacityField.setPromptText("Max Capacity");

        // Load unique categories and event types from DB
        populateFilterOptions(categoryBox, eventTypeBox);

        grid.add(new Label("Category:"), 0, 0);
        grid.add(categoryBox, 1, 0);
        grid.add(new Label("Event Type:"), 0, 1);
        grid.add(eventTypeBox, 1, 1);
        grid.add(new Label("Availability:"), 0, 2);
        grid.add(availabilityBox, 1, 2);
        grid.add(new Label("Min Capacity:"), 0, 3);
        grid.add(minCapacityField, 1, 3);
        grid.add(new Label("Max Capacity:"), 0, 4);
        grid.add(maxCapacityField, 1, 4);

        filterDialog.getDialogPane().setContent(grid);
        filterDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = filterDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            applyFilter(categoryBox.getValue(), eventTypeBox.getValue(), availabilityBox.getValue(),
                    minCapacityField.getText(), maxCapacityField.getText());
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


