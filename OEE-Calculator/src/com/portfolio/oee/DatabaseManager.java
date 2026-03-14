package com.portfolio.oee;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles all SQLite database operations for the OEE Tracker.
 * Initializes local storage and provides methods for CRUD operations.
 */
public class DatabaseManager {

    private static final String URL = "jdbc:sqlite:oee_data.db";

    /**
     * Establishes a connection to the SQLite database.
     * Creates the database file if it does not exist.
     */
    public static Connection connect() {

        Connection conn = null;
        try {
        	Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(URL);
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
        return conn;
    }

    /**
     * Initializes the database schema.
     */
    public static void initializeDatabase() {
        String createProductionTable = "CREATE TABLE IF NOT EXISTS production ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT DEFAULT CURRENT_DATE,"
                + "planned_time_mins REAL,"
                + "ideal_cycle_time_mins REAL,"
                + "total_produced INTEGER,"
                + "defects INTEGER"
                + ");";

        String createDowntimeTable = "CREATE TABLE IF NOT EXISTS downtime ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT DEFAULT CURRENT_DATE,"
                + "reason TEXT,"
                + "duration_mins REAL"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createProductionTable);
            stmt.execute(createDowntimeTable);
        } catch (SQLException e) {
            System.out.println("Initialization error: " + e.getMessage());
        }
    }

    // Insert new production log
    public static void insertProduction(double plannedTime, double idealCycle, int total, int defects) {
        String sql = "INSERT INTO production(planned_time_mins, ideal_cycle_time_mins, total_produced, defects) VALUES(?,?,?,?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, plannedTime);
            pstmt.setDouble(2, idealCycle);
            pstmt.setInt(3, total);
            pstmt.setInt(4, defects);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Insert error: " + e.getMessage());
        }
    }

    // Insert new downtime log
    public static void insertDowntime(String reason, double duration) {
        String sql = "INSERT INTO downtime(reason, duration_mins) VALUES(?,?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, reason);
            pstmt.setDouble(2, duration);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Downtime insert error: " + e.getMessage());
        }
    }

    // Aggregate total downtime for today
    public static double getTodayTotalDowntime() {
        String sql = "SELECT SUM(duration_mins) as total_down FROM downtime WHERE date = CURRENT_DATE";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble("total_down");
            }
        } catch (SQLException e) {
            System.out.println("Query error: " + e.getMessage());
        }
        return 0;
    }

    // Get today's latest production metrics for calculation
    public static double[] getTodayProductionMetrics() {
        String sql = "SELECT SUM(planned_time_mins) as pt, AVG(ideal_cycle_time_mins) as ict, "
                   + "SUM(total_produced) as tp, SUM(defects) as def "
                   + "FROM production WHERE date = CURRENT_DATE";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new double[]{
                    rs.getDouble("pt"),
                    rs.getDouble("ict"),
                    rs.getDouble("tp"),
                    rs.getDouble("def")
                };
            }
        } catch (SQLException e) {
            System.out.println("Query error: " + e.getMessage());
        }
        return new double[]{0, 0, 0, 0};
    }
}