import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestDbConnection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/campus_outfit?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai";
        String username = "root";
        String password = "password";

        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            System.out.println("Database connection successful!");
            connection.close();
        } catch (SQLException e) {
            System.err.println("Database connection failed:");
            e.printStackTrace();
        }
    }
}