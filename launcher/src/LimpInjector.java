import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * LIMP Client Injector - Prestige-style launcher.
 * Finds .minecraft directories, lets you pick one, and injects the mod JAR.
 * The mod self-destructs from the mods folder after loading.
 *
 * Usage: java -jar LimpInjector.jar
 *        (place legitclient-1.0.0.jar next to it, or embed it)
 */
public class LimpInjector {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";
    private static final Color BG = new Color(18, 18, 24);
    private static final Color BG_LIGHT = new Color(28, 28, 38);
    private static final Color ACCENT = new Color(180, 100, 255);
    private static final Color TEXT = new Color(220, 220, 230);
    private static final Color TEXT_DIM = new Color(120, 120, 140);
    private static final Color GREEN = new Color(80, 220, 120);
    private static final Color RED = new Color(255, 80, 80);

    private JFrame frame;
    private JLabel statusLabel;
    private JLabel pathLabel;
    private JButton injectBtn;
    private JButton browseBtn;
    private Path selectedMods;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LimpInjector().createUI());
    }

    private void createUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        frame = new JFrame("LIMP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(480, 340);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(BG);

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 18));
        header.setBackground(BG);
        JLabel title = new JLabel("LIMP CLIENT");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(ACCENT);
        header.add(title);
        main.add(header, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(BG);
        center.setBorder(new EmptyBorder(10, 40, 10, 40));

        // Minecraft path
        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));
        pathPanel.setBackground(BG_LIGHT);
        pathPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 50, 65), 1, true),
                new EmptyBorder(10, 14, 10, 14)));
        pathPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        pathLabel = new JLabel("No Minecraft found");
        pathLabel.setForeground(TEXT_DIM);
        pathLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pathPanel.add(pathLabel, BorderLayout.CENTER);

        browseBtn = new JButton("Browse");
        browseBtn.setForeground(TEXT);
        browseBtn.setBackground(BG_LIGHT);
        browseBtn.setFocusPainted(false);
        browseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browseBtn.addActionListener(e -> browse());
        pathPanel.add(browseBtn, BorderLayout.EAST);

        center.add(pathPanel);
        center.add(Box.createVerticalStrut(20));

        // Inject button
        injectBtn = new JButton("INJECT");
        injectBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        injectBtn.setForeground(Color.WHITE);
        injectBtn.setBackground(ACCENT);
        injectBtn.setFocusPainted(false);
        injectBtn.setBorder(new EmptyBorder(14, 0, 14, 0));
        injectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        injectBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        injectBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        injectBtn.setEnabled(false);
        injectBtn.addActionListener(e -> inject());

        // Hover effect
        injectBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (injectBtn.isEnabled()) injectBtn.setBackground(ACCENT.brighter()); }
            public void mouseExited(MouseEvent e) { injectBtn.setBackground(ACCENT); }
        });

        center.add(injectBtn);
        center.add(Box.createVerticalStrut(16));

        // Status
        statusLabel = new JLabel("Searching for Minecraft...");
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(statusLabel);

        main.add(center, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(BG);
        JLabel ver = new JLabel("v1.0.0 | Fabric 1.21.11 | DELETE = Panic Key");
        ver.setForeground(TEXT_DIM);
        ver.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footer.add(ver);
        main.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(main);
        frame.setVisible(true);

        // Auto-detect Minecraft
        new Thread(this::autoDetect).start();
    }

    private void autoDetect() {
        List<Path> candidates = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) candidates.add(Path.of(appdata, ".minecraft", "mods"));
        } else if (os.contains("mac")) {
            candidates.add(Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft", "mods"));
        } else {
            candidates.add(Path.of(System.getProperty("user.home"), ".minecraft", "mods"));
        }

        // Check common launcher locations
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, ".minecraft", "mods"));
        candidates.add(Path.of(home, "AppData", "Roaming", ".minecraft", "mods"));
        candidates.add(Path.of(home, "curseforge", "minecraft", "Instances"));

        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                setPath(p);
                return;
            }
            // Check parent exists (mods folder might not exist yet)
            if (Files.isDirectory(p.getParent())) {
                setPath(p);
                return;
            }
        }

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Minecraft not found - use Browse to select mods folder");
            statusLabel.setForeground(RED);
        });
    }

    private void setPath(Path modsDir) {
        selectedMods = modsDir;
        SwingUtilities.invokeLater(() -> {
            pathLabel.setText(modsDir.toString());
            pathLabel.setForeground(TEXT);
            injectBtn.setEnabled(true);
            statusLabel.setText("Ready to inject");
            statusLabel.setForeground(GREEN);
        });
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select your .minecraft/mods folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            setPath(chooser.getSelectedFile().toPath());
        }
    }

    private void inject() {
        injectBtn.setEnabled(false);
        statusLabel.setText("Injecting...");
        statusLabel.setForeground(ACCENT);

        new Thread(() -> {
            try {
                // Create mods folder if it doesn't exist
                Files.createDirectories(selectedMods);

                // Find the mod JAR - check next to this JAR, or in current directory
                Path modSource = findModJar();
                if (modSource == null) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("ERROR: " + MOD_JAR + " not found next to injector!");
                        statusLabel.setForeground(RED);
                        injectBtn.setEnabled(true);
                    });
                    return;
                }

                // Copy to mods folder
                Path dest = selectedMods.resolve(MOD_JAR);
                Files.copy(modSource, dest, StandardCopyOption.REPLACE_EXISTING);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Injected! Start Minecraft with Fabric to load.");
                    statusLabel.setForeground(GREEN);
                    injectBtn.setText("INJECTED ✓");
                    injectBtn.setBackground(GREEN);
                });

                // Auto-close after 3 seconds
                Thread.sleep(3000);
                System.exit(0);

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(RED);
                    injectBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private Path findModJar() {
        // Check next to the injector JAR
        try {
            Path self = Path.of(LimpInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = self.getParent();
            if (dir != null) {
                Path candidate = dir.resolve(MOD_JAR);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        // Check current directory
        Path cwd = Path.of(MOD_JAR);
        if (Files.exists(cwd)) return cwd;

        // Check common download locations
        String home = System.getProperty("user.home");
        for (String sub : new String[]{"Downloads", "Desktop", "Belgeler", "İndirilenler"}) {
            Path p = Path.of(home, sub, MOD_JAR);
            if (Files.exists(p)) return p;
        }

        return null;
    }
}
