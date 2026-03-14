package com.portfolio.oee;

import java.sql.*;
import java.util.Vector;
import java.util.Calendar;

/**
 * DatabaseManager: Handles SQLite persistence for industrial production and downtime.
 */
public class DatabaseManager {

    private static final String URL = "jdbc:sqlite:oee_data.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(URL);
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Database Connection Error: " + e.getMessage());
        }
        return conn;
    }

    public static void initializeDatabase() {
        String createProductionTable = "CREATE TABLE IF NOT EXISTS production ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "logs_produced REAL,"
                + "defects REAL,"
                + "operating_time_mins REAL"
                + ");";

        String createDowntimeTable = "CREATE TABLE IF NOT EXISTS downtime ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "reason TEXT,"
                + "duration_mins REAL"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createProductionTable);
            stmt.execute(createDowntimeTable);
        } catch (SQLException e) {
            System.out.println("Database Initialization Error: " + e.getMessage());
        }
    }

    public static void insertProduction(double logs, double defects, double timeMins) {
        if (logs <= 0 && timeMins <= 0) return;
        String sql = "INSERT INTO production(logs_produced, defects, operating_time_mins) VALUES(?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, logs);
            pstmt.setDouble(2, defects);
            pstmt.setDouble(3, timeMins);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Insert Production Error: " + e.getMessage());
        }
    }

    public static void insertDowntime(String reason, double duration) {
        String sql = "INSERT INTO downtime(reason, duration_mins) VALUES(?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, reason);
            pstmt.setDouble(2, duration);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Insert Downtime Error: " + e.getMessage());
        }
    }

    public static double[] getTodayAverages() {
        double avail = 1.0, perf = 0.0, qual = 1.0, oee = 0.0, totalUptime = 0.0;
        String sqlData = "SELECT SUM(logs_produced) as t_logs, SUM(defects) as t_def, SUM(operating_time_mins) as t_op " +
                         "FROM production WHERE date(timestamp) = date('now')";
        String sqlDown = "SELECT SUM(duration_mins) as t_down FROM downtime WHERE date(timestamp) = date('now')";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rsProd = stmt.executeQuery(sqlData);
            if (rsProd.next()) {
                double totalLogs = rsProd.getDouble("t_logs");
                double totalDefects = rsProd.getDouble("t_def");
                totalUptime = rsProd.getDouble("t_op");
                if (totalLogs > 0) qual = Math.max(0, (totalLogs - totalDefects) / totalLogs);
                double expectedLogs = totalUptime * 7.0; 
                if (expectedLogs > 0) perf = Math.min(1.0, totalLogs / expectedLogs);
            }

            ResultSet rsDown = stmt.executeQuery(sqlDown);
            double totalDownMins = rsDown.next() ? rsDown.getDouble("t_down") : 0;
            
            Calendar cal = Calendar.getInstance();
            long now = cal.getTimeInMillis();
            cal.set(Calendar.HOUR_OF_DAY, 6); // Shift starts at 6 AM
            cal.set(Calendar.MINUTE, 0);
            double shiftElapsed = (now - cal.getTimeInMillis()) / 60000.0;
            if (shiftElapsed <= 0) shiftElapsed = 1;

            avail = Math.max(0, (shiftElapsed - totalDownMins) / shiftElapsed);
            oee = avail * perf * qual;
        } catch (SQLException e) {
            System.out.println("Calculation Error: " + e.getMessage());
        }
        return new double[]{avail, perf, qual, oee, totalUptime};
    }

    public static Vector<Vector<Object>> getTodayDowntimeList() {
        Vector<Vector<Object>> data = new Vector<>();
        String sql = "SELECT strftime('%H:%M:%S', timestamp) as hora, reason, duration_mins FROM downtime " +
                     "WHERE date(timestamp) = date('now') AND duration_mins > 0 ORDER BY timestamp DESC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("hora"));
                row.add(rs.getString("reason"));
                row.add(rs.getDouble("duration_mins") + " min");
                data.add(row);
            }
        } catch (SQLException e) {
            System.out.println("List Loading Error: " + e.getMessage());
        }
        return data;
    }
}