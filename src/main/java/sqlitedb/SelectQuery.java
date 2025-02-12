package sqlitedb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SelectQuery {
	public static void main(String[] args) {
		final String TABLE_NAME = "STUDENT";

		try (Connection con = DatabaseConnection.getConnection();
				Statement stmt = con.createStatement();) {
			String query = "SELECT * FROM " + TABLE_NAME;

			try (ResultSet resultSet = stmt.executeQuery(query)) {
				while(resultSet.next()) {
					System.out.printf("Id: %d | Student Number: %s | First Name: %s | Last Name: %s\n",
							resultSet.getInt("id"), resultSet.getString("student_number"),
							resultSet.getString("first_name"), resultSet.getString("last_name"));
				}
			} 
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
}
