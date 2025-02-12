package sqlitedb;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class CreateTable {
	public static void main(String[] args) {
		final String TABLE_NAME = "venues";

		try (Connection con = DatabaseConnection.getConnection();
			 Statement stmt = con.createStatement()) {

			stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bookings (
                    booking_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_id INTEGER NOT NULL,
                    venue_id INTEGER NOT NULL,
                    booking_date TEXT NOT NULL,
                    booking_time TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'Confirmed',
                    FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE,
                    FOREIGN KEY (venue_id) REFERENCES venues(venue_id) ON DELETE CASCADE,
                    UNIQUE (venue_id, booking_date, booking_time) -- Prevents double booking
                );""");
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
}

