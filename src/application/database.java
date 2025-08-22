package application;

import java.sql.Connection;
import java.sql.DriverManager;

public class database {
	public static Connection connect() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			
			Connection connect = DriverManager.getConnection("jdbc:mysql://localhost/master", "marco", "29Papa278.");
			
			return connect;
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
