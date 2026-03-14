package com.portfolio.oee;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Main GUI Application for the OEE Tracker.
 * Now supports Internationalization (English, Spanish, Portuguese).
 */

public class OEE_Calculator extends JFrame {

    /**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private JProgressBar availabilityBar, performanceBar, qualityBar, oeeBar;
    private JLabel lblOeeResult;
    private ResourceBundle messages;

    public OEE_Calculator() {
        // 1. Language Selection before building the UI
        initLanguage();

        // 2. Initialize DB and GUI
        DatabaseManager.initializeDatabase();
        setupGUI();
        refreshDashboard();
    }

    private void initLanguage() {
        String[] options = {"English", "Español", "Português"};
        int choice = JOptionPane.showOptionDialog(null,
                "Select Language / Seleccione Idioma / Selecione o Idioma",
                "Language Setup",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        Locale locale;
        switch (choice) {
        	case 1: locale = Locale.forLanguageTag("es"); break;
        	case 2: locale = Locale.forLanguageTag("pt"); break;
        	default: locale = Locale.forLanguageTag("en");
    }

        // IMPORTANT: Ensure the path matches your package structure
        messages = ResourceBundle.getBundle("com.portfolio.oee.messages", locale);
    }

    private void setupGUI() {
        setTitle(messages.getString("window_title"));
        setSize(600, 550);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: Dashboard
        tabbedPane.addTab(messages.getString("tab_dashboard"), createDashboardPanel());

        // TAB 2: Data Entry
        tabbedPane.addTab(messages.getString("tab_log"), createDataEntryPanel());

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

        lblOeeResult = new JLabel(messages.getString("lbl_result") + " 0.0%", SwingConstants.CENTER);
        lblOeeResult.setFont(new Font("Arial", Font.BOLD, 24));

        panel.add(availabilityBar);
        panel.add(performanceBar);
        panel.add(qualityBar);
        panel.add(oeeBar);
        panel.add(lblOeeResult);

        return panel;
    }

    private JProgressBar createCustomBar(String title) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setBorder(BorderFactory.createTitledBorder(title));
        return bar;
    }

    private JPanel createDataEntryPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Production Entry
        JPanel prodPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        prodPanel.setBorder(BorderFactory.createTitledBorder("Log Production"));

        JTextField txtPlannedTime = new JTextField();
        JTextField txtIdealCycle = new JTextField();
        JTextField txtTotal = new JTextField();
        JTextField txtDefects = new JTextField();

        prodPanel.add(new JLabel("Planned Run Time (mins):"));
        prodPanel.add(txtPlannedTime);
        prodPanel.add(new JLabel("Ideal Cycle Time (mins/unit):"));
        prodPanel.add(txtIdealCycle);
        prodPanel.add(new JLabel("Total Units Produced:"));
        prodPanel.add(txtTotal);
        prodPanel.add(new JLabel("Defective Units:"));
        prodPanel.add(txtDefects);

        JButton btnSaveProd = new JButton(messages.getString("btn_save_prod"));
        prodPanel.add(new JLabel(""));
        prodPanel.add(btnSaveProd);

        // Downtime Entry
        JPanel downPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        downPanel.setBorder(BorderFactory.createTitledBorder("Log Downtime"));

        JTextField txtReason = new JTextField();
        JTextField txtDuration = new JTextField();

        downPanel.add(new JLabel("Stop Reason:"));
        downPanel.add(txtReason);
        downPanel.add(new JLabel("Duration (mins):"));
        downPanel.add(txtDuration);

        JButton btnSaveDown = new JButton(messages.getString("btn_save_down"));
        downPanel.add(new JLabel(""));
        downPanel.add(btnSaveDown);

        // Listeners (Action Logic)
        btnSaveProd.addActionListener(e -> {
            try {
                DatabaseManager.insertProduction(
                    Double.parseDouble(txtPlannedTime.getText()),
                    Double.parseDouble(txtIdealCycle.getText()),
                    Integer.parseInt(txtTotal.getText()),
                    Integer.parseInt(txtDefects.getText())
                );
                JOptionPane.showMessageDialog(this, "OK!");
                refreshDashboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: Invalid Input");
            }
        });

        btnSaveDown.addActionListener(e -> {
            try {
                DatabaseManager.insertDowntime(
                    txtReason.getText(),
                    Double.parseDouble(txtDuration.getText())
                );
                JOptionPane.showMessageDialog(this, "OK!");
                refreshDashboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: Invalid Input");
            }
        });

        panel.add(prodPanel, BorderLayout.NORTH);
        panel.add(downPanel, BorderLayout.CENTER);
        return panel;
    }

    private void refreshDashboard() {
        double[] metrics = DatabaseManager.getTodayProductionMetrics();
        double plannedTime = metrics[0];
        double idealCycleTime = metrics[1];
        double totalProduced = metrics[2];
        double defects = metrics[3];
        double downtime = DatabaseManager.getTodayTotalDowntime();

        if (plannedTime == 0) {
			return;
		}

        double operatingTime = plannedTime - downtime;
        double availability = Math.max(0, operatingTime / plannedTime);

        double performance = 0;
        if (operatingTime > 0) {
            double expectedProduction = operatingTime / idealCycleTime;
            performance = Math.min(1.0, totalProduced / expectedProduction);
        }

        double quality = 0;
        if (totalProduced > 0) {
            double goodUnits = totalProduced - defects;
            quality = Math.max(0, goodUnits / totalProduced);
        }

        double oee = availability * performance * quality;

        // UI Updates
        availabilityBar.setValue((int) (availability * 100));
        performanceBar.setValue((int) (performance * 100));
        qualityBar.setValue((int) (quality * 100));
        oeeBar.setValue((int) (oee * 100));

        lblOeeResult.setText(String.format("%s %.1f%%", messages.getString("lbl_result"), oee * 100));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new OEE_Calculator().setVisible(true);
        });
    }
}