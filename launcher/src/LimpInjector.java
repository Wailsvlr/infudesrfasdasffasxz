import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class LimpInjector {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";
    private static final String VERSION = "1.0.0";

    // Colors matching Prestige dark theme
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
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Launch page
    private String activeTab = "Client";
    private JPanel tabContentPanel;
    private JButton clientTabBtn, injectionTabBtn;

    // Target page
    private JPanel targetListPanel;
    private List<MinecraftTarget> targets = new ArrayList<>();

    // Toggles
    private boolean betaChannel = true;
    private boolean zeroTrace = true;
    private boolean filelessInjection = false;

    static class MinecraftTarget {
        String name;
        String version;
        Path modsDir;
        boolean alreadyLoaded;
        MinecraftTarget(String name, String version, Path modsDir, boolean loaded) {
            this.name = name; this.version = version; this.modsDir = modsDir; this.alreadyLoaded = loaded;
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> new LimpInjector().createUI());
    }

    private void createUI() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}

        frame = new JFrame("LIMP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(660, 560);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setUndecorated(true);
        frame.setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        // Title bar
        root.add(createTitleBar(), BorderLayout.NORTH);

        // Card layout for pages
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(BG_DARK);

        cardPanel.add(createLaunchPage(), "launch");
        cardPanel.add(createTargetPage(), "target");

        root.add(cardPanel, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Detect targets
        new Thread(this::detectMinecraft).start();
    }

    // ── Title bar (draggable, with close/minimize) ──
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

        // Logo + name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        left.setOpaque(false);

        JLabel icon = new JLabel("⬣") {
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

        // Nav tabs
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

        bar.add(left, BorderLayout.CENTER);

        // Window buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        btns.setOpaque(false);

        JButton min = windowBtn("—");
        min.addActionListener(e -> frame.setExtendedState(JFrame.ICONIFIED));
        JButton close = windowBtn("✕");
        close.addActionListener(e -> System.exit(0));

        btns.add(min);
        btns.add(close);
        bar.add(btns, BorderLayout.EAST);

        // Dragging
        final Point[] drag = {null};
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { drag[0] = e.getPoint(); }
            public void mouseReleased(MouseEvent e) { drag[0] = null; }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (drag[0] != null) {
                    Point loc = frame.getLocation();
                    frame.setLocation(loc.x + e.getX() - drag[0].x, loc.y + e.getY() - drag[0].y);
                }
            }
        });

        return bar;
    }

    private JLabel dot() {
        JLabel d = new JLabel("·");
        d.setForeground(TEXT_DIM);
        d.setFont(new Font("SansSerif", Font.BOLD, 14));
        return d;
    }

    private JButton navButton(String text, boolean active) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", active ? Font.BOLD : Font.PLAIN, 13));
        b.setForeground(active ? TEXT_WHITE : TEXT_DIM);
        b.setBackground(active ? ACCENT : null);
        b.setOpaque(active);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (active) b.setBorder(new EmptyBorder(4, 14, 4, 14));
        else b.setBorder(new EmptyBorder(4, 10, 4, 10));
        return b;
    }

    private JButton windowBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 14));
        b.setForeground(TEXT_DIM);
        b.setBackground(null);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(28, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(TEXT_WHITE); }
            public void mouseExited(MouseEvent e) { b.setForeground(TEXT_DIM); }
        });
        return b;
    }

    // ── Launch page ──
    private JPanel createLaunchPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_DARK);

        // Sub-tabs: Client | Injection
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

        // Content area
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
                if (getModel().isPressed() || getBackground().equals(ACCENT)) {
                    g2.setColor(ACCENT);
                } else {
                    g2.setColor(BG_CARD);
                }
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

        // Left: info card
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

        // Protection status badge
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badge.setOpaque(false);
        badge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel dot = new JLabel("● ");
        dot.setForeground(GREEN);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
        badge.add(dot);
        JLabel badgeText = new JLabel("Protection status active");
        badgeText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        badgeText.setForeground(GREEN);
        badge.add(badgeText);
        card.add(badge);
        card.add(Box.createVerticalStrut(16));

        // Title
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

        // Supported versions
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

        // Description
        String desc = tab.equals("Client")
                ? "A focused competitive client built for consistent cross-version play, anti-cheat compatibility, and a curated module set."
                : "The complete LIMP experience with self-destruct, full customisation, and every protection capability enabled.";
        JLabel descLbl = new JLabel("<html><body style='width:170px'>" + desc + "</body></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLbl.setForeground(TEXT_DIM);
        descLbl.setAlignmentX(0);
        card.add(descLbl);
        card.add(Box.createVerticalGlue());

        // Bottom status + inject button
        JLabel status = new JLabel("● Minecraft target selection is the next step");
        status.setFont(new Font("SansSerif", Font.PLAIN, 11));
        status.setForeground(GREEN);
        status.setAlignmentX(0);
        card.add(status);
        card.add(Box.createVerticalStrut(10));

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
        inject.addActionListener(e -> cardLayout.show(cardPanel, "target"));
        card.add(inject);

        content.add(card, BorderLayout.WEST);

        // Right: capabilities + toggles
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        // Capabilities header
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

        // Capabilities
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

        // Toggles
        right.add(createToggle("Beta release channel", "Newest features; may be less stable.", betaChannel, v -> betaChannel = v));
        right.add(Box.createVerticalStrut(6));
        if (tab.equals("Client")) {
            right.add(createToggle("Fileless injection (TEST)", "Legacy temp-file path (StandardInjection).", filelessInjection, v -> filelessInjection = v));
        } else {
            right.add(createToggle("Zero-trace cleanup", "When you unload LIMP, wipes its last bytes from the game.", zeroTrace, v -> zeroTrace = v));
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

        JLabel icon = new JLabel(included ? "✓" : "−");
        icon.setFont(new Font("SansSerif", Font.BOLD, 13));
        icon.setForeground(included ? GREEN : TEXT_DIM);
        icon.setPreferredSize(new Dimension(20, 20));
        row.add(icon, BorderLayout.WEST);

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

        boolean[] on = {initial};
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

    // ── Target selection page ──
    private JPanel createTargetPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_DARK);
        page.setBorder(new EmptyBorder(20, 40, 20, 40));

        // Header badge
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel badgeRow = new JPanel(new BorderLayout());
        badgeRow.setOpaque(false);
        badgeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel badge = new JLabel("  ●  LIMP CLIENT  ");
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setForeground(ACCENT_LIGHT);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1, true), new EmptyBorder(3, 8, 3, 8)));
        badgeRow.add(badge, BorderLayout.WEST);

        JLabel countLabel = new JLabel("Scanning...");
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countLabel.setForeground(TEXT_DIM);
        badgeRow.add(countLabel, BorderLayout.EAST);
        top.add(badgeRow);
        top.add(Box.createVerticalStrut(14));

        JLabel chooseLabel = new JLabel("Choose your Minecraft target");
        chooseLabel.setFont(new Font("SansSerif", Font.BOLD | Font.ITALIC, 22));
        chooseLabel.setForeground(ACCENT_LIGHT);
        chooseLabel.setAlignmentX(0);
        top.add(chooseLabel);
        top.add(Box.createVerticalStrut(20));

        page.add(top, BorderLayout.NORTH);

        // Target list
        targetListPanel = new JPanel();
        targetListPanel.setOpaque(false);
        targetListPanel.setLayout(new BoxLayout(targetListPanel, BoxLayout.Y_AXIS));

        JLabel searching = new JLabel("Searching for Minecraft installations...");
        searching.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searching.setForeground(TEXT_DIM);
        searching.setAlignmentX(Component.CENTER_ALIGNMENT);
        targetListPanel.add(searching);

        JScrollPane scroll = new JScrollPane(targetListPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        page.add(scroll, BorderLayout.CENTER);

        // Back button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        bottom.setOpaque(false);
        JButton back = new JButton("Back to launch") {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT_LIGHT : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        back.setFont(new Font("SansSerif", Font.BOLD, 13));
        back.setForeground(Color.WHITE);
        back.setOpaque(false);
        back.setContentAreaFilled(false);
        back.setBorderPainted(false);
        back.setFocusPainted(false);
        back.setPreferredSize(new Dimension(200, 40));
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> cardLayout.show(cardPanel, "launch"));
        bottom.add(back);
        page.add(bottom, BorderLayout.SOUTH);

        // Store countLabel ref for updating
        page.putClientProperty("countLabel", countLabel);
        return page;
    }

    private void refreshTargetList() {
        SwingUtilities.invokeLater(() -> {
            targetListPanel.removeAll();

            if (targets.isEmpty()) {
                JLabel none = new JLabel("No Minecraft installations found. Click Browse below.");
                none.setFont(new Font("SansSerif", Font.PLAIN, 13));
                none.setForeground(TEXT_DIM);
                none.setAlignmentX(Component.CENTER_ALIGNMENT);
                targetListPanel.add(none);
                targetListPanel.add(Box.createVerticalStrut(12));

                JButton browse = new JButton("Browse for .minecraft folder");
                browse.setFont(new Font("SansSerif", Font.PLAIN, 12));
                browse.setForeground(ACCENT_LIGHT);
                browse.setBackground(null);
                browse.setOpaque(false);
                browse.setBorderPainted(false);
                browse.setFocusPainted(false);
                browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                browse.setAlignmentX(Component.CENTER_ALIGNMENT);
                browse.addActionListener(e -> browseTarget());
                targetListPanel.add(browse);
            } else {
                for (MinecraftTarget t : targets) {
                    targetListPanel.add(targetRow(t));
                    targetListPanel.add(Box.createVerticalStrut(8));
                }

                // Browse for more
                targetListPanel.add(Box.createVerticalStrut(8));
                JButton browse = new JButton("+ Add custom Minecraft folder");
                browse.setFont(new Font("SansSerif", Font.PLAIN, 11));
                browse.setForeground(ACCENT_LIGHT);
                browse.setBackground(null);
                browse.setOpaque(false);
                browse.setBorderPainted(false);
                browse.setFocusPainted(false);
                browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                browse.setAlignmentX(0);
                browse.addActionListener(e -> browseTarget());
                targetListPanel.add(browse);
            }

            // Update count
            JPanel targetPage = (JPanel) cardPanel.getComponent(1);
            JLabel countLabel = (JLabel) targetPage.getClientProperty("countLabel");
            if (countLabel != null) {
                countLabel.setText(targets.size() + " target" + (targets.size() != 1 ? "s" : "") + " detected");
            }

            targetListPanel.revalidate();
            targetListPanel.repaint();
        });
    }

    private JPanel targetRow(MinecraftTarget target) {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (target.alreadyLoaded) {
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 15));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 60));
                } else {
                    g2.setColor(BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(BORDER);
                }
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(14, 16, 14, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Minecraft icon placeholder
        JPanel iconPanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 130, 50));
                g2.fillRoundRect(0, 0, 36, 36, 6, 6);
                g2.setColor(new Color(80, 170, 60));
                g2.fillRect(4, 4, 14, 14);
                g2.setColor(new Color(100, 70, 40));
                g2.fillRect(4, 18, 28, 14);
                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(36, 36));
        row.add(iconPanel, BorderLayout.WEST);

        // Labels
        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(target.name);
        name.setFont(new Font("SansSerif", Font.BOLD, 13));
        name.setForeground(TEXT_WHITE);
        labels.add(name);
        String subText = target.alreadyLoaded ? "LIMP is already loaded in this target" : target.modsDir.toString();
        JLabel sub = new JLabel(subText);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(TEXT_DIM);
        labels.add(sub);
        row.add(labels, BorderLayout.CENTER);

        // Button
        if (target.alreadyLoaded) {
            JLabel loaded = new JLabel("Already loaded");
            loaded.setFont(new Font("SansSerif", Font.PLAIN, 11));
            loaded.setForeground(TEXT_DIM);
            loaded.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(TEXT_DIM, 1, true), new EmptyBorder(4, 12, 4, 12)));
            row.add(loaded, BorderLayout.EAST);
        } else {
            JButton injectBtn = new JButton("Inject") {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            injectBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            injectBtn.setForeground(Color.WHITE);
            injectBtn.setOpaque(false);
            injectBtn.setContentAreaFilled(false);
            injectBtn.setBorderPainted(false);
            injectBtn.setFocusPainted(false);
            injectBtn.setPreferredSize(new Dimension(80, 30));
            injectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            injectBtn.addActionListener(e -> doInject(target, injectBtn));
            row.add(injectBtn, BorderLayout.EAST);
        }

        return row;
    }

    // ── Injection logic ──
    private void doInject(MinecraftTarget target, JButton btn) {
        btn.setEnabled(false);
        btn.setText("...");

        new Thread(() -> {
            try {
                Files.createDirectories(target.modsDir);
                Path modSource = findModJar();
                if (modSource == null) {
                    SwingUtilities.invokeLater(() -> {
                        btn.setText("Error");
                        JOptionPane.showMessageDialog(frame,
                                MOD_JAR + " not found!\n\nPlace it next to LimpInjector.jar",
                                "LIMP Client", JOptionPane.ERROR_MESSAGE);
                        btn.setEnabled(true);
                        btn.setText("Inject");
                    });
                    return;
                }

                Path dest = target.modsDir.resolve(MOD_JAR);
                Files.copy(modSource, dest, StandardCopyOption.REPLACE_EXISTING);

                target.alreadyLoaded = true;
                refreshTargetList();

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btn.setText("Error");
                    btn.setEnabled(true);
                    JOptionPane.showMessageDialog(frame, "Injection failed: " + ex.getMessage(),
                            "LIMP Client", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // ── Detection ──
    private void detectMinecraft() {
        List<Path> candidates = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) candidates.add(Path.of(appdata, ".minecraft"));
            // Common launcher locations
            candidates.add(Path.of(home, "curseforge", "minecraft", "Instances"));
            candidates.add(Path.of(home, "AppData", "Roaming", ".minecraft"));
            // MultiMC / Prism Launcher instances
            candidates.add(Path.of(home, "AppData", "Roaming", "PrismLauncher", "instances"));
            candidates.add(Path.of(home, "AppData", "Local", "MultiMC", "instances"));
        } else if (os.contains("mac")) {
            candidates.add(Path.of(home, "Library", "Application Support", "minecraft"));
        } else {
            candidates.add(Path.of(home, ".minecraft"));
            candidates.add(Path.of(home, ".local", "share", "PrismLauncher", "instances"));
        }

        Set<String> seen = new HashSet<>();
        for (Path mc : candidates) {
            if (!Files.isDirectory(mc)) continue;

            // Standard .minecraft with mods folder
            Path mods = mc.resolve("mods");
            if (Files.isDirectory(mc.resolve("versions")) || Files.isDirectory(mods) || Files.exists(mc.resolve("options.txt"))) {
                String key = mc.toAbsolutePath().toString();
                if (seen.add(key)) {
                    String version = detectVersion(mc);
                    boolean loaded = Files.exists(mods.resolve(MOD_JAR));
                    targets.add(new MinecraftTarget("Minecraft " + version, version, mods, loaded));
                }
            }

            // Instance-based launchers (MultiMC, Prism, CurseForge)
            if (Files.isDirectory(mc)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(mc)) {
                    for (Path inst : stream) {
                        Path instMc = inst.resolve(".minecraft");
                        if (!Files.isDirectory(instMc)) instMc = inst;
                        Path instMods = instMc.resolve("mods");
                        if (Files.isDirectory(instMc.resolve("versions")) || Files.isDirectory(instMods)) {
                            String key = instMc.toAbsolutePath().toString();
                            if (seen.add(key)) {
                                String instVer = detectVersion(instMc);
                                boolean loaded = Files.exists(instMods.resolve(MOD_JAR));
                                targets.add(new MinecraftTarget(inst.getFileName() + " (" + instVer + ")", instVer, instMods, loaded));
                            }
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        refreshTargetList();
    }

    private String detectVersion(Path mcDir) {
        // Check version from fabric loader json or versions directory
        try {
            Path versions = mcDir.resolve("versions");
            if (Files.isDirectory(versions)) {
                String latest = "";
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(versions)) {
                    for (Path v : stream) {
                        String name = v.getFileName().toString();
                        if (name.contains("fabric") || name.matches("1\\.\\d+.*")) {
                            if (name.compareTo(latest) > 0) latest = name;
                        }
                    }
                }
                if (!latest.isEmpty()) return latest;
            }
        } catch (IOException ignored) {}
        return "1.21.11";
    }

    private void browseTarget() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select your .minecraft folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path mc = chooser.getSelectedFile().toPath();
            Path mods = mc.getFileName().toString().equals("mods") ? mc : mc.resolve("mods");
            boolean loaded = Files.exists(mods.resolve(MOD_JAR));
            targets.add(new MinecraftTarget("Custom (" + mc.getFileName() + ")", "1.21.11", mods, loaded));
            refreshTargetList();
        }
    }

    private Path findModJar() {
        try {
            Path self = Path.of(LimpInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = self.getParent();
            if (dir != null) {
                Path candidate = dir.resolve(MOD_JAR);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        Path cwd = Path.of(MOD_JAR);
        if (Files.exists(cwd)) return cwd;

        String home = System.getProperty("user.home");
        for (String sub : new String[]{"Downloads", "Desktop", "Belgeler", "İndirilenler"}) {
            Path p = Path.of(home, sub, MOD_JAR);
            if (Files.exists(p)) return p;
        }
        return null;
    }
}
