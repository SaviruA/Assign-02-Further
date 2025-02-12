package sqlitedb;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class UpdateTable {
	public static void main(String[] args) {
		final String TABLE_NAME = "venues"; // Update the correct table name

		try (Connection con = DatabaseConnection.getConnection();
			 Statement stmt = con.createStatement()) {

			// SQL to update all venue entries to 'Available'
			String sql = "UPDATE " + TABLE_NAME + " SET availability = 'Available'";

			int result = stmt.executeUpdate(sql);

			if (result > 0) {
				System.out.println("✅ Successfully updated " + TABLE_NAME);
				System.out.println(result + " row(s) affected.");
			} else {
				System.out.println("⚠ No rows were updated. Check if table exists and contains data.");
			}

		} catch (SQLException e) {
			System.out.println("❌ Error updating table: " + e.getMessage());
		}
	}
}
