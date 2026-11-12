import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.List;

public class LimpInjector {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";
    private static final String VERSION = "1.0.0";

    private static final Color BG_DARK = new Color(12, 10, 20);
    private static final Color BG_MAIN = new Color(18, 16, 28);
    private static final Color BG_CARD = new Color(24, 22, 38);
    private static final Color BG_ITEM = new Color(30, 28, 46);
    private static final Color BG_TOGGLE_OFF = new Color(50, 48, 66);
    private static final Color ACCENT = new Color(130, 80, 230);
    private static final Color ACCENT_LIGHT = new Color(160, 120, 255);
    private static final Color ACCENT_GLOW = new Color(130, 80, 230, 40);
    private static final Color GREEN = new Color(60, 200, 100);
    private static final Color GREEN_DIM = new Color(40, 160, 80);
    private static final Color TEXT_WHITE = new Color(230, 230, 240);
    private static final Color TEXT_GRAY = new Color(160, 158, 180);
    private static final Color TEXT_DIM = new Color(100, 98, 120);
    private static final Color BORDER = new Color(45, 42, 65);
    private static final Color RED = new Color(230, 60, 60);

    private JFrame frame;
    private String activeTab = "Client";
    private JPanel tabContentPanel;
    private JButton clientTabBtn, injectionTabBtn;

    private boolean betaChannel = true;
    private boolean zeroTrace = true;
    private boolean filelessInjection = false;

    // Auto-detected Minecraft path
    private volatile Path detectedModsDir;
    private volatile String detectedName = "Searching...";
    private JLabel mcStatusLabel;
    private JLabel mcPathLabel;

    // Injection status log
    private final List<String> injectionLog = new ArrayList<>();
    private void log(String msg) { injectionLog.add(msg); System.out.println("[LIMP] " + msg); }

    public static void main(String[] args) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        new LimpInjector().createUI();
                    } catch (Throwable t) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(t.toString());
                        for (StackTraceElement e : t.getStackTrace()) {
                            sb.append("\n  at ").append(e.toString());
                            if (sb.length() > 800) break;
                        }
                        JOptionPane.showMessageDialog(null, sb.toString(), "LIMP Client - Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                }
            });
        } catch (Throwable t) {
            StringBuilder sb = new StringBuilder();
            sb.append(t.toString());
            Throwable cause = t.getCause();
            if (cause != null) {
                sb.append("\nCaused by: ").append(cause.toString());
                for (StackTraceElement e : cause.getStackTrace()) {
                    sb.append("\n  at ").append(e.toString());
                    if (sb.length() > 800) break;
                }
            }
            JOptionPane.showMessageDialog(null, sb.toString(), "LIMP Client - Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void createUI() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}

        frame = new JFrame("LIMP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(660, 520);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setUndecorated(true);
        frame.setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        root.add(createTitleBar(), BorderLayout.NORTH);
        root.add(createLaunchPage(), BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);

        new Thread(new Runnable() {
            public void run() {
                detectMinecraft();
            }
        }).start();
    }

    // ── Title bar ──
    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_DARK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(BORDER);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(0, 42));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        left.setOpaque(false);

        JLabel icon = new JLabel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillOval(0, 2, 24, 24);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                g2.drawString("L", 7, 19);
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(24, 28));
        left.add(icon);

        JLabel title = new JLabel("LIMP");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(TEXT_WHITE);
        left.add(title);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        nav.setOpaque(false);
        JButton launchNav = navButton("Launch", true);
        JButton changelogNav = navButton("Changelog", false);
        JButton accountNav = navButton("Account", false);
        nav.add(launchNav);
        nav.add(dot());
        nav.add(changelogNav);
        nav.add(dot());
        nav.add(accountNav);
        left.add(Box.createHorizontalStrut(20));
        left.add(nav);

        bar.add(left, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        btns.setOpaque(false);
        btns.add(windowButton("–", false));
        btns.add(windowButton("×", true));
        bar.add(btns, BorderLayout.EAST);

        final int[] drag = {0, 0};
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { drag[0] = e.getX(); drag[1] = e.getY(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                frame.setLocation(e.getXOnScreen() - drag[0], e.getYOnScreen() - drag[1]);
            }
        });
        return bar;
    }

    private JButton windowButton(String text, boolean isClose) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 16));
        b.setForeground(TEXT_DIM);
        b.setPreferredSize(new Dimension(30, 30));
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> { if (isClose) System.exit(0); else frame.setState(JFrame.ICONIFIED); });
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(isClose ? RED : TEXT_WHITE); }
            public void mouseExited(MouseEvent e) { b.setForeground(TEXT_DIM); }
        });
        return b;
    }

    private JLabel dot() {
        JLabel d = new JLabel(" · ");
        d.setForeground(TEXT_DIM);
        d.setFont(new Font("SansSerif", Font.BOLD, 12));
        return d;
    }

    private JButton navButton(String text, boolean active) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(active ? ACCENT_LIGHT : TEXT_DIM);
        b.setBackground(active ? ACCENT : null);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (active) {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT, 1, true), new EmptyBorder(4, 10, 4, 10)));
        } else {
            b.setBorder(new EmptyBorder(5, 11, 5, 11));
        }
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(TEXT_WHITE); }
            public void mouseExited(MouseEvent e) { b.setForeground(active ? ACCENT_LIGHT : TEXT_DIM); }
        });
        return b;
    }

    // ── Launch page ──
    private JPanel createLaunchPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_DARK);

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        tabBar.setBackground(BG_DARK);

        clientTabBtn = tabButton("Client", true);
        injectionTabBtn = tabButton("Injection", false);
        clientTabBtn.addActionListener(e -> switchTab("Client"));
        injectionTabBtn.addActionListener(e -> switchTab("Injection"));

        JPanel tabGroup = new JPanel(new GridLayout(1, 2, 0, 0));
        tabGroup.setPreferredSize(new Dimension(240, 36));
        tabGroup.setBackground(BG_CARD);
        tabGroup.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        tabGroup.add(clientTabBtn);
        tabGroup.add(injectionTabBtn);
        tabBar.add(tabGroup);
        page.add(tabBar, BorderLayout.NORTH);

        tabContentPanel = new JPanel(new BorderLayout());
        tabContentPanel.setBackground(BG_DARK);
        tabContentPanel.setBorder(new EmptyBorder(0, 30, 20, 30));
        page.add(tabContentPanel, BorderLayout.CENTER);
        switchTab("Client");
        return page;
    }

    private JButton tabButton(String text, boolean active) {
        JButton b = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground().equals(ACCENT) ? ACCENT : BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(active ? TEXT_WHITE : TEXT_DIM);
        b.setBackground(active ? ACCENT : BG_CARD);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void switchTab(String tab) {
        activeTab = tab;
        clientTabBtn.setBackground(tab.equals("Client") ? ACCENT : BG_CARD);
        clientTabBtn.setForeground(tab.equals("Client") ? TEXT_WHITE : TEXT_DIM);
        injectionTabBtn.setBackground(tab.equals("Injection") ? ACCENT : BG_CARD);
        injectionTabBtn.setForeground(tab.equals("Injection") ? TEXT_WHITE : TEXT_DIM);
        clientTabBtn.repaint();
        injectionTabBtn.repaint();

        tabContentPanel.removeAll();
        tabContentPanel.add(buildTabContent(tab), BorderLayout.CENTER);
        tabContentPanel.revalidate();
        tabContentPanel.repaint();
    }

    private JPanel buildTabContent(String tab) {
        JPanel content = new JPanel(new BorderLayout(20, 0));
        content.setBackground(BG_DARK);

        // Left card
        JPanel card = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));
        card.setPreferredSize(new Dimension(230, 0));

        // Protection badge
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badge.setOpaque(false);
        badge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel bdot = new JLabel("  ");
        bdot.setForeground(GREEN);
        bdot.setFont(new Font("SansSerif", Font.PLAIN, 10));
        badge.add(bdot);
        JLabel badgeText = new JLabel("Protection status active");
        badgeText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        badgeText.setForeground(GREEN);
        badge.add(badgeText);
        card.add(badge);
        card.add(Box.createVerticalStrut(16));

        JLabel name1 = new JLabel("LIMP");
        name1.setFont(new Font("SansSerif", Font.BOLD, 28));
        name1.setForeground(TEXT_WHITE);
        name1.setAlignmentX(0);
        card.add(name1);

        JLabel name2 = new JLabel(tab);
        name2.setFont(new Font("SansSerif", Font.ITALIC | Font.BOLD, 24));
        name2.setForeground(ACCENT_LIGHT);
        name2.setAlignmentX(0);
        card.add(name2);
        card.add(Box.createVerticalStrut(10));

        JPanel verBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        verBadge.setOpaque(false);
        verBadge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel sup = new JLabel("SUPPORTED");
        sup.setFont(new Font("SansSerif", Font.BOLD, 9));
        sup.setForeground(TEXT_DIM);
        sup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TEXT_DIM, 1, true), new EmptyBorder(2, 6, 2, 6)));
        verBadge.add(sup);
        JLabel verRange = new JLabel("1.21 – 1.21.11");
        verRange.setFont(new Font("SansSerif", Font.PLAIN, 11));
        verRange.setForeground(TEXT_DIM);
        verBadge.add(verRange);
        card.add(verBadge);
        card.add(Box.createVerticalStrut(14));

        String desc = tab.equals("Client")
                ? "A focused competitive client built for consistent cross-version play, anti-cheat compatibility, and a curated module set."
                : "The complete LIMP experience with self-destruct, full customisation, and every protection capability enabled.";
        JLabel descLbl = new JLabel("<html><body style='width:170px'>" + desc + "</body></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLbl.setForeground(TEXT_DIM);
        descLbl.setAlignmentX(0);
        card.add(descLbl);
        card.add(Box.createVerticalGlue());

        // Minecraft status
        mcStatusLabel = new JLabel(detectedModsDir != null ? "  Minecraft detected" : "  " + detectedName);
        mcStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        mcStatusLabel.setForeground(detectedModsDir != null ? GREEN : TEXT_DIM);
        mcStatusLabel.setAlignmentX(0);
        card.add(mcStatusLabel);
        card.add(Box.createVerticalStrut(3));

        JPanel pathRow = new JPanel(new BorderLayout(4, 0));
        pathRow.setOpaque(false);
        pathRow.setAlignmentX(0);
        mcPathLabel = new JLabel(detectedModsDir != null ? detectedModsDir.getParent().toString() : "");
        mcPathLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        mcPathLabel.setForeground(TEXT_DIM);
        pathRow.add(mcPathLabel, BorderLayout.CENTER);
        JLabel changeBtn = new JLabel("[Change]");
        changeBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        changeBtn.setForeground(ACCENT_LIGHT);
        changeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        changeBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select Modrinth profile or .minecraft folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (detectedModsDir != null) chooser.setCurrentDirectory(detectedModsDir.getParent().toFile());
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    Path mc = chooser.getSelectedFile().toPath();
                    detectedModsDir = mc.getFileName().toString().equals("mods") ? mc : mc.resolve("mods");
                    detectedName = mc.getFileName().toString() + " (manual)";
                    updateStatusUI();
                }
            }
        });
        pathRow.add(changeBtn, BorderLayout.EAST);
        card.add(pathRow);
        card.add(Box.createVerticalStrut(10));

        // Inject button - directly injects!
        JButton inject = new JButton("Inject LIMP " + tab + "  →") {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT_LIGHT : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        inject.setFont(new Font("SansSerif", Font.BOLD, 13));
        inject.setForeground(Color.WHITE);
        inject.setOpaque(false);
        inject.setContentAreaFilled(false);
        inject.setBorderPainted(false);
        inject.setFocusPainted(false);
        inject.setBorder(new EmptyBorder(12, 0, 12, 0));
        inject.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        inject.setAlignmentX(0);
        inject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        inject.addActionListener(e -> doQuickInject(inject));
        card.add(inject);

        content.add(card, BorderLayout.WEST);

        // Right side: capabilities + toggles
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JPanel capHeader = new JPanel(new BorderLayout());
        capHeader.setOpaque(false);
        capHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel capTitle = new JLabel("INCLUDED CAPABILITIES");
        capTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        capTitle.setForeground(TEXT_DIM);
        capHeader.add(capTitle, BorderLayout.WEST);
        JLabel buildLbl = new JLabel("BUILD " + VERSION);
        buildLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        buildLbl.setForeground(TEXT_DIM);
        capHeader.add(buildLbl, BorderLayout.EAST);
        right.add(capHeader);
        right.add(Box.createVerticalStrut(8));

        String[] included;
        String[] notIncluded;
        if (tab.equals("Client")) {
            included = new String[]{"Competitive-ready modules", "Advanced anti-cheat compatibility", "Cloud configuration sync"};
            notIncluded = new String[]{"Manual-map injection", "Reduced string exposure", "Screen-share protection"};
        } else {
            included = new String[]{"Manual-map injection", "Competitive-ready modules", "Advanced anti-cheat compatibility",
                    "Reduced string exposure", "Screen-share protection", "Cloud configuration sync"};
            notIncluded = new String[]{};
        }

        for (String cap : included) {
            right.add(capabilityRow(cap, true));
            right.add(Box.createVerticalStrut(4));
        }

        if (notIncluded.length > 0) {
            right.add(Box.createVerticalStrut(8));
            JLabel notLabel = new JLabel("NOT INCLUDED IN THIS EDITION");
            notLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
            notLabel.setForeground(TEXT_DIM);
            notLabel.setAlignmentX(0);
            right.add(notLabel);
            right.add(Box.createVerticalStrut(8));
            for (String cap : notIncluded) {
                right.add(capabilityRow(cap, false));
                right.add(Box.createVerticalStrut(4));
            }
        }

        right.add(Box.createVerticalStrut(12));
        right.add(createToggle("Beta release channel", "Newest features; may be less stable.", betaChannel, new java.util.function.Consumer<Boolean>() { public void accept(Boolean v) { betaChannel = v; } }));
        right.add(Box.createVerticalStrut(6));
        if (tab.equals("Client")) {
            right.add(createToggle("Fileless injection (TEST)", "Legacy temp-file path (StandardInjection).", filelessInjection, new java.util.function.Consumer<Boolean>() { public void accept(Boolean v) { filelessInjection = v; } }));
        } else {
            right.add(createToggle("Zero-trace cleanup", "When you unload LIMP, wipes its last bytes from the game.", zeroTrace, new java.util.function.Consumer<Boolean>() { public void accept(Boolean v) { zeroTrace = v; } }));
        }
        right.add(Box.createVerticalGlue());

        content.add(right, BorderLayout.CENTER);
        return content;
    }

    private JPanel capabilityRow(String text, boolean included) {
        JPanel row = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_ITEM);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel ic = new JLabel(included ? "✓" : "–");
        ic.setFont(new Font("SansSerif", Font.BOLD, 13));
        ic.setForeground(included ? GREEN : TEXT_DIM);
        ic.setPreferredSize(new Dimension(20, 20));
        row.add(ic, BorderLayout.WEST);

        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(included ? TEXT_WHITE : TEXT_DIM);
        label.setBorder(new EmptyBorder(0, 8, 0, 0));
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private JPanel createToggle(String title, String desc, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        JPanel panel = new JPanel(new BorderLayout(12, 0)) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_ITEM);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        t.setForeground(TEXT_WHITE);
        labels.add(t);
        JLabel d = new JLabel(desc);
        d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        d.setForeground(TEXT_DIM);
        labels.add(d);
        panel.add(labels, BorderLayout.CENTER);

        final boolean[] on = {initial};
        JPanel toggle = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(on[0] ? ACCENT : BG_TOGGLE_OFF);
                g2.fillRoundRect(0, 4, 44, 22, 22, 22);
                g2.setColor(Color.WHITE);
                int cx = on[0] ? 30 : 14;
                g2.fillOval(cx - 8, 7, 16, 16);
                g2.dispose();
            }
        };
        toggle.setOpaque(false);
        toggle.setPreferredSize(new Dimension(44, 28));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                on[0] = !on[0];
                onChange.accept(on[0]);
                toggle.repaint();
            }
        });
        panel.add(toggle, BorderLayout.EAST);
        return panel;
    }

    // ── Inject via Java Attach API (like Vape V4 / Prestige Client) ──
    // Game stays running. Agent loads into the running JVM.
    private void doQuickInject(JButton btn) {
        if (detectedModsDir == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select your .minecraft or Modrinth profile folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path mc = chooser.getSelectedFile().toPath();
                detectedModsDir = mc.getFileName().toString().equals("mods") ? mc : mc.resolve("mods");
            } else {
                return;
            }
        }

        btn.setEnabled(false);
        btn.setText("Injecting...");

        new Thread(new Runnable() {
            public void run() {
                try {
                    injectionLog.clear();
                    log("Starting injection...");

                    Path modSource = findModJar();
                    if (modSource == null) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                btn.setText("Error");
                                btn.setEnabled(true);
                                JOptionPane.showMessageDialog(frame,
                                        MOD_JAR + " not found!\nPlace it next to LimpClient.jar",
                                        "LIMP Client", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                        return;
                    }
                    log("Mod JAR found: " + modSource);

                    // Copy mod to mods folder for next restart
                    Files.createDirectories(detectedModsDir);
                    Path dest = detectedModsDir.resolve(MOD_JAR);
                    Files.copy(modSource, dest, StandardCopyOption.REPLACE_EXISTING);
                    log("Mod copied to: " + detectedModsDir);

                    // Find running Minecraft JVM and inject via Attach API
                    log("Searching for Minecraft process...");
                    String mcPid = findMinecraftPid();
                    final boolean liveInjected;

                    if (mcPid != null) {
                        log("Minecraft found! PID: " + mcPid);
                        log("Attempting Attach API injection...");
                        liveInjected = tryAttachInject(mcPid, modSource.toAbsolutePath().toString());
                        if (liveInjected) {
                            log("LIVE INJECTION SUCCESSFUL!");
                        } else {
                            log("Attach API injection failed (see above)");
                        }
                    } else {
                        log("Minecraft process NOT FOUND (is it running?)");
                        liveInjected = false;
                    }

                    final boolean mcWasRunning = (mcPid != null);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            btn.setText(liveInjected ? "Injected!" : "Installed!");
                            btn.setForeground(GREEN);
                            showSuccessOverlay(mcWasRunning, liveInjected);
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            btn.setText("Error");
                            btn.setEnabled(true);
                            JOptionPane.showMessageDialog(frame, "Injection failed: " + ex.getMessage(),
                                    "LIMP Client", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }).start();
    }

    // ── Find Minecraft's JVM PID ──
    private String findMinecraftPid() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return findMinecraftPidWindows();
        return findMinecraftPidUnix();
    }

    private String findMinecraftPidWindows() {
        String selfPid = "";
        try {
            selfPid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        } catch (Exception ignored) {}
        log("  Self PID: " + selfPid);

        // Method 1: PowerShell (works on Windows 10/11, wmic is deprecated)
        String pid = powershellFind(selfPid);
        if (pid != null) return pid;

        // Method 2: tasklist
        pid = tasklistFind(selfPid);
        if (pid != null) return pid;

        // Method 3: wmic fallback (deprecated but may work on older Windows)
        pid = wmicFind("javaw.exe", selfPid);
        if (pid != null) return pid;
        pid = wmicFind("java.exe", selfPid);
        if (pid != null) return pid;

        // Method 4: jps (comes with JDK)
        pid = jpsFind(selfPid);
        if (pid != null) return pid;

        log("  All PID detection methods failed — is Minecraft running?");
        return null;
    }

    private String powershellFind(String selfPid) {
        try {
            log("  powershell: searching...");
            Process p = Runtime.getRuntime().exec(new String[]{
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance Win32_Process -Filter \"name='javaw.exe' OR name='java.exe'\" | Select-Object ProcessId,CommandLine | Format-List"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            String currentPid = null;
            String currentCmd = null;
            List<String> allPids = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("ProcessId")) {
                    currentPid = line.split(":", 2)[1].trim();
                } else if (line.startsWith("CommandLine")) {
                    currentCmd = line.split(":", 2).length > 1 ? line.split(":", 2)[1].trim() : "";
                }
                if (currentPid != null && currentCmd != null) {
                    if (!currentPid.equals(selfPid) && currentPid.matches("\\d+")) {
                        String lower = currentCmd.toLowerCase();
                        if (lower.contains("minecraft") || lower.contains("--gamedir") || lower.contains("lwjgl")
                                || lower.contains("fabric") || lower.contains("net.fabricmc")) {
                            log("  powershell: Minecraft PID " + currentPid);
                            reader.close();
                            p.waitFor();
                            return currentPid;
                        }
                        allPids.add(currentPid);
                    }
                    currentPid = null;
                    currentCmd = null;
                }
            }
            reader.close();
            p.waitFor();

            log("  powershell: " + allPids.size() + " java process(es), none matched minecraft keywords");
            // If only one java process and it's not us, probably Minecraft
            if (allPids.size() == 1) {
                log("  powershell: only 1 java process, using PID " + allPids.get(0));
                return allPids.get(0);
            }
        } catch (Exception e) {
            log("  powershell failed: " + e.getMessage());
        }
        return null;
    }

    private String wmicFind(String procName, String selfPid) {
        try {
            log("  wmic: searching " + procName + "...");
            Process p = Runtime.getRuntime().exec(new String[]{
                "cmd", "/c", "wmic process where \"name='" + procName + "'\" get ProcessId,CommandLine /FORMAT:CSV"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if ((lower.contains("net.minecraft") || lower.contains("--gamedir") || lower.contains("lwjgl")
                        || lower.contains("minecraft") || lower.contains("fabric"))
                        && !lower.contains("limpinjector") && !lower.contains("limpclient")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String pid = parts[parts.length - 1].trim();
                        if (!pid.isEmpty() && !pid.equals(selfPid) && pid.matches("\\d+")) {
                            log("  wmic: found PID " + pid);
                            reader.close();
                            p.waitFor();
                            return pid;
                        }
                    }
                }
            }
            reader.close();
            p.waitFor();
            log("  wmic: no " + procName + " with minecraft found");
        } catch (Exception e) {
            log("  wmic failed: " + e.getMessage());
        }
        return null;
    }

    private String tasklistFind(String selfPid) {
        try {
            log("  tasklist: searching...");
            // Get all java processes
            Process p = Runtime.getRuntime().exec(new String[]{
                "cmd", "/c", "tasklist /FI \"IMAGENAME eq javaw.exe\" /FO CSV /NH"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            List<String> javaPids = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                // Format: "javaw.exe","12345","Console","1","123,456 K"
                if (line.contains("javaw.exe")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String pid = parts[1].replace("\"", "").trim();
                        if (pid.matches("\\d+") && !pid.equals(selfPid)) {
                            javaPids.add(pid);
                        }
                    }
                }
            }
            reader.close();
            p.waitFor();

            // Also check java.exe
            p = Runtime.getRuntime().exec(new String[]{
                "cmd", "/c", "tasklist /FI \"IMAGENAME eq java.exe\" /FO CSV /NH"
            });
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = reader.readLine()) != null) {
                if (line.contains("java.exe")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String pid = parts[1].replace("\"", "").trim();
                        if (pid.matches("\\d+") && !pid.equals(selfPid)) {
                            javaPids.add(pid);
                        }
                    }
                }
            }
            reader.close();
            p.waitFor();

            log("  tasklist: found " + javaPids.size() + " java process(es)");

            if (javaPids.size() == 1) {
                // Only one java process besides us — likely Minecraft
                log("  tasklist: using PID " + javaPids.get(0));
                return javaPids.get(0);
            } else if (javaPids.size() > 1) {
                // Multiple java processes — check each with wmic for MC indicators
                for (String pid : javaPids) {
                    try {
                        Process wp = Runtime.getRuntime().exec(new String[]{
                            "cmd", "/c", "wmic process where \"ProcessId=" + pid + "\" get CommandLine /FORMAT:CSV"
                        });
                        BufferedReader wr = new BufferedReader(new InputStreamReader(wp.getInputStream()));
                        String wl;
                        while ((wl = wr.readLine()) != null) {
                            String lower = wl.toLowerCase();
                            if (lower.contains("minecraft") || lower.contains("--gamedir") || lower.contains("lwjgl") || lower.contains("fabric")) {
                                log("  tasklist: PID " + pid + " is Minecraft");
                                wr.close();
                                wp.waitFor();
                                return pid;
                            }
                        }
                        wr.close();
                        wp.waitFor();
                    } catch (Exception ignored) {}
                }
                // If we can't determine which is MC, use the first non-self one
                log("  tasklist: can't determine MC, using first PID " + javaPids.get(0));
                return javaPids.get(0);
            }
        } catch (Exception e) {
            log("  tasklist failed: " + e.getMessage());
        }
        return null;
    }

    private String jpsFind(String selfPid) {
        try {
            log("  jps: searching...");
            Process p = Runtime.getRuntime().exec(new String[]{"jps", "-l"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("minecraft") || lower.contains("net.fabricmc") || lower.contains("cpw.mods")) {
                    String pid = line.trim().split("\\s+")[0];
                    if (pid.matches("\\d+") && !pid.equals(selfPid)) {
                        log("  jps: found PID " + pid);
                        reader.close();
                        p.waitFor();
                        return pid;
                    }
                }
            }
            reader.close();
            p.waitFor();
        } catch (Exception e) {
            log("  jps: not available");
        }
        return null;
    }

    private String findMinecraftPidUnix() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "sh", "-c", "ps aux | grep -i minecraft | grep java | grep -v grep | head -1"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) return parts[1];
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── Java Attach API injection (DLL-style, game stays running) ──
    private boolean tryAttachInject(String pid, String modSourcePath) {
        String agentJar = null;
        try {
            agentJar = new java.io.File(
                LimpInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getAbsolutePath();
        } catch (Exception e) {
            return false;
        }
        String agentArgs = detectedModsDir.toAbsolutePath().toString() + "|" + modSourcePath;

        // Try 1: Direct Attach API (if launcher runs on JDK)
        if (tryDirectAttach(pid, agentJar, agentArgs)) return true;

        // Try 2: Find a JDK on the system and use its tools.jar / attach module
        if (tryJdkAttach(pid, agentJar, agentArgs)) return true;

        // Try 3: Use jcmd if available
        if (tryJcmd(pid, agentJar)) return true;

        return false;
    }

    private boolean tryDirectAttach(String pid, String agentJar, String agentArgs) {
        log("  Try 1: Direct Attach API...");
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            log("  VirtualMachine class found!");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            try {
                vmClass.getMethod("loadAgent", String.class, String.class).invoke(vm, agentJar, agentArgs);
                log("  Direct attach: SUCCESS!");
                return true;
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }
        } catch (ClassNotFoundException e) {
            log("  Direct attach: VirtualMachine class not available (JRE, not JDK)");
            return false;
        } catch (Exception e) {
            log("  Direct attach FAILED: " + e.getMessage());
            return false;
        }
    }

    private boolean tryJdkAttach(String pid, String agentJar, String agentArgs) {
        log("  Try 2: Searching for JDK on system...");
        List<Path> searchPaths = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            // Minecraft's bundled Java (HIGHEST PRIORITY — always has attach API)
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                searchPaths.add(Paths.get(appdata, ".minecraft", "runtime", "java-runtime-delta"));
                searchPaths.add(Paths.get(appdata, ".minecraft", "runtime", "java-runtime-gamma"));
                searchPaths.add(Paths.get(appdata, ".minecraft", "runtime", "java-runtime-beta"));
                searchPaths.add(Paths.get(appdata, ".minecraft", "runtime", "java-runtime-alpha"));
            }
            // Modrinth's bundled Java
            if (appdata != null) {
                searchPaths.add(Paths.get(appdata, "com.modrinth.theseus", "meta", "java_versions"));
            }
            String localApp = System.getenv("LOCALAPPDATA");
            // Modrinth alternate location
            if (localApp != null) {
                searchPaths.add(Paths.get(localApp, "com.modrinth.theseus", "meta", "java_versions"));
            }
            // Standard JDK locations
            searchPaths.add(Paths.get("C:\\Program Files\\Java"));
            searchPaths.add(Paths.get("C:\\Program Files\\Eclipse Adoptium"));
            searchPaths.add(Paths.get("C:\\Program Files (x86)\\Java"));
            searchPaths.add(Paths.get(System.getProperty("user.home"), ".jdks"));
            if (localApp != null) {
                searchPaths.add(Paths.get(localApp, "Programs", "Eclipse Adoptium"));
            }
            // JAVA_HOME
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                searchPaths.add(Paths.get(javaHome));
            }
        } else {
            searchPaths.add(Paths.get("/usr/lib/jvm"));
            searchPaths.add(Paths.get(System.getProperty("user.home"), ".sdkman", "candidates", "java"));
        }

        for (Path searchDir : searchPaths) {
            if (!Files.isDirectory(searchDir)) continue;
            try {
                // Walk up to 3 levels deep to find java executables
                java.util.stream.Stream<Path> stream = Files.walk(searchDir, 3);
                try {
                    Iterator<Path> it = stream.iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        String name = p.getFileName().toString();
                        boolean isJava = os.contains("win")
                                ? name.equals("java.exe") && p.getParent().getFileName().toString().equals("bin")
                                : name.equals("java") && p.getParent().getFileName().toString().equals("bin");
                        if (isJava && Files.isExecutable(p)) {
                            if (tryExternalAttach(p.toString(), pid, agentJar, agentArgs)) {
                                return true;
                            }
                        }
                    }
                } finally {
                    stream.close();
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    private boolean tryExternalAttach(String javaExe, String pid, String agentJar, String agentArgs) {
        try {
            log("    Trying Java: " + javaExe);
            Path tmpDir = Files.createTempDirectory("limp-attach-");
            Path srcFile = tmpDir.resolve("LimpAttach.java");
            String code = "import com.sun.tools.attach.VirtualMachine;\n"
                    + "public class LimpAttach {\n"
                    + "  public static void main(String[] a) throws Exception {\n"
                    + "    VirtualMachine vm = VirtualMachine.attach(a[0]);\n"
                    + "    vm.loadAgent(a[1], a[2]);\n"
                    + "    vm.detach();\n"
                    + "  }\n"
                    + "}\n";
            Files.write(srcFile, code.getBytes());

            // Derive javac path from java path
            String javacExe = javaExe.replace("java.exe", "javac.exe").replace("/java", "/javac");
            if (javaExe.endsWith("/java")) javacExe = javaExe + "c";

            Process compile = Runtime.getRuntime().exec(new String[]{javacExe, srcFile.toString()});
            boolean compiled = compile.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!compiled || compile.exitValue() != 0) {
                log("    javac not found or compile failed");
                // Try jcmd from this Java's bin directory
                Path binDir = Paths.get(javaExe).getParent();
                if (binDir != null) {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    Path jcmdPath = binDir.resolve(os.contains("win") ? "jcmd.exe" : "jcmd");
                    if (Files.exists(jcmdPath)) {
                        log("    Found jcmd at: " + jcmdPath);
                        Process jcmdProc = Runtime.getRuntime().exec(new String[]{
                            jcmdPath.toString(), pid, "JVMTI.agent_load", agentJar, agentArgs
                        });
                        boolean jcmdDone = jcmdProc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                        if (jcmdDone && jcmdProc.exitValue() == 0) {
                            log("    jcmd agent_load: SUCCESS!");
                            deleteDir(tmpDir);
                            return true;
                        }
                        log("    jcmd failed (exit=" + (jcmdDone ? jcmdProc.exitValue() : "timeout") + ")");
                    }
                }
                deleteDir(tmpDir);
                return false;
            }

            log("    Compiled attach helper, running...");
            Process run = Runtime.getRuntime().exec(new String[]{
                javaExe, "-cp", tmpDir.toString(), "LimpAttach", pid, agentJar, agentArgs
            });
            boolean finished = run.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            int exit = finished ? run.exitValue() : -1;
            if (finished && exit == 0) {
                log("    External attach: SUCCESS!");
            } else {
                BufferedReader err = new BufferedReader(new InputStreamReader(run.getErrorStream()));
                String errLine = err.readLine();
                log("    External attach FAILED (exit=" + exit + ")" + (errLine != null ? ": " + errLine : ""));
            }
            deleteDir(tmpDir);
            return finished && exit == 0;
        } catch (Exception e) {
            log("    External attach error: " + e.getMessage());
            return false;
        }
    }

    private boolean tryJcmd(String pid, String agentJar) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] jcmdPaths;
        if (os.contains("win")) {
            jcmdPaths = new String[]{"jcmd.exe", "jcmd"};
        } else {
            jcmdPaths = new String[]{"jcmd", "/usr/bin/jcmd"};
        }
        for (String jcmd : jcmdPaths) {
            try {
                log("    Trying: " + jcmd);
                Process p = Runtime.getRuntime().exec(new String[]{jcmd, pid, "JVMTI.agent_load", agentJar});
                boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    log("    jcmd: SUCCESS!");
                    return true;
                }
            } catch (Exception ignored) {}
        }
        log("  Try 3: jcmd not available");
        return false;
    }

    private void deleteDir(Path dir) {
        try {
            java.util.stream.Stream<Path> stream = Files.walk(dir);
            try {
                List<Path> paths = new ArrayList<>();
                Iterator<Path> it = stream.iterator();
                while (it.hasNext()) paths.add(it.next());
                Collections.reverse(paths);
                for (Path p : paths) {
                    try { Files.delete(p); } catch (Exception ignored) {}
                }
            } finally {
                stream.close();
            }
        } catch (Exception ignored) {}
    }

    private void showSuccessOverlay(boolean mcWasRunning, boolean liveInjected) {
        JPanel overlay = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_DARK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                Color glow = liveInjected ? GREEN : ACCENT;
                g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 20));
                g2.fillOval(getWidth() / 2 - 100, 40, 200, 200);
                g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 10));
                g2.fillOval(getWidth() / 2 - 140, 20, 280, 260);
                g2.dispose();
            }
        };
        overlay.setLayout(new BoxLayout(overlay, BoxLayout.Y_AXIS));
        overlay.setBorder(new EmptyBorder(60, 40, 40, 40));

        JLabel check = new JLabel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(liveInjected ? GREEN : ACCENT);
                g2.fillOval(0, 0, 64, 64);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                g2.drawString(liveInjected ? "⚡" : "✓", liveInjected ? 14 : 16, 48);
                g2.dispose();
            }
        };
        check.setPreferredSize(new Dimension(64, 64));
        check.setMaximumSize(new Dimension(64, 64));
        check.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(check);
        overlay.add(Box.createVerticalStrut(24));

        String titleText = liveInjected ? "LIMP Client Injected Live!" : "LIMP Client Installed";
        JLabel suc1 = new JLabel(titleText);
        suc1.setFont(new Font("SansSerif", Font.BOLD, 24));
        suc1.setForeground(TEXT_WHITE);
        suc1.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(suc1);
        overlay.add(Box.createVerticalStrut(8));

        String msg2 = liveInjected
                ? "Agent injected into running Minecraft — check in-game chat!"
                : mcWasRunning
                    ? "Mod installed. Restart Minecraft for full features."
                    : "Mod installed. Start Minecraft to load the client.";
        JLabel suc2 = new JLabel(msg2);
        suc2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        suc2.setForeground(liveInjected ? GREEN : TEXT_GRAY);
        suc2.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(suc2);
        overlay.add(Box.createVerticalStrut(6));

        JLabel suc3 = new JLabel("Press RIGHT SHIFT in-game to open the menu.");
        suc3.setFont(new Font("SansSerif", Font.PLAIN, 12));
        suc3.setForeground(TEXT_DIM);
        suc3.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(suc3);
        overlay.add(Box.createVerticalStrut(6));

        JLabel suc4 = new JLabel("The mod self-destructs after loading — no traces left.");
        suc4.setFont(new Font("SansSerif", Font.PLAIN, 11));
        suc4.setForeground(TEXT_DIM);
        suc4.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(suc4);
        overlay.add(Box.createVerticalStrut(4));

        // Show where mod was copied
        if (detectedModsDir != null) {
            JLabel pathLabel = new JLabel("Mods: " + detectedModsDir.toAbsolutePath().toString());
            pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
            pathLabel.setForeground(TEXT_DIM);
            pathLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            overlay.add(pathLabel);
        }
        overlay.add(Box.createVerticalStrut(10));

        // Show injection log
        if (!injectionLog.isEmpty()) {
            JTextArea logArea = new JTextArea();
            logArea.setEditable(false);
            logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
            logArea.setBackground(new Color(10, 8, 18));
            logArea.setForeground(TEXT_DIM);
            logArea.setBorder(new EmptyBorder(8, 8, 8, 8));
            StringBuilder sb = new StringBuilder();
            for (String line : injectionLog) sb.append(line).append("\n");
            logArea.setText(sb.toString());
            JScrollPane scroll = new JScrollPane(logArea);
            scroll.setPreferredSize(new Dimension(500, 100));
            scroll.setMaximumSize(new Dimension(500, 100));
            scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
            scroll.setAlignmentX(Component.CENTER_ALIGNMENT);
            overlay.add(scroll);
        }
        overlay.add(Box.createVerticalStrut(14));

        // Close button
        JButton close = new JButton("Close Launcher") {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT_LIGHT : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        close.setFont(new Font("SansSerif", Font.BOLD, 14));
        close.setForeground(Color.WHITE);
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setPreferredSize(new Dimension(200, 44));
        close.setMaximumSize(new Dimension(200, 44));
        close.setAlignmentX(Component.CENTER_ALIGNMENT);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> System.exit(0));
        overlay.add(close);

        // Auto-close after 5 seconds
        overlay.add(Box.createVerticalStrut(16));
        JLabel autoClose = new JLabel("Auto-closing in 5 seconds...");
        autoClose.setFont(new Font("SansSerif", Font.PLAIN, 11));
        autoClose.setForeground(TEXT_DIM);
        autoClose.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlay.add(autoClose);

        frame.getContentPane().removeAll();
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        root.add(createTitleBar(), BorderLayout.NORTH);
        root.add(overlay, BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.revalidate();
        frame.repaint();

        // Countdown timer
        final javax.swing.Timer timer = new javax.swing.Timer(1000, null);
        final int[] countdown = {5};
        timer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                countdown[0]--;
                if (countdown[0] <= 0) {
                    timer.stop();
                    System.exit(0);
                } else {
                    autoClose.setText("Auto-closing in " + countdown[0] + " seconds...");
                }
            }
        });
        timer.start();
    }

    // ── Detection — aggressively finds Modrinth/launcher profiles ──
    private void detectMinecraft() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");

        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            String localAppdata = System.getenv("LOCALAPPDATA");

            // 1. MODRINTH — search aggressively for any profile with mods
            Path modrinthFound = findModrinthProfile(appdata, localAppdata, home);
            if (modrinthFound != null) return;

            // 2. Other launchers
            List<Path> launchers = new ArrayList<>();
            if (appdata != null) launchers.add(Paths.get(appdata, "PrismLauncher", "instances"));
            launchers.add(Paths.get(home, "AppData", "Roaming", "PrismLauncher", "instances"));
            launchers.add(Paths.get(home, "curseforge", "minecraft", "Instances"));
            launchers.add(Paths.get(home, "AppData", "Local", "MultiMC", "instances"));
            if (appdata != null) launchers.add(Paths.get(appdata, "ATLauncher", "instances"));
            if (appdata != null) launchers.add(Paths.get(appdata, "gdlauncher_next", "instances"));
            launchers.add(Paths.get(home, ".lunarclient", "offline", "multiver"));

            for (Path dir : launchers) {
                if (findInstanceIn(dir)) return;
            }

            // 3. Standard .minecraft (LAST)
            if (appdata != null && Files.isDirectory(Paths.get(appdata, ".minecraft"))) {
                detectedModsDir = Paths.get(appdata, ".minecraft", "mods");
                detectedName = "Minecraft (.minecraft)";
                updateStatusUI();
                return;
            }
        } else if (os.contains("mac")) {
            String lib = home + "/Library/Application Support";
            if (findModrinthProfileAt(Paths.get(lib, "com.modrinth.theseus", "profiles"))) return;
            if (Files.isDirectory(Paths.get(lib, "minecraft"))) {
                detectedModsDir = Paths.get(lib, "minecraft", "mods");
                detectedName = "Minecraft";
                updateStatusUI();
                return;
            }
        } else {
            if (findModrinthProfileAt(Paths.get(home, ".modrinth", "profiles"))) return;
            if (findInstanceIn(Paths.get(home, ".local", "share", "PrismLauncher", "instances"))) return;
            if (Files.isDirectory(Paths.get(home, ".minecraft"))) {
                detectedModsDir = Paths.get(home, ".minecraft", "mods");
                detectedName = "Minecraft";
                updateStatusUI();
                return;
            }
        }

        // Absolute last resort
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            detectedModsDir = Paths.get(appdata, ".minecraft", "mods");
            detectedName = "Minecraft (default)";
            updateStatusUI();
            return;
        }
        detectedName = "Minecraft not found";
        updateStatusUI();
    }

    private Path findModrinthProfile(String appdata, String localAppdata, String home) {
        // Try ALL possible Modrinth data locations — ModrinthApp FIRST (newer versions)
        List<Path> modrinthRoots = new ArrayList<>();
        if (appdata != null) {
            modrinthRoots.add(Paths.get(appdata, "ModrinthApp"));
            modrinthRoots.add(Paths.get(appdata, "modrinth"));
            modrinthRoots.add(Paths.get(appdata, "com.modrinth.theseus"));
        }
        if (localAppdata != null) {
            modrinthRoots.add(Paths.get(localAppdata, "ModrinthApp"));
            modrinthRoots.add(Paths.get(localAppdata, "modrinth"));
            modrinthRoots.add(Paths.get(localAppdata, "com.modrinth.theseus"));
        }
        modrinthRoots.add(Paths.get(home, "AppData", "Roaming", "ModrinthApp"));
        modrinthRoots.add(Paths.get(home, "AppData", "Local", "ModrinthApp"));

        for (Path root : modrinthRoots) {
            try {
                if (!Files.isDirectory(root)) continue;
                log("Modrinth root found: " + root);

                Path profilesDir = root.resolve("profiles");
                if (findModrinthProfileAt(profilesDir)) {
                    log("Profile found: " + detectedName + " -> " + detectedModsDir);
                    return detectedModsDir;
                }

                if (findModrinthProfileAt(root)) {
                    log("Profile found (root): " + detectedName + " -> " + detectedModsDir);
                    return detectedModsDir;
                }
            } catch (Exception e) {
                log("Modrinth root error (" + root + "): " + e.getMessage());
            }
        }
        return null;
    }

    private boolean findModrinthProfileAt(Path profilesDir) {
        if (!Files.isDirectory(profilesDir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir)) {
            Path bestProfile = null;
            int bestScore = -1;

            for (Path profile : stream) {
                if (!Files.isDirectory(profile)) continue;
                String pName = profile.getFileName().toString().toLowerCase();
                Path modsDir = profile.resolve("mods");

                int modCount = 0;
                if (Files.isDirectory(modsDir)) {
                    try (DirectoryStream<Path> mods = Files.newDirectoryStream(modsDir, "*.jar")) {
                        for (Path m : mods) modCount++;
                    } catch (IOException ignored) {}
                }

                boolean hasMcFiles = modCount > 0
                        || Files.exists(profile.resolve("options.txt"))
                        || Files.isDirectory(profile.resolve("config"));
                if (!hasMcFiles) continue;

                // Score: mod count + bonus for matching version + bonus for recent modification
                int score = modCount;
                if (pName.contains("1.21.11") || pName.contains("1.21")) score += 500;
                try {
                    long lastMod = Files.getLastModifiedTime(profile).toMillis();
                    long ageHours = (System.currentTimeMillis() - lastMod) / (3600_000);
                    if (ageHours < 24) score += 200;
                    else if (ageHours < 168) score += 100;
                } catch (IOException ignored) {}

                log("  Profile: " + profile.getFileName() + " (" + modCount + " mods, score=" + score + ")");

                if (score > bestScore) {
                    bestScore = score;
                    bestProfile = profile;
                }
            }

            if (bestProfile != null) {
                detectedModsDir = bestProfile.resolve("mods");
                detectedName = bestProfile.getFileName().toString() + " (Modrinth)";
                updateStatusUI();
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private boolean findInstanceIn(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path inst : stream) {
                if (!Files.isDirectory(inst)) continue;
                Path instMc = inst.resolve(".minecraft");
                if (Files.isDirectory(instMc)) {
                    detectedModsDir = instMc.resolve("mods");
                    detectedName = inst.getFileName().toString();
                    updateStatusUI();
                    return true;
                }
                if (Files.isDirectory(inst.resolve("mods")) || Files.exists(inst.resolve("options.txt"))) {
                    detectedModsDir = inst.resolve("mods");
                    detectedName = inst.getFileName().toString();
                    updateStatusUI();
                    return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void updateStatusUI() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (mcStatusLabel != null) {
                    if (detectedModsDir != null) {
                        mcStatusLabel.setText("  " + detectedName + " detected");
                        mcStatusLabel.setForeground(GREEN);
                        mcPathLabel.setText(detectedModsDir.getParent().toString());
                    } else {
                        mcStatusLabel.setText("  " + detectedName);
                        mcStatusLabel.setForeground(RED);
                        mcPathLabel.setText("Click inject to browse manually");
                    }
                }
            }
        });
    }

    private Path findModJar() {
        // Extract from embedded resource (bundled inside JAR)
        try {
            InputStream in = LimpInjector.class.getResourceAsStream("/" + MOD_JAR);
            if (in != null) {
                try {
                    Path tmp = Files.createTempFile("limp-mod-", ".jar");
                    tmp.toFile().deleteOnExit();
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    return tmp;
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {}

        // Check next to the injector
        try {
            URI loc = LimpInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path self = Paths.get(loc);
            Path dir = self.getParent();
            if (dir != null) {
                Path candidate = dir.resolve(MOD_JAR);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        Path cwd = Paths.get(MOD_JAR);
        if (Files.exists(cwd)) return cwd;

        String userHome = System.getProperty("user.home", "");
        for (String sub : new String[]{"Downloads", "Desktop", "Belgeler", "Indirilenler"}) {
            Path p = Paths.get(userHome, sub, MOD_JAR);
            if (Files.exists(p)) return p;
        }
        return null;
    }
}
