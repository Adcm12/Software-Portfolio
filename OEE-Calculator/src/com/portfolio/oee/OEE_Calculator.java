package com.portfolio.oee;

import java.awt.*;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Vector;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * OEE_Calculator: Main UI with real-time monitoring and professional PDF reporting.
 */
public class OEE_Calculator extends JFrame {

    private static final long serialVersionUID = 1L;
    private JProgressBar availabilityBar, performanceBar, qualityBar, oeeBar;
    private JLabel lblOeeResult, lblTimer, lblLogsTotales, lblPromedioVel, lblStatus;
    private JTextField txtVelocidadReal, txtLogsMalos;
    private JButton btnAction, btnExportPDF;
    private JTable downtimeTable;
    private DefaultTableModel tableModel;
    private ResourceBundle messages;
    private JPanel chartContainer;
    
    // Industrial targets for color-coding
    private final double META_AVAIL = 0.85;
    private final double META_PERF = 0.90;
    private final double META_QUAL = 0.98;
    private final double META_OEE = 0.75;
    
    private double logsEnSesion = 0;
    private long tiempoOpEnSesionMillis = 0;
    private double totalLogsUI = 0;
    private long tiempoTotalOperacionMillis = 0;
    private long tiempoTotalParadaMillis = 0;
    private long ultimaMarcaTiempo;
    private long inicioParadaActual = 0;
    private Timer mainTimer, autoSaveTimer;
    private long startTime;
    private boolean isRunning = true; 
    private String motivoActual = "";

    public OEE_Calculator() {
        initLanguage();
        DatabaseManager.initializeDatabase();
        setupGUI();
        startLiveMonitoring();
        startAutoSaveTimer();
    }

    private void initLanguage() {
        String[] options = {"English", "Español", "Português"};
        int choice = JOptionPane.showOptionDialog(null, "Seleccione Idioma", "Configuración",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        Locale locale = (choice == 2) ? Locale.forLanguageTag("pt") : (choice == 1) ? Locale.forLanguageTag("es") : Locale.ENGLISH;
        messages = ResourceBundle.getBundle("com.portfolio.oee.messages", locale);
    }

    private void setupGUI() {
        setTitle("Industrial OEE Tracker - Production Monitor");
        setSize(850, 950);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(messages.getString("tab_dashboard"), createDashboardPanel());
        tabbedPane.addTab(messages.getString("tab_log"), createLiveControlPanel());
        tabbedPane.addTab("Reportes Diarios", createHistoryPanel());
        add(tabbedPane, BorderLayout.CENTER);
        
        lblStatus = new JLabel(" Estado: Sistema Activo | Guardado automático habilitado", SwingConstants.LEFT);
        add(lblStatus, BorderLayout.SOUTH);
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        availabilityBar = createCustomBar("DISPONIBILIDAD");
        performanceBar = createCustomBar("RENDIMIENTO");
        qualityBar = createCustomBar("CALIDAD");
        oeeBar = createCustomBar("OEE TOTAL");
        lblOeeResult = new JLabel("OEE: 0.0%", SwingConstants.CENTER);
        lblOeeResult.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 40));
        panel.add(availabilityBar); panel.add(performanceBar);
        panel.add(qualityBar); panel.add(oeeBar); panel.add(lblOeeResult);
        return panel;
    }

    private JProgressBar createCustomBar(String title) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setBorder(BorderFactory.createTitledBorder(title));
        return bar;
    }

    private JPanel createLiveControlPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel monitor = new JPanel(new GridLayout(3, 1, 5, 5));
        monitor.setBackground(Color.DARK_GRAY);
        lblTimer = new JLabel("00:00:00", SwingConstants.CENTER);
        lblTimer.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 50));
        lblTimer.setForeground(Color.CYAN);
        lblLogsTotales = new JLabel("LOGS TOTALES: 0", SwingConstants.CENTER);
        lblLogsTotales.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 25));
        lblLogsTotales.setForeground(Color.WHITE);
        lblPromedioVel = new JLabel("EN MARCHA", SwingConstants.CENTER);
        lblPromedioVel.setForeground(Color.GREEN);
        monitor.add(lblTimer); monitor.add(lblLogsTotales); monitor.add(lblPromedioVel);

        JPanel inputs = new JPanel(new GridLayout(2, 2, 10, 10));
        inputs.add(new JLabel("VELOCIDAD REAL (mt/min):"));
        txtVelocidadReal = new JTextField("180");
        inputs.add(txtVelocidadReal);
        inputs.add(new JLabel("LOGS DEFECTUOSOS:"));
        txtLogsMalos = new JTextField("0");
        inputs.add(txtLogsMalos);

        btnAction = new JButton(messages.getString("btn_stop_machine"));
        btnAction.setBackground(new Color(200, 0, 0));
        btnAction.setForeground(Color.WHITE);
        btnAction.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 26));
        btnAction.addActionListener(e -> toggleMachine());
        
        mainPanel.add(monitor, BorderLayout.NORTH);
        mainPanel.add(inputs, BorderLayout.CENTER);
        mainPanel.add(btnAction, BorderLayout.SOUTH);
        return mainPanel;
    }

    private void toggleMachine() {
        if (isRunning) {
            String reason = JOptionPane.showInputDialog(this, "Razón de parada:", "Entrada de Parada", JOptionPane.WARNING_MESSAGE);
            if (reason == null || reason.isEmpty()) return;
            motivoActual = reason;
            saveDataToDB(); 
            inicioParadaActual = System.currentTimeMillis();
            isRunning = false;
            btnAction.setText(messages.getString("btn_start_prod"));
            btnAction.setBackground(new Color(0, 150, 0));
            lblPromedioVel.setText("PARADA: " + reason);
            lblPromedioVel.setForeground(Color.RED);
        } else {
            long duration = (System.currentTimeMillis() - inicioParadaActual) / 60000;
            DatabaseManager.insertDowntime(motivoActual, Math.max(1, (double)duration));
            isRunning = true;
            btnAction.setText(messages.getString("btn_stop_machine"));
            btnAction.setBackground(new Color(200, 0, 0));
            lblPromedioVel.setText("EN MARCHA");
            lblPromedioVel.setForeground(Color.GREEN);
            refreshReportData();
        }
    }

    private void startAutoSaveTimer() {
        autoSaveTimer = new Timer(5000, e -> {
            if (isRunning) {
                saveDataToDB();
                lblStatus.setText(" Estado: Datos guardados a las " + new java.util.Date().toString().substring(11,19));
            }
        });
        autoSaveTimer.start();
    }

    private synchronized void saveDataToDB() {
        double minOp = (double)tiempoOpEnSesionMillis / 60000.0;
        double scrap = 0;
        try { scrap = Double.parseDouble(txtLogsMalos.getText()); } catch(Exception e){}
        DatabaseManager.insertProduction(logsEnSesion, scrap, minOp);
        txtLogsMalos.setText("0"); 
        logsEnSesion = 0;
        tiempoOpEnSesionMillis = 0;
        refreshReportData();
    }

    private JPanel createHistoryPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        chartContainer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistoChart(g);
            }
        };
        chartContainer.setPreferredSize(new Dimension(800, 320));
        chartContainer.setBackground(Color.WHITE);

        tableModel = new DefaultTableModel(null, new String[]{"Hora", "Razón", "Duración"});
        downtimeTable = new JTable(tableModel);
        
        btnExportPDF = new JButton("Generar Reporte PDF para Gerencia");
        btnExportPDF.addActionListener(e -> generatePDFReport());

        mainPanel.add(chartContainer, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(downtimeTable), BorderLayout.CENTER);
        mainPanel.add(btnExportPDF, BorderLayout.SOUTH);
        return mainPanel;
    }

    private void generatePDFReport() {
        Document document = new Document();
        try {
            String fileName = "Reporte_OEE_" + System.currentTimeMillis() + ".pdf";
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();
            
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD);
            document.add(new Paragraph("Reporte de Desempeño OEE - Resumen de Producción", titleFont));
            document.add(new Paragraph("Generado el: " + new java.util.Date().toString()));
            document.add(new Paragraph("------------------------------------------------------------------------------------------"));
            
            double[] data = DatabaseManager.getTodayAverages();
            document.add(new Paragraph("\n1. RESUMEN OPERACIONAL:"));
            document.add(new Paragraph("Tiempo Total de Máquina (En marcha): " + String.format("%.2f min", data[4])));
            document.add(new Paragraph("Disponibilidad: " + String.format("%.2f%%", data[0]*100)));
            document.add(new Paragraph("Rendimiento: " + String.format("%.2f%%", data[1]*100)));
            document.add(new Paragraph("Calidad: " + String.format("%.2f%%", data[2]*100)));
            document.add(new Paragraph("PUNTAJE OEE TOTAL: " + String.format("%.2f%%", data[3]*100)));
            
            document.add(new Paragraph("\n2. DETALLE DE PARADAS:"));
            PdfPTable table = new PdfPTable(3);
            table.addCell("Hora"); table.addCell("Motivo"); table.addCell("Duración");
            for(Vector<Object> row : DatabaseManager.getTodayDowntimeList()) {
                table.addCell(row.get(0).toString());
                table.addCell(row.get(1).toString());
                table.addCell(row.get(2).toString());
            }
            document.add(table);
            
            document.close();
            JOptionPane.showMessageDialog(this, "¡Éxito! Reporte guardado como: " + fileName);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al generar reporte: " + e.getMessage());
        }
    }

    private void refreshReportData() {
        tableModel.setDataVector(DatabaseManager.getTodayDowntimeList(), new Vector<>(java.util.Arrays.asList("Hora", "Motivo", "Duración")));
        chartContainer.repaint();
    }

    private void drawHistoChart(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double[] data = DatabaseManager.getTodayAverages(); 
        String[] labels = {"Disp.", "Rend.", "Calid.", "OEE"};
        int x = 100, width = 70, maxHeight = 220, bottomY = 260;
        double[] metas = {META_AVAIL, META_PERF, META_QUAL, META_OEE};

        for (int i = 0; i < 4; i++) {
            int barHeight = (int) (data[i] * maxHeight);
            // Color dinámico: Verde si cumple meta, Rojo si no
            g2.setColor(data[i] >= metas[i] ? new Color(40, 167, 69) : new Color(220, 53, 69));
            g2.fillRect(x, bottomY - barHeight, width, barHeight);
            g2.setColor(Color.BLACK);
            g2.drawString(labels[i], x + 15, bottomY + 20);
            g2.drawString(String.format("%.1f%%", data[i]*100), x + 10, bottomY - barHeight - 10);
            x += 160;
        }
        g2.drawString("Tiempo decorrido (Uptime): " + String.format("%.2f", data[4]) + " min", 50, 310);
    }

    private void startLiveMonitoring() {
        startTime = System.currentTimeMillis();
        ultimaMarcaTiempo = startTime;
        mainTimer = new Timer(1000, e -> {
            long ahora = System.currentTimeMillis();
            long delta = ahora - ultimaMarcaTiempo;
            ultimaMarcaTiempo = ahora;
            if (isRunning) {
                tiempoTotalOperacionMillis += delta;
                tiempoOpEnSesionMillis += delta;
                try {
                    double vReal = Double.parseDouble(txtVelocidadReal.getText());
                    double nuevosLogs = (vReal / 180.0) * (7.0 / 60.0);
                    totalLogsUI += nuevosLogs;
                    logsEnSesion += nuevosLogs;
                } catch (Exception ex) {}
            } else {
                tiempoTotalParadaMillis += delta;
            }
            updateUI(ahora - startTime);
            calculateLiveOEE(ahora - startTime);
        });
        mainTimer.start();
    }

    private void updateUI(long elapsed) {
        long sec = (elapsed / 1000) % 60;
        long min = (elapsed / (1000 * 60)) % 60;
        long hr = (elapsed / (1000 * 60 * 60));
        lblTimer.setText(String.format("%02d:%02d:%02d", hr, min, sec));
        lblLogsTotales.setText("LOGS TOTALES: " + (int)totalLogsUI);
    }

    private void calculateLiveOEE(long totalTime) {
        if (totalTime <= 0) return;
        double avail = (double)(totalTime - tiempoTotalParadaMillis) / totalTime;
        double targetLogs = (tiempoTotalOperacionMillis / 60000.0) * 7.0;
        double perf = (targetLogs > 0) ? Math.min(1.0, totalLogsUI / targetLogs) : 1.0;
        double badLogs = 0;
        try { badLogs = Double.parseDouble(txtLogsMalos.getText()); } catch (Exception e) {}
        double qual = (totalLogsUI > 0) ? Math.max(0, (totalLogsUI - badLogs) / totalLogsUI) : 1.0;
        double oee = avail * perf * qual;

        updateBarColor(availabilityBar, avail, META_AVAIL);
        updateBarColor(performanceBar, perf, META_PERF);
        updateBarColor(qualityBar, qual, META_QUAL);
        updateBarColor(oeeBar, oee, META_OEE);
        
        lblOeeResult.setText(String.format("OEE: %.1f%%", oee * 100));
        lblOeeResult.setForeground(oee >= META_OEE ? new Color(0, 120, 0) : Color.RED);
    }

    private void updateBarColor(JProgressBar bar, double val, double meta) {
        bar.setValue((int)(val * 100));
        bar.setForeground(val >= meta ? new Color(40, 167, 69) : new Color(220, 53, 69));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OEE_Calculator().setVisible(true));
    }
}