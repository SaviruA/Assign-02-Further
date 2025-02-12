package assighn.a2;

import sqlitedb.DatabaseConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ImportVenuesFromCSV {
    private static final String CSV_FILE_PATH = "src/main/resources/venues.csv";

    public static void main(String[] args) {
        String insertQuery = "INSERT INTO venues (name, capacity, suitable_for, category, booking_price, availability) VALUES (?, ?, ?, ?, ?, 'Available')";

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

                String[] values = line.split(",(?![^\\[]*\\])");

                if (values.length < 5) {
                    System.out.println("Skipping invalid line: " + line);
                    continue;
                }

                try {
                    pstmt.setString(1, values[0].trim()); // Venue Name
                    pstmt.setInt(2, Integer.parseInt(values[1].trim())); // Capacity
                    pstmt.setString(3, values[2].trim()); // Suitable For
                    pstmt.setString(4, values[3].trim()); // Category
                    pstmt.setInt(5, Integer.parseInt(values[4].trim())); // Booking Price

                    pstmt.addBatch();
                } catch (NumberFormatException e) {
                    System.out.println("Skipping line due to invalid number format: " + line);
                }
            }

            pstmt.executeBatch();
            System.out.println("Venues imported successfully!");

        } catch (IOException | SQLException e) {
            System.out.println("Error importing venues: " + e.getMessage());
        }
    }
}



