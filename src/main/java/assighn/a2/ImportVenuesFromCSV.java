package assighn.a2;

import sqlitedb.DatabaseConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ImportVenuesFromCSV {
    private static final String CSV_FILE_PATH = "src/main/resources/venues.csv"; // Update with correct path

    public static void main(String[] args) {
        String insertQuery = "INSERT INTO venues (name, capacity, suitable_for, category, booking_price) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection();
             BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH));
             PreparedStatement pstmt = con.prepareStatement(insertQuery)) {

            String line;
            boolean firstLine = true; // Skip CSV header

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header row
                }

                // **Fix: Split by commas, while keeping text inside quotes together**
                String[] values = line.split(",(?![^\\[]*\\])");

                if (values.length < 5) {
                    System.out.println("Skipping invalid line: " + line);
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

                    pstmt.setString(1, venueName);
                    pstmt.setInt(2, capacity);
                    pstmt.setString(3, suitableFor);
                    pstmt.setString(4, category);
                    pstmt.setInt(5, bookingPrice);

                    pstmt.addBatch(); // Add to batch
                } catch (NumberFormatException e) {
                    System.out.println("Skipping line due to invalid number format: " + line);
                }
            }

            pstmt.executeBatch(); // Execute batch insert
            System.out.println("Venues imported successfully!");

        } catch (IOException | SQLException e) {
            System.out.println("Error importing venues: " + e.getMessage());
        }
    }
}


