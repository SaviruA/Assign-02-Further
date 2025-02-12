package assighn.a2;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

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

    /**
     * Initialize the event management page with available events.
     */
    @FXML
    public void initializePage() {
        pageTitle.setText("Manage Events");
        loadAvailableEvents();
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

        // Dummy logic for auto-matching (Replace with SQL Query)
        matchResult.setText("✅ Suggested Venue: Grand Arena (Score: 95%)");
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
        SELECT e.event_id, e.target_audience, e.event_date, e.event_time,
               v.id AS venue_id, v.name AS venue_name, v.capacity,
               (SELECT COUNT(*) FROM bookings b WHERE b.venue_id = v.id
               AND b.booking_date = e.event_date AND b.booking_time = e.event_time) AS booked
        FROM events e
        LEFT JOIN venues v ON v.availability = 'Available'
        WHERE e.event_name = ?
        ORDER BY booked ASC, v.capacity DESC
    """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query)) {

            con.createStatement().execute("PRAGMA busy_timeout = 5000;");  // Add busy timeout
            stmt.setString(1, selectedEvent);
            ResultSet rs = stmt.executeQuery();

            ObservableList<String> availableVenues = FXCollections.observableArrayList();
            int eventId = -1, targetAudience = -1;
            String eventDate = "", eventTime = "";
            boolean dataFound = false;

            while (rs.next()) {
                dataFound = true;
                if (eventId == -1) {
                    eventId = rs.getInt("event_id");
                    targetAudience = rs.getInt("target_audience");
                    eventDate = rs.getString("event_date");
                    eventTime = rs.getString("event_time");
                }
                if (rs.getInt("booked") == 0) { // Venue is not booked for that date/time
                    int venueId = rs.getInt("venue_id");
                    String venueName = rs.getString("venue_name");
                    int capacity = rs.getInt("capacity");

                    String warning = (capacity < targetAudience) ? " ⚠ Low Capacity!" : "";
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

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(selection -> {
                int chosenVenueId = Integer.parseInt(selection.split(" - ")[0].trim());  // Extract venue ID
                confirmBooking(finalEventId, chosenVenueId, finalEventDate, finalEventTime, finalTargetAudience);
            });

        } catch (SQLException e) {
            eventDetails.setText("❌ Error fetching booking details: " + e.getMessage());
        }
    }


    private void confirmBooking(int eventId, int venueId, String eventDate, String eventTime, int targetAudience) {
        String bookingQuery = "INSERT INTO bookings (event_id, venue_id, booking_date, booking_time, status) VALUES (?, ?, ?, ?, 'Confirmed')";
        String updateVenue = "UPDATE venues SET availability = 'Not Available' WHERE id = ?";
        String updateEvent = "UPDATE events SET available = 'No' WHERE event_id = ?";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.createStatement().execute("PRAGMA busy_timeout = 5000;");  // Prevent database lock
            con.setAutoCommit(false);  // ✅ Start transaction

            // ❌ Prevent double booking
            String checkExistingBooking = """
            SELECT COUNT(*) AS count FROM bookings 
            WHERE venue_id = ? AND booking_date = ? AND booking_time = ?
        """;

            try (PreparedStatement checkStmt = con.prepareStatement(checkExistingBooking)) {
                checkStmt.setInt(1, venueId);
                checkStmt.setString(2, eventDate);
                checkStmt.setString(3, eventTime);
                ResultSet checkRs = checkStmt.executeQuery();

                if (checkRs.next() && checkRs.getInt("count") > 0) {
                    eventDetails.setText("❌ Double booking error! Venue is already booked for this date & time.");
                    return;
                }
            }

            // ⚠ Capacity Warning
            String capacityQuery = "SELECT capacity FROM venues WHERE id = ?";
            try (PreparedStatement capacityStmt = con.prepareStatement(capacityQuery)) {
                capacityStmt.setInt(1, venueId);
                ResultSet capacityRs = capacityStmt.executeQuery();
                if (capacityRs.next() && capacityRs.getInt("capacity") < targetAudience) {
                    Alert warningAlert = new Alert(Alert.AlertType.WARNING);
                    warningAlert.setTitle("Capacity Warning");
                    warningAlert.setHeaderText("⚠ Low Capacity!");
                    warningAlert.setContentText("The selected venue has low capacity. Proceed anyway?");
                    ButtonType proceed = new ButtonType("Proceed", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    warningAlert.getButtonTypes().setAll(proceed, cancel);

                    Optional<ButtonType> choice = warningAlert.showAndWait();
                    if (choice.isPresent() && choice.get() == cancel) {
                        eventDetails.setText("⚠ Booking cancelled due to low capacity.");
                        return;
                    }
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

                con.commit();  // ✅ Commit transaction
                eventDetails.setText("✅ Booking Confirmed: Venue ID " + venueId);

                // 🔄 Refresh event list so booked events are removed
                loadAvailableEvents();

            } catch (SQLException ex) {
                con.rollback();  // ❌ Rollback on failure
                throw ex;
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
            String query = "SELECT event_name FROM events WHERE available = 'No'";
            PreparedStatement pstmt = con.prepareStatement(query);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                bookedEvents.add(rs.getString("event_name"));
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(null, bookedEvents);
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

                    // Cancel booking
                    String cancelQuery = "DELETE FROM bookings WHERE event_id = ?";
                    PreparedStatement cancelStmt = con.prepareStatement(cancelQuery);
                    cancelStmt.setInt(1, eventId);
                    cancelStmt.executeUpdate();

                    // Update event availability
                    String updateQuery = "UPDATE events SET available = 'Yes' WHERE event_id = ?";
                    PreparedStatement updateStmt = con.prepareStatement(updateQuery);
                    updateStmt.setInt(1, eventId);
                    updateStmt.executeUpdate();

                    eventDetails.setText("⛔ Booking canceled for " + selectedEvent);
                    loadAvailableEvents(); // Refresh list

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
        // Dummy order list (Replace with SQL)
        eventDetails.setText("📜 Orders:\n1. Rock Concert - Booked\n2. Tech Conference - Pending");
    }

    /**
     * Calculate commission for the event organizers.
     */
    @FXML
    private void calculateCommission() {
        // Dummy commission logic (Replace with SQL)
        eventDetails.setText("💵 Commission: $2000");
    }

    /**
     * Import CSV file into the database.
     */

    @FXML
    private void importCSV() {
        final String CSV_FILE_PATH = "src/main/resources/requests.csv"; // Ensure the path is correct
        String insertQuery = "INSERT INTO events (client, event_name, artist, event_date, event_time, duration, target_audience, available) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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

                if (values.length < 7) { // Ensure correct number of columns
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
                    pstmt.setString(8, available);
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

    @FXML
    private void exitApplication() {
        Stage stage = (Stage) pageTitle.getScene().getWindow();
        stage.close();
    }




}
