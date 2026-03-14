package com.portfolio.oee;

import java.awt.*;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Vector;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

public class OEE_Calculator extends JFrame {

    private static final long serialVersionUID = 1L;
    private JProgressBar availabilityBar, performanceBar, qualityBar, oeeBar;
    private JLabel lblOeeResult, lblTimer, lblLogsTotales, lblPromedioVel;
    private JTextField txtVelocidadReal, txtLogsMalos;
    private JButton btnAction;
    private JTable downtimeTable;
    private DefaultTableModel tableModel;
    private ResourceBundle messages;
    
    private final double META_AVAIL = 0.85;
    private final double META_PERF = 0.90;
    private final double META_QUAL = 0.98;
    private final double META_OEE = 0.75;

    private final double VELOCIDAD_OBJETIVO = 180.0; 
    private final double LOGS_POR_MINUTO_OBJETIVO = 7.0; 
    
    private double logsProducidosSimulados = 0;
    private long tiempoTotalParadaMillis = 0;
    private long tiempoNetoOperacionMillis = 0;
    private long ultimaMarcaTiempo;
    private long inicioParadaActual = 0;
    
    private Timer mainTimer;
    private long startTime;
    private boolean isRunning = true; 

    public OEE_Calculator() {
        initLanguage();
        DatabaseManager.initializeDatabase();
        setupGUI();
        startLiveMonitoring();
    }

    private void initLanguage() {
        String[] options = {"English", "Español", "Português"};
        int choice = JOptionPane.showOptionDialog(null, "Select Language", "Setup",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        Locale locale = (choice == 2) ? Locale.forLanguageTag("pt") : (choice == 1) ? Locale.forLanguageTag("es") : Locale.ENGLISH;
        messages = ResourceBundle.getBundle("com.portfolio.oee.messages", locale);
    }

    private void setupGUI() {
        setTitle("Industrial OEE Tracker - Production Monitor");
        setSize(850, 950);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        UIManager.put("Panel.background", new Color(225, 225, 225));
        getContentPane().setBackground(new Color(225, 225, 225));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(messages.getString("tab_dashboard"), createDashboardPanel());
        tabbedPane.addTab(messages.getString("tab_log"), createLiveControlPanel());
        tabbedPane.addTab("Report", createHistoryPanel());

        add(tabbedPane);
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 15, 15));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        availabilityBar = createCustomBar("DISPONIBILIDAD (Meta: " + (int)(META_AVAIL*100) + "%)");
        performanceBar = createCustomBar("RENDIMIENTO (Meta: " + (int)(META_PERF*100) + "%)");
        qualityBar = createCustomBar("CALIDAD (Meta: " + (int)(META_QUAL*100) + "%)");
        oeeBar = createCustomBar("OEE TOTAL (Meta: " + (int)(META_OEE*100) + "%)");

        lblOeeResult = new JLabel("OEE: 0.0%", SwingConstants.CENTER);
        lblOeeResult.setFont(new Font("Segoe UI", Font.BOLD, 36));

        panel.add(availabilityBar); panel.add(performanceBar);
        panel.add(qualityBar); panel.add(oeeBar); panel.add(lblOeeResult);
        return panel;
    }

    private JProgressBar createCustomBar(String title) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setFont(new Font("Arial", Font.BOLD, 14));
        bar.setPreferredSize(new Dimension(600, 45));
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(new Font("Arial", Font.ITALIC, 12));
        bar.setBorder(border);
        bar.setBackground(Color.WHITE);
        return bar;
    }

    private JPanel createLiveControlPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel monitor = new JPanel(new GridLayout(3, 1, 10, 10));
        monitor.setBackground(Color.DARK_GRAY);
        
        lblTimer = new JLabel("00:00:00", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Monospaced", Font.BOLD, 45));
        lblTimer.setForeground(Color.CYAN);
        lblLogsTotales = new JLabel("LOGS: 0", SwingConstants.CENTER);
        lblLogsTotales.setFont(new Font("Arial", Font.BOLD, 22));
        lblLogsTotales.setForeground(Color.WHITE);
        lblPromedioVel = new JLabel("EFICIENCIA: 100%", SwingConstants.CENTER);
        lblPromedioVel.setForeground(Color.YELLOW);

        monitor.add(lblTimer); monitor.add(lblLogsTotales); monitor.add(lblPromedioVel);

        JPanel inputs = new JPanel(new GridLayout(3, 2, 15, 15));
        inputs.setOpaque(false);
        inputs.add(new JLabel("VELOCIDAD ACTUAL (mt/min):"));
        txtVelocidadReal = new JTextField("180");
        inputs.add(txtVelocidadReal);
        inputs.add(new JLabel("LOGS DEFECTUOSOS (SCRAP):"));
        txtLogsMalos = new JTextField("0");
        inputs.add(txtLogsMalos);

        btnAction = new JButton(messages.getString("btn_stop_machine"));
        btnAction.setBackground(new Color(200, 0, 0));
        btnAction.setForeground(Color.WHITE);
        btnAction.setFont(new Font("Arial", Font.BOLD, 24));
        btnAction.addActionListener(e -> toggleMachine());
        
        mainPanel.add(monitor, BorderLayout.NORTH);
        mainPanel.add(inputs, BorderLayout.CENTER);
        mainPanel.add(btnAction, BorderLayout.SOUTH);

        return mainPanel;
    }

    private void toggleMachine() {
        if (isRunning) {
            String reason = JOptionPane.showInputDialog(this, "Motivo de la parada:", "Downtime Log", JOptionPane.WARNING_MESSAGE);
            if (reason == null || reason.isEmpty()) reason = "Desconocido";
            inicioParadaActual = System.currentTimeMillis();
            isRunning = false;
            btnAction.setText(messages.getString("btn_start_prod"));
            btnAction.setBackground(new Color(0, 150, 0));
            DatabaseManager.insertDowntime(reason, 0); 
        } else {
            long duracionParada = (System.currentTimeMillis() - inicioParadaActual) / 60000;
            DatabaseManager.insertDowntime("End of Stop", duracionParada);
            isRunning = true;
            btnAction.setText(messages.getString("btn_stop_machine"));
            btnAction.setBackground(new Color(200, 0, 0));
            refreshReportData();
        }
    }

    private JPanel createHistoryPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Gráfico
        JPanel chartContainer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistoChart(g);
            }
        };
        chartContainer.setPreferredSize(new Dimension(800, 350));
        chartContainer.setBackground(Color.WHITE);
        chartContainer.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Tabla de Paradas
        String[] columnNames = {"Time", "Reason", "Duration"};
        tableModel = new DefaultTableModel(null, columnNames);
        downtimeTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(downtimeTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Downtime Events - Today"));

        JButton btnRefresh = new JButton("REFRESH REPORT");
        btnRefresh.addActionListener(e -> {
            chartContainer.repaint();
            refreshReportData();
        });

        mainPanel.add(chartContainer, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(btnRefresh, BorderLayout.SOUTH);

        return mainPanel;
    }

    private void refreshReportData() {
        tableModel.setDataVector(DatabaseManager.getTodayDowntimeList(), new Vector<>(java.util.Arrays.asList("Time", "Reason", "Duration")));
    }

    private void drawHistoChart(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double[] data = DatabaseManager.getTodayAverages(); 
        double[] metas = {META_AVAIL, META_PERF, META_QUAL, META_OEE};
        String[] labels = {"Avail.", "Perf.", "Qual.", "OEE"};

        int x = 100;
        int width = 70;
        int maxHeight = 250;
        int bottomY = 300;

        for (int i = 0; i < data.length; i++) {
            int barHeight = (int) (data[i] * maxHeight);
            g2.setColor(data[i] >= metas[i] ? new Color(40, 167, 69) : new Color(220, 53, 69));
            g2.fillRect(x, bottomY - barHeight, width, barHeight);
            
            g2.setColor(Color.BLACK);
            int metaY = bottomY - (int)(metas[i] * maxHeight);
            g2.drawLine(x - 5, metaY, x + width + 5, metaY);

            g2.drawString(labels[i], x + 15, bottomY + 20);
            g2.drawString(String.format("%.1f%%", data[i]*100), x + 10, bottomY - barHeight - 10);
            x += 160;
        }
    }

    private void startLiveMonitoring() {
        startTime = System.currentTimeMillis();
        ultimaMarcaTiempo = startTime;
        mainTimer = new Timer(1000, e -> {
            long ahora = System.currentTimeMillis();
            long tiempoTranscurrido = ahora - startTime;
            long delta = ahora - ultimaMarcaTiempo;
            ultimaMarcaTiempo = ahora;

            if (!isRunning) {
                tiempoTotalParadaMillis += delta;
            } else {
                tiempoNetoOperacionMillis += delta;
                try {
                    double vReal = Double.parseDouble(txtVelocidadReal.getText());
                    logsProducidosSimulados += (vReal / VELOCIDAD_OBJETIVO) * (LOGS_POR_MINUTO_OBJETIVO / 60.0);
                } catch (Exception ex) {}
            }
            updateUI(tiempoTranscurrido);
            calculateLiveOEE(tiempoTranscurrido);
        });
        mainTimer.start();
    }

    private void updateUI(long elapsed) {
        long sec = (elapsed / 1000) % 60;
        long min = (elapsed / (1000 * 60)) % 60;
        long hr = (elapsed / (1000 * 60 * 60));
        lblTimer.setText(String.format("%02d:%02d:%02d", hr, min, sec));
        lblLogsTotales.setText("TOTAL LOGS: " + (int)logsProducidosSimulados);
    }

    private void calculateLiveOEE(long totalTime) {
        if (totalTime <= 0) return;
        double avail = (double)(totalTime - tiempoTotalParadaMillis) / totalTime;
        double targetLogs = (tiempoNetoOperacionMillis / 60000.0) * LOGS_POR_MINUTO_OBJETIVO;
        double perf = (targetLogs > 0) ? Math.min(1.0, logsProducidosSimulados / targetLogs) : 1.0;
        double badLogs = 0;
        try { badLogs = Double.parseDouble(txtLogsMalos.getText()); } catch (Exception e) {}
        double qual = (logsProducidosSimulados > 0) ? Math.max(0, (logsProducidosSimulados - badLogs) / logsProducidosSimulados) : 1.0;
        double oee = avail * perf * qual;

        updateBar(availabilityBar, avail, META_AVAIL);
        updateBar(performanceBar, perf, META_PERF);
        updateBar(qualityBar, qual, META_QUAL);
        updateBar(oeeBar, oee, META_OEE);

        lblPromedioVel.setText(String.format("PERFORMANCE: %.1f%%", perf * 100));
        lblOeeResult.setText(String.format("OEE: %.1f%%", oee * 100));
        lblOeeResult.setForeground(oee >= META_OEE ? new Color(0, 120, 0) : Color.RED);
    }

    private void updateBar(JProgressBar bar, double value, double meta) {
        bar.setValue((int)(value * 100));
        bar.setForeground(value >= meta ? new Color(40, 167, 69) : new Color(220, 53, 69));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OEE_Calculator().setVisible(true));
    }
}