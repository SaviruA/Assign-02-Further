package sqlitedb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UsePreparedStatement {
	public static void main(String[] args) {
		final String TABLE_NAME = "Student";
		String sql = "INSERT INTO " + TABLE_NAME + " (id, student_number, first_name, last_name)" +
				" VALUES (?, ?, ?, ?)";

		try (Connection con = DatabaseConnection.getConnection(); 
				PreparedStatement stmt = con.prepareStatement(sql);) {
			stmt.setInt(1, 2);
			stmt.setString(2, "s3089940");
			stmt.setString(3, "Tom");
			stmt.setString(4, "Bruster");

			int result = stmt.executeUpdate(); 

			if (result == 1) {
				System.out.println("Insert into table " + TABLE_NAME + " executed successfully");
				System.out.println(result + " row(s) affected");
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
}
