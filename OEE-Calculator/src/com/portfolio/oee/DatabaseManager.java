package com.portfolio.oee;

import java.sql.*;
import java.util.Vector;

public class DatabaseManager {

    private static final String URL = "jdbc:sqlite:oee_data.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(URL);
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Error de conexión: " + e.getMessage());
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

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createProductionTable);
            stmt.execute(createDowntimeTable);
        } catch (SQLException e) {
            System.out.println("Error de inicialización: " + e.getMessage());
        }
    }

    public static void insertProduction(double logs, double defects, double timeMins) {
        String sql = "INSERT INTO production(logs_produced, defects, operating_time_mins) VALUES(?,?,?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, logs);
            pstmt.setDouble(2, defects);
            pstmt.setDouble(3, timeMins);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error al insertar producción: " + e.getMessage());
        }
    }

    public static void insertDowntime(String reason, double duration) {
        String sql = "INSERT INTO downtime(reason, duration_mins) VALUES(?,?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, reason);
            pstmt.setDouble(2, duration);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error al insertar parada: " + e.getMessage());
        }
    }

    public static double[] getTodayAverages() {
        double avail = 1.0, perf = 0.0, qual = 1.0, oee = 0.0;
        // Obtenemos sumatoria de producción de hoy
        String sqlData = "SELECT SUM(logs_produced) as t_logs, SUM(defects) as t_def, SUM(operating_time_mins) as t_op " +
                         "FROM production WHERE date(timestamp) = date('now')";
        // Obtenemos sumatoria de paradas de hoy
        String sqlDown = "SELECT SUM(duration_mins) as t_down FROM downtime WHERE date(timestamp) = date('now')";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rsProd = stmt.executeQuery(sqlData);
            if (rsProd.next()) {
                double totalLogs = rsProd.getDouble("t_logs");
                double totalDefects = rsProd.getDouble("t_def");
                double totalOpTime = rsProd.getDouble("t_op");
                
                if (totalLogs > 0) qual = (totalLogs - totalDefects) / totalLogs;
                
                // Meta: 7 logs por minuto. Performance = logs reales / (tiempo operado * 7)
                double logsEsperados = totalOpTime * 7.0;
                if (logsEsperados > 0) perf = Math.min(1.0, totalLogs / logsEsperados);
            }

            ResultSet rsDown = stmt.executeQuery(sqlDown);
            double totalDown = rsDown.next() ? rsDown.getDouble("t_down") : 0;
            
            // Disponibilidad basada en un turno de 8 horas (480 min)
            double tiempoTurno = 480.0;
            avail = Math.max(0, (tiempoTurno - totalDown) / tiempoTurno);
            
            oee = avail * perf * qual;

        } catch (SQLException e) {
            System.out.println("Error en cálculos: " + e.getMessage());
        }
        return new double[]{avail, perf, qual, oee};
    }

    public static Vector<Vector<Object>> getTodayDowntimeList() {
        Vector<Vector<Object>> data = new Vector<>();
        String sql = "SELECT strftime('%H:%M:%S', timestamp) as hora, reason, duration_mins FROM downtime " +
                     "WHERE date(timestamp) = date('now') AND duration_mins > 0 ORDER BY timestamp DESC";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getString("hora"));
                row.add(rs.getString("reason"));
                row.add(rs.getDouble("duration_mins") + " min");
                data.add(row);
            }
        } catch (SQLException e) {
            System.out.println("Error tabla paradas: " + e.getMessage());
        }
        return data;
    }
}