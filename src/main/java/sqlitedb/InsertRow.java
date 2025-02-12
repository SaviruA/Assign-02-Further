package sqlitedb;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class InsertRow {
	public static void main(String[] args) {
		final String TABLE_NAME = "users";

		try (Connection con = DatabaseConnection.getConnection();
			 Statement stmt = con.createStatement()) {

			String query = "INSERT INTO " + TABLE_NAME +
					" (id, username, password, first_name, last_name, role) VALUES " +
					"(2, 'staff', 'steph123', 'Steph', 'Curry', 'Staff')";

			int result = stmt.executeUpdate(query);

			if (result == 1) {
				System.out.println("Insert into table " + TABLE_NAME + " executed successfully");
				System.out.println(result + " row(s) affected");
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
}

