package assighn.a2;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;


import javafx.stage.Stage;
import sqlitedb.DatabaseConnection;

public class EventManagementController {

    @FXML private Label pageTitle;
    @FXML private ListView<String> eventsList;
    @FXML private TextArea eventDetails;
    @FXML private Button matchVenueButton;
    @FXML private TextArea matchResult;
    @FXML private Button bookVenueButton;
    @FXML private Button cancelBookingButton;
    @FXML private Button showOrdersButton;
    @FXML private Button calculateCommissionButton;
    @FXML private Button importCSVButton;
    @FXML private Button managementCornerButton;

    /**
     * Initialize the event management page with available events.
     */
    @FXML
    public void initializePage() {
        pageTitle.setText("Manage Events");
        loadAvailableEvents();

        // Simulate role check - Replace this with actual authentication logic
        boolean isManager = checkUserRole();

        // Show Management Corner only for managers
        managementCornerButton.setVisible(isManager);

        eventsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                displayEventDetails();
            }
        });
    }


    /**
     * Load available events from the database.
     */
    private void loadAvailableEvents() {
        ObservableList<String> events = FXCollections.observableArrayList();
        String query = "SELECT event_name FROM events WHERE available = 'Yes'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                events.add(rs.getString("event_name"));
            }
            eventsList.setItems(events);
        } catch (SQLException e) {
            eventDetails.setText("❌ Error loading events: " + e.getMessage());
        }
    }

    /**
     * Display event details when an event is selected.
     */
    @FXML
    private void displayEventDetails() {
        String selectedEvent = eventsList.getSelectionModel().getSelectedItem();
        if (selectedEvent == null || selectedEvent.isEmpty()) {
            eventDetails.setText("❌ No event selected.");
            return;
        }

        String query = "SELECT * FROM events WHERE event_name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setString(1, selectedEvent);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    eventDetails.setText(
                            "📅 Event: " + rs.getString("event_name") + "\n" +
                                    "🏛 Client: " + rs.getString("client") + "\n" +
                                    "🎤 Artist: " + rs.getString("artist") + "\n" +
                                    "📆 Date: " + rs.getString("event_date") + "\n" +
                                    "⏰ Time: " + rs.getString("event_time") + "\n" +
                                    "🎭 Type: " + rs.getString("type") + "\n" +
                                    "🏷 Category: " + rs.getString("category") + "\n" +
                                    "✅ Available: " + rs.getString("available")
                    );
                } else {
                    eventDetails.setText("❌ No event details found.");
                }
            }
        } catch (SQLException e) {
            eventDetails.setText("❌ Error fetching event details: " + e.getMessage());
        }
    }

    /**
     * Auto-match venues based on event requirements.
     */
    @FXML
    private void autoMatchVenue() {
        String selectedEvent = eventsList.getSelectionModel().getSelectedItem();
        if (selectedEvent == null) {
            matchResult.setText("❌ Select an event first!");
            return;
        }

        String eventQuery = """
        SELECT event_id, target_audience, event_date, event_time, type AS event_type, category AS event_category
        FROM events
        WHERE event_name = ?
    """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement eventStmt = con.prepareStatement(eventQuery)) {

            eventStmt.setString(1, selectedEvent);
            ResultSet eventRs = eventStmt.executeQuery();

            if (!eventRs.next()) {
                matchResult.setText("❌ No event details found.");
                return;
            }

            int eventId = eventRs.getInt("event_id");
            int targetAudience = eventRs.getInt("target_audience");
            String eventDate = eventRs.getString("event_date");
            String eventTime = eventRs.getString("event_time");
            String eventType = eventRs.getString("event_type").trim().toLowerCase();
            String eventCategory = eventRs.getString("event_category").trim().toLowerCase();

            // Fetch available venues
            String venueQuery = """
            SELECT v.id, v.name, v.capacity, v.category, v.suitable_for,
                   (SELECT COUNT(*) FROM bookings WHERE venue_id = v.id AND booking_date = ? AND booking_time = ?) AS booked
            FROM venues v
            WHERE v.availability = 'Available'
        """;

            try (PreparedStatement venueStmt = con.prepareStatement(venueQuery)) {
                venueStmt.setString(1, eventDate);
                venueStmt.setString(2, eventTime);
                ResultSet venueRs = venueStmt.executeQuery();

                // Store venues by their computed match scores
                TreeMap<Integer, List<String>> venueMatches = new TreeMap<>(Collections.reverseOrder());

                while (venueRs.next()) {
                    int venueId = venueRs.getInt("id");
                    String venueName = venueRs.getString("name");
                    int venueCapacity = venueRs.getInt("capacity");
                    String venueCategory = venueRs.getString("category").trim().toLowerCase();
                    String venueSuitableFor = venueRs.getString("suitable_for").trim().toLowerCase();
                    int isBooked = venueRs.getInt("booked");

                    int score = 0;

                    // **Criterion 1: Availability (25 pts)**
                    if (isBooked == 0) score += 25;

                    // **Criterion 2: Capacity (25 pts)**
                    if (venueCapacity >= targetAudience) score += 25;

                    // **Criterion 3: Event Type Match (25 pts)**
                    if (venueCategory.equals(eventCategory)) {
                        score += 25;
                    }

                    // **Criterion 4: Event Category Match (25 pts)**
                    String suitableForPrimary = venueSuitableFor.split(";")[0].trim(); // Extract the first part before ';'
                    if (suitableForPrimary.equalsIgnoreCase(eventType)) {
                        score += 25;
                    }

                    // Store venue based on score
                    venueMatches.computeIfAbsent(score, k -> new ArrayList<>())
                            .add(venueId + " - " + venueName + " (Capacity: " + venueCapacity + ")");
                }

                // Display results
                if (!venueMatches.isEmpty()) {
                    StringBuilder resultText = new StringBuilder("🏆 Venue Matches:\n");
                    for (Map.Entry<Integer, List<String>> entry : venueMatches.entrySet()) {
                        resultText.append("\n🔹 Score: ").append(entry.getKey()).append("%\n");
                        for (String venue : entry.getValue()) {
                            resultText.append(" - ").append(venue).append("\n");
                        }
                    }
                    matchResult.setText(resultText.toString());
                } else {
                    matchResult.setText("❌ Unable to find a match. Try adding more venues or loosening criteria.");
                }
            }
        } catch (SQLException e) {
            matchResult.setText("❌ Error fetching venue matches: " + e.getMessage());
        }
    }

    /**
     * Book a venue for the selected event.
     */
    @FXML
    private void bookVenue() {
        String selectedEvent = eventsList.getSelectionModel().getSelectedItem();
        if (selectedEvent == null) {
            eventDetails.setText("❌ Select an event to book.");
            return;
        }

        String query = """
    SELECT e.event_id, e.target_audience, e.event_date, e.event_time, e.type, e.category,
           v.id AS venue_id, v.name AS venue_name, v.capacity, v.category AS venue_type, v.suitable_for,
           (SELECT COUNT(*) FROM bookings b WHERE b.venue_id = v.id
           AND b.booking_date = e.event_date AND b.booking_time = e.event_time) AS booked
    FROM events e
    LEFT JOIN venues v ON v.availability = 'Available'
    WHERE e.event_name = ?
    ORDER BY booked ASC, v.capacity DESC
    """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query)) {

            con.createStatement().execute("PRAGMA busy_timeout = 5000;");
            stmt.setString(1, selectedEvent);
            ResultSet rs = stmt.executeQuery();

            ObservableList<String> availableVenues = FXCollections.observableArrayList();
            int eventId = -1, targetAudience = -1;
            String eventDate = "", eventTime = "", eventType = "", eventCategory = "";
            boolean dataFound = false;

            while (rs.next()) {
                dataFound = true;
                if (eventId == -1) {
                    eventId = rs.getInt("event_id");
                    targetAudience = rs.getInt("target_audience");
                    eventDate = rs.getString("event_date");
                    eventTime = rs.getString("event_time");
                    eventType = rs.getString("type"); // Event Type
                    eventCategory = rs.getString("category"); // Event Category
                }

                if (rs.getInt("booked") == 0) {
                    int venueId = rs.getInt("venue_id");
                    String venueName = rs.getString("venue_name");
                    int capacity = rs.getInt("capacity");
                    String venueType = rs.getString("venue_type"); // Venue Type
                    String venueSuitableFor = rs.getString("suitable_for"); // Venue Suitability List

                    // Extract the first part of suitable_for before semicolon
                    String venuePrimarySuitability = venueSuitableFor.split(";")[0].trim();

                    String warning = "";
                    if (capacity < targetAudience) warning += " ⚠ Low Capacity!";
                    if (venueType != null && eventCategory != null && !venueType.equalsIgnoreCase(eventCategory)) {
                        warning += " ❗ Category Mismatch!";
                    }
                    if (eventType != null && !venuePrimarySuitability.equalsIgnoreCase(eventType)) {
                        warning += " ❗ Type Mismatch!";
                    }

                    availableVenues.add(venueId + " - " + venueName + " (Capacity: " + capacity + ")" + warning);
                }
            }

            if (!dataFound) {
                eventDetails.setText("❌ No event details found.");
                return;
            }

            if (availableVenues.isEmpty()) {
                eventDetails.setText("❌ No available venues for this event.");
                return;
            }

            // Show venue selection dialog
            ChoiceDialog<String> dialog = new ChoiceDialog<>(availableVenues.getFirst(), availableVenues);
            dialog.setTitle("Select Venue");
            dialog.setHeaderText("Choose a venue for: " + selectedEvent);
            dialog.setContentText("Available Venues:");

            final int finalEventId = eventId;
            final int finalTargetAudience = targetAudience;
            final String finalEventDate = eventDate;
            final String finalEventTime = eventTime;
            final String finalEventType = eventType;
            final String finalEventCategory = eventCategory;

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(selection -> {
                int chosenVenueId = Integer.parseInt(selection.split(" - ")[0].trim());
                confirmBooking(finalEventId, chosenVenueId, finalEventDate, finalEventTime, finalTargetAudience, finalEventType, finalEventCategory);
            });

        } catch (SQLException e) {
            eventDetails.setText("❌ Error fetching booking details: " + e.getMessage());
        }
    }



    private void confirmBooking(int eventId, int venueId, String eventDate, String eventTime, int targetAudience, String eventType, String eventCategory) {
        String bookingQuery = "INSERT INTO bookings (event_id, venue_id, booking_date, booking_time, status) VALUES (?, ?, ?, ?, 'Confirmed')";
        String updateVenue = "UPDATE venues SET availability = 'Not Available' WHERE id = ?";
        String updateEvent = "UPDATE events SET available = 'No' WHERE event_id = ?";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.createStatement().execute("PRAGMA busy_timeout = 5000;");
            con.setAutoCommit(false);

            String venueQuery = "SELECT capacity, category, suitable_for FROM venues WHERE id = ?";
            try (PreparedStatement venueStmt = con.prepareStatement(venueQuery)) {
                venueStmt.setInt(1, venueId);
                ResultSet venueRs = venueStmt.executeQuery();

                if (!venueRs.next()) {
                    eventDetails.setText("❌ Venue not found.");
                    return;
                }

                int venueCapacity = venueRs.getInt("capacity");
                String venueType = venueRs.getString("category");
                String venueSuitableFor = venueRs.getString("suitable_for");

                // Extract the first part before the semicolon
                String venuePrimarySuitability = venueSuitableFor.split(";")[0].trim();

                // ⚠ Check for Type Mismatch
                if (eventType != null && !venuePrimarySuitability.equalsIgnoreCase(eventType)) {
                    Alert typeAlert = new Alert(Alert.AlertType.WARNING);
                    typeAlert.setTitle("Type Mismatch");
                    typeAlert.setHeaderText("❗ Venue Type Mismatch!");
                    typeAlert.setContentText("The venue is suitable for (" + venuePrimarySuitability + ") but the event type is (" + eventType + "). Proceed?");
                    ButtonType proceed = new ButtonType("Proceed", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    typeAlert.getButtonTypes().setAll(proceed, cancel);

                    Optional<ButtonType> choice = typeAlert.showAndWait();
                    if (choice.isPresent() && choice.get() == cancel) {
                        eventDetails.setText("❗ Booking cancelled due to type mismatch.");
                        return;
                    }
                }

                // ⚠ Check for Category Mismatch
                if (venueType != null && eventCategory != null && !venueType.equalsIgnoreCase(eventCategory)) {
                    Alert categoryAlert = new Alert(Alert.AlertType.WARNING);
                    categoryAlert.setTitle("Category Mismatch");
                    categoryAlert.setHeaderText("❗ Venue Category Mismatch!");
                    categoryAlert.setContentText("The venue category is (" + venueType + "), but the event category is (" + eventCategory + "). Proceed?");
                    ButtonType proceed = new ButtonType("Proceed", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    categoryAlert.getButtonTypes().setAll(proceed, cancel);

                    Optional<ButtonType> choice = categoryAlert.showAndWait();
                    if (choice.isPresent() && choice.get() == cancel) {
                        eventDetails.setText("❗ Booking cancelled due to category mismatch.");
                        return;
                    }
                }

                // ✅ Insert Booking and Update Event/Venue Availability
                try (PreparedStatement bookStmt = con.prepareStatement(bookingQuery);
                     PreparedStatement updateVenueStmt = con.prepareStatement(updateVenue);
                     PreparedStatement updateEventStmt = con.prepareStatement(updateEvent)) {

                    bookStmt.setInt(1, eventId);
                    bookStmt.setInt(2, venueId);
                    bookStmt.setString(3, eventDate);
                    bookStmt.setString(4, eventTime);
                    bookStmt.executeUpdate();

                    updateVenueStmt.setInt(1, venueId);
                    updateVenueStmt.executeUpdate();

                    updateEventStmt.setInt(1, eventId);
                    updateEventStmt.executeUpdate();

                    con.commit();
                    eventDetails.setText("✅ Booking Confirmed: Venue ID " + venueId);
                    loadAvailableEvents();

                } catch (SQLException ex) {
                    con.rollback();
                    throw ex;
                }
            }
        } catch (SQLException e) {
            eventDetails.setText("❌ Error booking venue: " + e.getMessage());
        }
    }

    /**
     * Cancel a booking.
     */
    @FXML
    private void cancelBooking() {
        try (Connection con = DatabaseConnection.getConnection()) {
            ObservableList<String> bookedEvents = FXCollections.observableArrayList();
            String query = """
            SELECT e.event_name, b.venue_id 
            FROM bookings b 
            JOIN events e ON b.event_id = e.event_id
        """;

            PreparedStatement pstmt = con.prepareStatement(query);
            ResultSet rs = pstmt.executeQuery();

            Map<String, Integer> eventVenueMap = new HashMap<>();
            while (rs.next()) {
                String eventName = rs.getString("event_name");
                int venueId = rs.getInt("venue_id");
                bookedEvents.add(eventName);
                eventVenueMap.put(eventName, venueId);
            }

            if (bookedEvents.isEmpty()) {
                eventDetails.setText("❌ No bookings found to cancel.");
                return;
            }

            // Display cancel booking dialog
            ChoiceDialog<String> dialog = new ChoiceDialog<>(bookedEvents.getFirst(), bookedEvents);
            dialog.setTitle("Cancel Booking");
            dialog.setHeaderText("Select a booking to cancel:");
            dialog.setContentText("Booking:");

            dialog.showAndWait().ifPresent(selectedEvent -> {
                try {
                    // Get event ID
                    String eventQuery = "SELECT event_id FROM events WHERE event_name = ?";
                    PreparedStatement eventStmt = con.prepareStatement(eventQuery);
                    eventStmt.setString(1, selectedEvent);
                    ResultSet eventRs = eventStmt.executeQuery();

                    if (!eventRs.next()) {
                        eventDetails.setText("❌ Event not found.");
                        return;
                    }

                    int eventId = eventRs.getInt("event_id");
                    int venueId = eventVenueMap.get(selectedEvent); // Get the associated venue ID

                    // Cancel booking
                    String cancelQuery = "DELETE FROM bookings WHERE event_id = ?";
                    PreparedStatement cancelStmt = con.prepareStatement(cancelQuery);
                    cancelStmt.setInt(1, eventId);
                    cancelStmt.executeUpdate();

                    // Update event availability
                    String updateEventQuery = "UPDATE events SET available = 'Yes' WHERE event_id = ?";
                    PreparedStatement updateEventStmt = con.prepareStatement(updateEventQuery);
                    updateEventStmt.setInt(1, eventId);
                    updateEventStmt.executeUpdate();

                    // Update venue availability
                    String updateVenueQuery = "UPDATE venues SET availability = 'Available' WHERE id = ?";
                    PreparedStatement updateVenueStmt = con.prepareStatement(updateVenueQuery);
                    updateVenueStmt.setInt(1, venueId);
                    updateVenueStmt.executeUpdate();

                    eventDetails.setText("⛔ Booking canceled for " + selectedEvent);
                    loadAvailableEvents(); // Refresh the event list

                } catch (SQLException e) {
                    eventDetails.setText("❌ Error canceling booking: " + e.getMessage());
                }
            });

        } catch (SQLException e) {
            eventDetails.setText("❌ Error fetching bookings: " + e.getMessage());
        }
    }


    /**
     * Show all bookings/orders.
     */
    @FXML
    private void showOrders() {
        String query = """
        SELECT e.event_name, v.name AS venue_name, b.booking_date, b.booking_time
        FROM bookings b
        JOIN events e ON b.event_id = e.event_id
        JOIN venues v ON b.venue_id = v.id
        ORDER BY b.booking_date, b.booking_time
    """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            StringBuilder bookingsList = new StringBuilder();
            int count = 0;

            while (rs.next()) {
                count++;
                String eventName = rs.getString("event_name");
                String venueName = rs.getString("venue_name");
                String date = rs.getString("booking_date");
                String time = rs.getString("booking_time");

                bookingsList.append(count)
                        .append(". 🎟 Event: ").append(eventName)
                        .append("\n🏛 Venue: ").append(venueName)
                        .append("\n📅 Date: ").append(date)
                        .append(" ⏰ Time: ").append(time)
                        .append("\n\n");
            }

            if (count == 0) {
                bookingsList.append("❌ No bookings have been made yet.");
            }

            // Show as a pop-up alert
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Bookings List");
            alert.setHeaderText("📜 All Bookings");
            alert.setContentText(bookingsList.toString());
            alert.showAndWait();

        } catch (SQLException e) {
            eventDetails.setText("❌ Error fetching orders: " + e.getMessage());
        }
    }

    /**
     * Calculate commission for the event organizers.
     */
    @FXML
    private void calculateCommission() {
        String query = """
    SELECT e.client, SUM(v.booking_price) AS total_venue_cost, COUNT(e.event_id) AS total_events
    FROM bookings b
    JOIN events e ON b.event_id = e.event_id
    JOIN venues v ON b.venue_id = v.id
    GROUP BY e.client
    """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            StringBuilder commissionDetails = new StringBuilder("💵 Commission Details:\n\n");
            double totalCommission = 0;

            while (rs.next()) {
                String clientName = rs.getString("client");
                double totalVenueCost = rs.getDouble("total_venue_cost");
                int totalEvents = rs.getInt("total_events");

                // Determine commission rate
                double commissionRate = (totalEvents > 1) ? 0.09 : 0.10; // 9% if multiple bookings, else 10%
                double commissionAmount = totalVenueCost * commissionRate;
                totalCommission += commissionAmount;

                commissionDetails.append("🧑 Client: ").append(clientName)
                        .append("\n🏛 Total Venue Cost: $").append(String.format("%.2f", totalVenueCost))
                        .append("\n📌 Events Booked: ").append(totalEvents)
                        .append("\n💰 Commission Rate: ").append((commissionRate * 100)).append("%")
                        .append("\n💵 Commission Earned: $").append(String.format("%.2f", commissionAmount))
                        .append("\n\n");
            }

            // If no commission data found
            if (commissionDetails.toString().equals("💵 Commission Details:\n\n")) {
                commissionDetails.append("❌ No bookings found, no commission earned.");
            } else {
                commissionDetails.append("🔹 **Total Commission Earned:** $").append(String.format("%.2f", totalCommission));
            }

            // Show in an alert
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Commission Report");
            alert.setHeaderText("📜 Manager Commission Breakdown");
            alert.setContentText(commissionDetails.toString());
            alert.showAndWait();

        } catch (SQLException e) {
            eventDetails.setText("❌ Error calculating commission: " + e.getMessage());
        }
    }


    /**
     * Import CSV file into the database.
     */

    @FXML
    private void importCSV() {
        final String CSV_FILE_PATH = "src/main/resources/requests.csv"; // Ensure the path is correct
        String insertQuery = """
        INSERT INTO events (client, event_name, artist, event_date, event_time, duration, target_audience, type, category, available) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

        try (Connection con = DatabaseConnection.getConnection();
             BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH));
             PreparedStatement pstmt = con.prepareStatement(insertQuery)) {

            String line;
            boolean firstLine = true; // Skip header

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Assuming CSV is comma-separated
                String[] values = line.split(",(?![^\\[]*])");

                if (values.length < 9) { // Ensure correct number of columns
                    System.out.println("❌ Skipping invalid row: " + line);
                    continue;
                }

                String client = values[0].trim();  // Client
                String eventName = values[1].trim();  // Title (Event Name)
                String artist = values[2].trim();  // Artist
                String eventDate = values[3].trim();  // Date
                String eventTime = values[4].trim();  // Time
                String durationString = values[5].trim();  // Duration
                String targetAudienceString = values[6].trim();  // Target Audience
                String type = values[7].trim();  // Type of event (e.g., gig, festival)
                String category = values[8].trim();  // Category (e.g., indoor, outdoor, convertible)
                String available = "Yes";  // Set available to 'Yes' by default

                try {
                    int duration = Integer.parseInt(durationString); // Ensure duration is numeric
                    int targetAudience = Integer.parseInt(targetAudienceString); // Ensure target audience is numeric

                    pstmt.setString(1, client);
                    pstmt.setString(2, eventName);
                    pstmt.setString(3, artist);
                    pstmt.setString(4, eventDate);
                    pstmt.setString(5, eventTime);
                    pstmt.setInt(6, duration);
                    pstmt.setInt(7, targetAudience);
                    pstmt.setString(8, type);
                    pstmt.setString(9, category);
                    pstmt.setString(10, available);
                    pstmt.addBatch();

                } catch (NumberFormatException e) {
                    System.out.println("❌ Skipping invalid numeric values: " + durationString + ", " + targetAudienceString);
                }
            }

            pstmt.executeBatch();
            System.out.println("✅ CSV data imported successfully!");
            loadAvailableEvents(); // Refresh event list after import

        } catch (IOException | SQLException e) {
            System.out.println("❌ Error importing CSV: " + e.getMessage());
        }
    }

    /**
     * Check user role
     */
    private boolean checkUserRole() {
        String currentUserRole = getCurrentUserRole();
        return "Manager".equalsIgnoreCase(currentUserRole);
    }

    /**
     * Get the current user's role.
     * This simulates retrieving user role from session/login.
     */
    private String getCurrentUserRole() {
        // Simulating a logged-in user. Replace this with actual authentication.
        return "Manager";  // Example: Change this to "Staff" to test restricted access.
    }

    /**
     * Open Management Corner when clicked.
     */
    @FXML
    private void openManagementCorner() {
        if (!checkUserRole()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Access Denied");
            alert.setHeaderText("🚫 Restricted Access");
            alert.setContentText("Only managers can access the Management Corner.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("management-dashboard..fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Management Corner");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorMessage("Error opening Management Corner: " + e.getMessage());
        }
    }

    /**
     * Display an error message.
     */
    private void showErrorMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("❌ An error occurred");
        alert.setContentText(message);
        alert.showAndWait();
    }



    @FXML
    private void exitApplication() {
        Stage stage = (Stage) pageTitle.getScene().getWindow();
        stage.close();
    }




}
