package assighn.a2;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import sqlitedb.DatabaseConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ManagementController {

    /**
     * Display Venue Utilization Percentage in a Pie Chart.
     */
    @FXML
    private void showVenueUtilization() {
        Stage stage = new Stage();
        stage.setTitle("Venue Utilization Percentage");

        PieChart pieChart = new PieChart();
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

        String query = """
            SELECT v.name, COUNT(b.venue_id) AS total_bookings
            FROM venues v
            LEFT JOIN bookings b ON v.id = b.venue_id
            GROUP BY v.name
            ORDER BY total_bookings DESC
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String venueName = rs.getString("name");
                int bookings = rs.getInt("total_bookings");
                pieChartData.add(new PieChart.Data(venueName, bookings));
            }

        } catch (SQLException e) {
            showError("Error fetching venue utilization data: " + e.getMessage());
        }

        pieChart.setData(pieChartData);
        VBox vbox = new VBox(pieChart);
        Scene scene = new Scene(vbox, 600, 400);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Display Income vs Commission per Order in a Bar Chart.
     */
    @FXML
    private void showIncomeCommission() {
        Stage stage = new Stage();
        stage.setTitle("Income vs Commission per Order");

        BarChart<String, Number> barChart = new BarChart<>(new javafx.scene.chart.CategoryAxis(), new javafx.scene.chart.NumberAxis());
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        XYChart.Series<String, Number> commissionSeries = new XYChart.Series<>();

        incomeSeries.setName("Income $");
        commissionSeries.setName("Commission $");

        String query = """
            SELECT e.event_name, v.booking_price,
                   CASE WHEN (SELECT COUNT(*) FROM events e2 WHERE e2.client = e.client) > 1 
                        THEN v.booking_price * 0.09 
                        ELSE v.booking_price * 0.10 
                   END AS commission
            FROM bookings b
            JOIN events e ON b.event_id = e.event_id
            JOIN venues v ON b.venue_id = v.id
            ORDER BY v.booking_price DESC
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String eventName = rs.getString("event_name");
                double income = rs.getDouble("booking_price");
                double commission = rs.getDouble("commission");

                incomeSeries.getData().add(new XYChart.Data<>(eventName, income));
                commissionSeries.getData().add(new XYChart.Data<>(eventName, commission));
            }

        } catch (SQLException e) {
            showError("Error fetching income and commission data: " + e.getMessage());
        }

        barChart.getData().addAll(incomeSeries, commissionSeries);
        VBox vbox = new VBox(barChart);
        Scene scene = new Scene(vbox, 800, 500);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Backup Data to LMVM File.
     */
    @FXML
    private void backupToLMVM() {
        File backupFile = new File("backup.lmvm");

        try (Connection con = DatabaseConnection.getConnection()) {
            StringBuilder backupData = new StringBuilder();

            // Tables to back up
            String[] tables = {"bookings", "events", "users", "venues"};

            for (String table : tables) {
                backupData.append("TABLE: ").append(table).append("\n");

                String query = "SELECT * FROM " + table;
                try (PreparedStatement stmt = con.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    int columnCount = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            backupData.append(rs.getMetaData().getColumnName(i))
                                    .append("=")
                                    .append(rs.getString(i))
                                    .append("; ");
                        }
                        backupData.append("\n");
                    }
                }
                backupData.append("\n");
            }

            Files.writeString(backupFile.toPath(), backupData.toString());
            showSuccess("Backup Successful", "Data successfully backed up to 'backup.lmvm'.");

        } catch (SQLException | IOException e) {
            showError("Failed to create backup: " + e.getMessage());
        }
    }



    /**
     * Load Data from LMVM File.
     */
    @FXML
    private void loadFromLMVM() {
        File backupFile = new File("backup.lmvm");

        if (!backupFile.exists()) {
            showError("No backup file found. Please create a backup first.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false); // Transaction start

            String content = Files.readString(backupFile.toPath());
            String[] sections = content.split("TABLE: ");

            for (String section : sections) {
                if (section.isBlank()) continue;

                String[] lines = section.split("\n");
                String tableName = lines[0].trim();
                String columnsQuery = getColumnNamesQuery(tableName, con);

                if (columnsQuery.isEmpty()) continue;

                String insertQuery = "INSERT INTO " + tableName + " " + columnsQuery + " VALUES ";
                StringBuilder values = new StringBuilder();

                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].isBlank()) continue;

                    String[] keyValues = lines[i].split("; ");
                    StringBuilder row = new StringBuilder("(");

                    for (String keyValue : keyValues) {
                        String[] splitData = keyValue.split("=");
                        if (splitData.length > 1) {
                            row.append("'").append(splitData[1].replace("'", "''")).append("', ");
                        }
                    }

                    if (row.length() > 2) {
                        row.setLength(row.length() - 2); // Remove last comma
                    }
                    row.append("), ");
                    values.append(row);
                }

                if (values.length() > 2) {
                    values.setLength(values.length() - 2); // Remove last comma
                    insertQuery += values.toString();

                    try (PreparedStatement stmt = con.prepareStatement(insertQuery)) {
                        stmt.executeUpdate();
                    }
                }
            }

            con.commit();
            showSuccess("Restore Successful", "Backup data restored successfully.");

        } catch (IOException | SQLException e) {
            showError("Failed to restore backup: " + e.getMessage());
        }
    }


    /**
     * Retrieves the column names for a table to generate INSERT statements.
     */
    private String getColumnNamesQuery(String tableName, Connection con) throws SQLException {
        String query = "PRAGMA table_info(" + tableName + ")";
        StringBuilder columns = new StringBuilder("(");

        try (PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                columns.append(rs.getString("name")).append(", ");
            }
        }

        if (columns.length() > 2) {
            columns.setLength(columns.length() - 2); // Remove last comma
        }
        columns.append(")");

        return columns.toString();
    }



    /**
     * Display an error message in an alert.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("⚠ Something went wrong");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Display a success message in an alert.
     */
    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("✅ Success");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void closeWindow(javafx.event.ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

}
