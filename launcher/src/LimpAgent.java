import java.lang.instrument.Instrumentation;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import javax.swing.*;
import java.awt.*;

public class LimpAgent {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";
    private static Instrumentation savedInst;
    private static StringBuilder logBuf = new StringBuilder();
    private static String logPath;
    private static volatile JLabel overlayStatus;

    private static void log(String msg) {
        System.out.println("[LIMP] " + msg);
        logBuf.append(msg).append('\n');
        flush();
    }

    private static void flush() {
        if (logPath == null) {
            String t = System.getenv("TEMP");
            if (t == null) t = System.getenv("TMP");
            if (t == null) t = System.getProperty("java.io.tmpdir");
            logPath = t + File.separator + "limp_agent_log.txt";
        }
        try { Files.write(Paths.get(logPath), logBuf.toString().getBytes("UTF-8")); }
        catch (Exception x) {}
    }

    public static void agentmain(String args, Instrumentation inst) {
        savedInst = inst;
        log("=== LIMP Agent v2 ===");
        log("Java " + System.getProperty("java.version"));

        // ── STEP 0: Show overlay ON TOP of Minecraft (guaranteed visible) ──
        try {
            showOverlay();
            log("Overlay shown!");
        } catch (Throwable t) {
            log("Overlay failed: " + t);
        }

        try {
            // ── STEP 1: Copy mod for persistence ──
            copyMod(args);

            // ── STEP 2: Discover classes via Instrumentation ──
            log("Scanning loaded classes via Instrumentation...");
            Class<?>[] allClasses = inst.getAllLoadedClasses();
            ClassLoader mcLoader = null;
            int mcTotal = 0;
            int clientTotal = 0;
            StringBuilder clientSample = new StringBuilder();

            for (Class<?> c : allClasses) {
                String name = c.getName();
                if (!name.startsWith("net.minecraft.")) continue;
                mcTotal++;
                if (mcLoader == null && c.getClassLoader() != null) {
                    mcLoader = c.getClassLoader();
                }
                if (name.startsWith("net.minecraft.client.")) {
                    clientTotal++;
                    if (clientSample.length() < 2000) {
                        clientSample.append("  ").append(name).append('\n');
                    }
                }
            }

            log("MC classes: " + mcTotal + ", client classes: " + clientTotal);
            log("ClassLoader: " + (mcLoader == null ? "NULL" : mcLoader.getClass().getName()));
            if (clientTotal > 0) {
                log("Client class sample:\n" + clientSample);
            }

            // ── STEP 3: Find MinecraftClient singleton ──
            Class<?> mcClass = null;
            Object mcInstance = null;
            int bestFieldCount = 0;

            for (Class<?> c : allClasses) {
                if (!c.getName().startsWith("net.minecraft.")) continue;
                try {
                    Field[] fields = c.getDeclaredFields();
                    if (fields.length <= bestFieldCount) continue;
                    for (Field f : fields) {
                        if (f.getType() == c && Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);
                            Object val = f.get(null);
                            if (val != null && fields.length > bestFieldCount) {
                                mcClass = c;
                                mcInstance = val;
                                bestFieldCount = fields.length;
                            }
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (mcInstance != null) {
                log("MC Singleton: " + mcClass.getName() + " [" + bestFieldCount + " fields]");
                StringBuilder fb = new StringBuilder();
                Field[] ff = mcClass.getDeclaredFields();
                for (int i = 0; i < Math.min(ff.length, 50); i++) {
                    fb.append(ff[i].getName()).append(':').append(ff[i].getType().getSimpleName());
                    if (Modifier.isStatic(ff[i].getModifiers())) fb.append("[S]");
                    fb.append(' ');
                    if (fb.length() > 1500) break;
                }
                log("Fields: " + fb);
                setOverlay("MC found: " + mcClass.getName(), new Color(100, 200, 255));
            } else {
                log("No MC singleton found! (best had " + bestFieldCount + " fields)");
                // Fallback: try known names via classloader
                if (mcLoader != null) {
                    String[] names = {
                        "net.minecraft.client.Minecraft",
                        "net.minecraft.client.MinecraftClient",
                        "net.minecraft.class_310"
                    };
                    for (String name : names) {
                        try {
                            Class<?> c = mcLoader.loadClass(name);
                            for (Field f : c.getDeclaredFields()) {
                                if (f.getType() == c && Modifier.isStatic(f.getModifiers())) {
                                    f.setAccessible(true);
                                    Object val = f.get(null);
                                    if (val != null) {
                                        mcClass = c;
                                        mcInstance = val;
                                        log("Found MC via name: " + name);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        if (mcInstance != null) break;
                    }
                }
            }

            // ── STEP 4: Change GLFW window title ──
            boolean titleChanged = false;
            if (mcLoader != null && mcInstance != null) {
                titleChanged = changeWindowTitle(mcLoader, mcInstance);
            }

            // ── STEP 5: Send chat message ──
            if (mcInstance != null && mcLoader != null) {
                sendChatMessage(mcInstance, mcLoader);
            }

            // Final overlay status
            if (titleChanged) {
                setOverlay("LIMP Active!", new Color(80, 255, 120));
            } else if (mcInstance != null) {
                setOverlay("Loaded (title fail)", new Color(255, 255, 80));
            } else {
                setOverlay("Loaded (no MC)", new Color(255, 200, 80));
            }

            log("=== Agent done ===");
        } catch (Throwable e) {
            log("FATAL: " + e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log(sw.toString());
            setOverlay("Error!", new Color(255, 80, 80));
        }
    }

    // ── Swing Overlay (appears on top of fullscreen Minecraft) ──

    private static void showOverlay() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
                try {
                    final JFrame frame = new JFrame();
                    frame.setUndecorated(true);
                    frame.setAlwaysOnTop(true);
                    frame.setFocusableWindowState(false);
                    frame.setType(Window.Type.UTILITY);
                    frame.setSize(320, 52);
                    Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
                    frame.setLocation((scr.width - 320) / 2, 18);
                    try { frame.setOpacity(0.92f); } catch (Exception ignored) {}

                    JPanel panel = new JPanel(new BorderLayout(0, 1));
                    panel.setBackground(new Color(16, 16, 26));
                    panel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(140, 70, 255), 2),
                        BorderFactory.createEmptyBorder(4, 14, 4, 14)));

                    JLabel title = new JLabel("LIMP CLIENT", SwingConstants.CENTER);
                    title.setForeground(new Color(140, 70, 255));
                    title.setFont(new Font("Consolas", Font.BOLD, 15));

                    overlayStatus = new JLabel("injecting...", SwingConstants.CENTER);
                    overlayStatus.setForeground(new Color(170, 170, 170));
                    overlayStatus.setFont(new Font("Consolas", Font.PLAIN, 11));

                    panel.add(title, BorderLayout.NORTH);
                    panel.add(overlayStatus, BorderLayout.CENTER);
                    frame.setContentPane(panel);
                    frame.setVisible(true);

                    javax.swing.Timer hide = new javax.swing.Timer(12000,
                        new java.awt.event.ActionListener() {
                            public void actionPerformed(java.awt.event.ActionEvent e) {
                                frame.dispose();
                            }
                        });
                    hide.setRepeats(false);
                    hide.start();
                } catch (Exception e) {
                    System.err.println("[LIMP] overlay create: " + e);
                }
            }
        });
    }

    private static void setOverlay(final String text, final Color color) {
        if (overlayStatus == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    overlayStatus.setText(text);
                    overlayStatus.setForeground(color);
                } catch (Exception ignored) {}
            }
        });
    }

    // ── Copy mod to mods folder ──

    private static void copyMod(String args) {
        if (args == null || args.isEmpty()) return;
        String[] parts = args.split("\\|", 2);
        if (parts.length < 2) return;
        String modsDir = parts[0];
        String modJar = parts[1];
        try {
            Path mods = Paths.get(modsDir);
            Files.createDirectories(mods);
            Files.copy(Paths.get(modJar), mods.resolve(MOD_JAR), StandardCopyOption.REPLACE_EXISTING);
            log("Mod copied -> " + mods);
        } catch (Exception e) {
            log("Mod copy failed: " + e);
        }
    }

    // ── GLFW Window Title Change ──

    private static boolean changeWindowTitle(ClassLoader loader, Object mc) {
        try {
            log("Changing window title...");
            Class<?> glfw = loader.loadClass("org.lwjgl.glfw.GLFW");
            Method setTitle = glfw.getMethod("glfwSetWindowTitle", long.class, CharSequence.class);

            long handle = findGLFWHandle(mc);
            if (handle == 0) {
                log("No GLFW window handle found!");
                logFieldTree(mc, 1);
                return false;
            }

            setTitle.invoke(null, handle, "Minecraft - LIMP Client");
            log("Window title changed! handle=" + handle);
            return true;
        } catch (Exception e) {
            log("Title change error: " + e);
            return false;
        }
    }

    private static long findGLFWHandle(Object mc) {
        // Strategy 1: find a field whose type has EXACTLY one non-static long field
        for (Field f : mc.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(mc);
                if (val == null) continue;
                int longCount = 0;
                Field candidate = null;
                for (Field wf : val.getClass().getDeclaredFields()) {
                    if (wf.getType() == long.class && !Modifier.isStatic(wf.getModifiers())) {
                        longCount++;
                        candidate = wf;
                    }
                }
                if (longCount == 1 && candidate != null) {
                    candidate.setAccessible(true);
                    long h = candidate.getLong(val);
                    if (h > 0xFFFF) {
                        log("Handle (exact): " + f.getName() + "." + candidate.getName()
                            + "=" + h + " type=" + val.getClass().getSimpleName());
                        return h;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Strategy 2: any sub-object with a large positive long
        for (Field f : mc.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(mc);
                if (val == null) continue;
                for (Field wf : val.getClass().getDeclaredFields()) {
                    if (wf.getType() != long.class || Modifier.isStatic(wf.getModifiers())) continue;
                    wf.setAccessible(true);
                    long h = wf.getLong(val);
                    if (h > 0xFFFF) {
                        log("Handle (broad): " + f.getName() + "." + wf.getName()
                            + "=" + h + " type=" + val.getClass().getSimpleName());
                        return h;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return 0;
    }

    private static void logFieldTree(Object obj, int depth) {
        if (depth > 2) return;
        String indent = depth == 1 ? "  " : "    ";
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            log(indent + f.getName() + " : " + f.getType().getSimpleName());
        }
    }

    // ── Chat Message ──

    private static void sendChatMessage(final Object mc, final ClassLoader loader) {
        try {
            log("Attempting chat message...");

            // Find execute(Runnable) on MC instance for thread safety
            Method execMethod = null;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    String mn = m.getName();
                    if (mn.equals("execute") || mn.equals("tell") || mn.startsWith("method_")) {
                        execMethod = m;
                        break;
                    }
                }
            }

            Runnable chatTask = new Runnable() {
                public void run() {
                    doChat(mc, loader);
                }
            };

            if (execMethod != null) {
                execMethod.invoke(mc, chatTask);
                log("Chat scheduled via " + execMethod.getName());
            } else {
                log("No execute method, running chat directly");
                chatTask.run();
            }
        } catch (Exception e) {
            log("Chat error: " + e);
        }
    }

    private static void doChat(Object mc, ClassLoader loader) {
        try {
            // Navigate: MC -> inGameHud -> chatHud -> addMessage(Text)
            Object gui = findNonStaticField(mc, "Hud", "Gui", "InGame", "Overlay");
            if (gui == null) {
                gui = findFieldByName(mc, "field_1705", "inGameHud", "gui", "f_91065_");
            }
            if (gui == null) {
                log("GUI not found. MC fields:");
                for (Field f : mc.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    log("  " + f.getName() + ":" + f.getType().getName());
                }
                return;
            }
            log("GUI: " + gui.getClass().getName());

            Object chat = findNonStaticField(gui, "Chat");
            if (chat == null) {
                chat = findFieldByName(gui, "field_2013", "chatHud", "chat", "f_93823_");
            }
            if (chat == null) {
                log("Chat not found in GUI");
                return;
            }
            log("Chat: " + chat.getClass().getName());

            // Create text component
            Object text = createText(loader,
                "§d§l[LIMP]§r §aInjected! Press RSHIFT for menu.");
            if (text == null) {
                log("Could not create Text component");
                return;
            }
            log("Text created: " + text.getClass().getName());

            // Call addMessage or any compatible method
            for (Method m : chat.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(text)) {
                    try {
                        m.invoke(chat, text);
                        log("Chat sent via " + m.getName() + "!");
                        return;
                    } catch (Exception e) {
                        log("Method " + m.getName() + " failed: " + e);
                    }
                }
            }
            log("No compatible chat method found");
        } catch (Exception e) {
            log("doChat error: " + e);
        }
    }

    private static Object createText(ClassLoader loader, String str) {
        // Strategy 1: scan all loaded interfaces for static String->self method
        if (savedInst != null) {
            for (Class<?> c : savedInst.getAllLoadedClasses()) {
                if (!c.getName().startsWith("net.minecraft.")) continue;
                if (!c.isInterface()) continue;
                try {
                    for (Method m : c.getDeclaredMethods()) {
                        if (!Modifier.isStatic(m.getModifiers())) continue;
                        if (m.getParameterCount() != 1) continue;
                        if (m.getParameterTypes()[0] != String.class) continue;
                        if (!c.isAssignableFrom(m.getReturnType())) continue;
                        try {
                            Object r = m.invoke(null, str);
                            if (r != null) {
                                log("Text via " + c.getSimpleName() + "." + m.getName());
                                return r;
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        }
        // Strategy 2: known names
        String[] names = {
            "net.minecraft.network.chat.Component",
            "net.minecraft.text.Text",
            "net.minecraft.class_2561"
        };
        for (String name : names) {
            try {
                Class<?> c = loader.loadClass(name);
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                        try {
                            Object r = m.invoke(null, str);
                            if (r != null) return r;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Helpers ──

    private static Object findNonStaticField(Object obj, String... typeKeywords) {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String typeName = f.getType().getName();
            for (String kw : typeKeywords) {
                if (typeName.toLowerCase().contains(kw.toLowerCase())) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(obj);
                        if (val != null) return val;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private static Object findFieldByName(Object obj, String... names) {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            for (String name : names) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(obj);
                        if (val != null) return val;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }
}
