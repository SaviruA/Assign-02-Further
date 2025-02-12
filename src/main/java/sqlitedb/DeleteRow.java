package sqlitedb;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DeleteRow {
	public static void main(String[] args) {
		final String TABLE_NAME = "venues";

		try (Connection con = DatabaseConnection.getConnection();
			 Statement stmt = con.createStatement()) {

			String sql = "DELETE FROM " + TABLE_NAME +
					" WHERE id BETWEEN 62 AND 92";

			int result = stmt.executeUpdate(sql);

			System.out.println("Delete from table " + TABLE_NAME + " executed successfully");
			System.out.println(result + " row(s) affected");

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
}

