package com.portfolio.oee;

import java.awt.*;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;

public class OEE_Calculator extends JFrame {

    private static final long serialVersionUID = 1L;
    private JProgressBar availabilityBar, performanceBar, qualityBar, oeeBar;
    private JLabel lblOeeResult, lblTimer, lblShiftStatus, lblLogsTotales, lblPromedioVel;
    private JTextField txtVelocidadReal, txtLogsMalos;
    private JButton btnAction;
    private ResourceBundle messages;
    
    // --- PARÁMETROS TÉCNICOS ---
    private final double VELOCIDAD_OBJETIVO = 180.0; 
    private final double LOGS_POR_MINUTO_OBJETIVO = 7.0; 
    
    // --- ACUMULADORES ---
    private double logsProducidosSimulados = 0;
    private long tiempoTotalParadaMillis = 0;
    private long tiempoNetoOperacionMillis = 0; // Tiempo que la máquina ha estado RUNNING
    private long ultimaMarcaTiempo;
    
    private Timer mainTimer;
    private long startTime;
    private int currentShift = 1; 
    private boolean isRunning = true; 

    public OEE_Calculator() {
        initLanguage();
        DatabaseManager.initializeDatabase();
        setupGUI();
        startLiveMonitoring();
    }

    private void initLanguage() {
        String[] options = {"English", "Español", "Português"};
        int choice = JOptionPane.showOptionDialog(null, "Seleccione Idioma", "Configuración",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        Locale locale = (choice == 2) ? Locale.forLanguageTag("pt") : (choice == 1) ? Locale.forLanguageTag("es") : Locale.ENGLISH;
        messages = ResourceBundle.getBundle("com.portfolio.oee.messages", locale);
    }

    private void setupGUI() {
        setTitle(messages.getString("window_title") + " - Monitor Syneco Style");
        setSize(750, 850);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(messages.getString("tab_dashboard"), createDashboardPanel());
        tabbedPane.addTab(messages.getString("tab_log"), createLiveControlPanel());
        add(tabbedPane);
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        availabilityBar = createCustomBar(messages.getString("lbl_availability"));
        performanceBar = createCustomBar(messages.getString("lbl_performance"));
        qualityBar = createCustomBar(messages.getString("lbl_quality"));
        oeeBar = createCustomBar(messages.getString("lbl_oee"));
        oeeBar.setForeground(new Color(34, 139, 34));

        lblOeeResult = new JLabel("OEE: 0.0%", SwingConstants.CENTER);
        lblOeeResult.setFont(new Font("Arial", Font.BOLD, 32));

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
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel monitor = new JPanel(new GridLayout(3, 1, 5, 5));
        monitor.setBackground(new Color(235, 240, 250));
        
        lblTimer = new JLabel("00:00:00", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Monospaced", Font.BOLD, 35));
        
        lblLogsTotales = new JLabel("LOGS PRODUCIDOS: 0", SwingConstants.CENTER);
        lblLogsTotales.setFont(new Font("Arial", Font.BOLD, 20));
        
        lblPromedioVel = new JLabel("EFICIENCIA DE VELOCIDAD: 100%", SwingConstants.CENTER);
        lblPromedioVel.setForeground(Color.BLUE);

        monitor.add(lblTimer);
        monitor.add(lblLogsTotales);
        monitor.add(lblPromedioVel);

        JPanel inputs = new JPanel(new GridLayout(4, 2, 10, 10));
        inputs.setBorder(BorderFactory.createTitledBorder("Control de Sensor Manual"));
        
        inputs.add(new JLabel("Velocidad Real (mt/min):"));
        txtVelocidadReal = new JTextField("180");
        inputs.add(txtVelocidadReal);
        
        inputs.add(new JLabel("Logs Defectuosos (Scrap):"));
        txtLogsMalos = new JTextField("0");
        inputs.add(txtLogsMalos);

        btnAction = new JButton(messages.getString("btn_stop_machine"));
        btnAction.setBackground(Color.RED);
        btnAction.setForeground(Color.WHITE);
        btnAction.setFont(new Font("Arial", Font.BOLD, 20));
        btnAction.addActionListener(e -> toggleMachine());
        
        mainPanel.add(monitor, BorderLayout.NORTH);
        mainPanel.add(inputs, BorderLayout.CENTER);
        mainPanel.add(btnAction, BorderLayout.SOUTH);

        return mainPanel;
    }

    private void toggleMachine() {
        isRunning = !isRunning;
        if (isRunning) {
            btnAction.setText(messages.getString("btn_stop_machine"));
            btnAction.setBackground(Color.RED);
        } else {
            btnAction.setText(messages.getString("btn_start_prod"));
            btnAction.setBackground(Color.GREEN);
        }
    }

    private void startLiveMonitoring() {
        startTime = System.currentTimeMillis();
        ultimaMarcaTiempo = startTime;

        mainTimer = new Timer(1000, e -> {
            long ahora = System.currentTimeMillis();
            long tiempoTranscurridoTurno = ahora - startTime;
            long delta = ahora - ultimaMarcaTiempo;
            ultimaMarcaTiempo = ahora;

            if (!isRunning) {
                tiempoTotalParadaMillis += delta;
            } else {
                tiempoNetoOperacionMillis += delta;
                try {
                    double vReal = Double.parseDouble(txtVelocidadReal.getText());
                    double logsPorSegundo = (vReal / VELOCIDAD_OBJETIVO) * (LOGS_POR_MINUTO_OBJETIVO / 60.0);
                    logsProducidosSimulados += logsPorSegundo;
                } catch (Exception ex) {}
            }

            if (tiempoTranscurridoTurno >= 28800000) resetShift();

            updateUI(tiempoTranscurridoTurno);
            calculateLiveOEE(tiempoTranscurridoTurno);
        });
        mainTimer.start();
    }

    private void resetShift() {
        startTime = System.currentTimeMillis();
        tiempoTotalParadaMillis = 0;
        tiempoNetoOperacionMillis = 0;
        logsProducidosSimulados = 0;
        currentShift = (currentShift % 3) + 1;
    }

    private void updateUI(long elapsed) {
        long sec = (elapsed / 1000) % 60;
        long min = (elapsed / (1000 * 60)) % 60;
        long hr = (elapsed / (1000 * 60 * 60));
        lblTimer.setText(String.format("%02d:%02d:%02d", hr, min, sec));
        lblLogsTotales.setText(String.format("LOGS PRODUCIDOS: %d", (int)logsProducidosSimulados));
    }

    private void calculateLiveOEE(long tiempoTotalTurno) {
        if (tiempoTotalTurno <= 0) return;

        // 1. DISPONIBILIDAD PROMEDIO
        double disponibilidad = (double)(tiempoTotalTurno - tiempoTotalParadaMillis) / tiempoTotalTurno;

        // 2. RENDIMIENTO PROMEDIO (Performance)
        // Calculamos cuántos logs DEBERÍAN haberse hecho en el tiempo que la máquina estuvo RUNNING
        double minutosEnMarcha = tiempoNetoOperacionMillis / 60000.0;
        double logsObjetivoEsperados = minutosEnMarcha * LOGS_POR_MINUTO_OBJETIVO;
        
        double rendimiento = (logsObjetivoEsperados > 0) ? 
                Math.min(1.0, logsProducidosSimulados / logsObjetivoEsperados) : 1.0;

        // 3. CALIDAD PROMEDIO
        double logsMalos = 0;
        try { logsMalos = Double.parseDouble(txtLogsMalos.getText()); } catch (Exception e) {}
        double calidad = (logsProducidosSimulados > 0) ? 
                Math.max(0, (logsProducidosSimulados - logsMalos) / logsProducidosSimulados) : 1.0;

        double oee = disponibilidad * rendimiento * calidad;

        availabilityBar.setValue((int)(disponibilidad * 100));
        performanceBar.setValue((int)(rendimiento * 100));
        qualityBar.setValue((int)(calidad * 100));
        oeeBar.setValue((int)(oee * 100));
        
        lblPromedioVel.setText(String.format("RENDIMIENTO PROMEDIO: %.1f%%", rendimiento * 100));
        lblOeeResult.setText(String.format("OEE: %.1f%%", oee * 100));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OEE_Calculator().setVisible(true));
    }
}