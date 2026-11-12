import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;

public class LimpAgent {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";
    private static Instrumentation savedInst;

    public static void agentmain(String args, Instrumentation inst) {
        savedInst = inst;
        System.out.println("[LIMP] ========================================");
        System.out.println("[LIMP] Agent loaded into running JVM!");
        System.out.println("[LIMP] Args: " + args);

        try {
            String modsDir = null;
            String modJarPath = null;
            if (args != null && !args.isEmpty()) {
                String[] parts = args.split("\\|", 2);
                modsDir = parts[0];
                modJarPath = parts.length > 1 ? parts[1] : null;
            }

            // Copy mod to mods folder for persistence (next restart loads full features)
            if (modsDir != null && modJarPath != null) {
                try {
                    Path mods = Paths.get(modsDir);
                    Files.createDirectories(mods);
                    Files.copy(Paths.get(modJarPath), mods.resolve(MOD_JAR), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[LIMP] Mod copied to " + mods);
                } catch (Exception e) {
                    System.err.println("[LIMP] Mod copy failed: " + e);
                }
            }

            // Find Minecraft's classloader
            ClassLoader mcLoader = findMinecraftClassLoader();
            if (mcLoader == null) {
                System.err.println("[LIMP] Could not find Minecraft classloader!");
                System.err.println("[LIMP] Listing all threads for debug:");
                Thread[] threads = new Thread[Thread.activeCount() + 50];
                int count = Thread.enumerate(threads);
                for (int i = 0; i < count; i++) {
                    if (threads[i] != null) {
                        ClassLoader cl = threads[i].getContextClassLoader();
                        System.err.println("[LIMP]   Thread: " + threads[i].getName()
                            + " | CL: " + (cl == null ? "null" : cl.getClass().getName()));
                    }
                }
                return;
            }

            // Step 1: Change window title (most visible proof of injection)
            changeWindowTitle(mcLoader);

            // Step 2: Send chat message
            sendChatMessage(mcLoader);

            // Step 3: Install render hook via class retransformation
            installRenderHook(mcLoader, inst);

            System.out.println("[LIMP] ========================================");
            System.out.println("[LIMP] Injection complete!");

        } catch (Exception e) {
            System.err.println("[LIMP] Agent error: " + e);
            e.printStackTrace();
        }
    }

    // ── Change Minecraft Window Title via GLFW ──
    private static void changeWindowTitle(ClassLoader mcLoader) {
        try {
            System.out.println("[LIMP] Changing window title...");

            // Load GLFW class from Minecraft's classloader
            Class<?> glfwClass = mcLoader.loadClass("org.lwjgl.glfw.GLFW");
            Method setTitle = glfwClass.getMethod("glfwSetWindowTitle", long.class, CharSequence.class);

            // Find Minecraft instance
            Object mc = findMinecraftInstance(mcLoader);
            if (mc == null) {
                System.err.println("[LIMP] Could not find Minecraft instance for title change");
                return;
            }

            // Find Window object (has a long 'handle' field)
            long windowHandle = findWindowHandle(mc);
            if (windowHandle == 0) {
                System.err.println("[LIMP] Could not find GLFW window handle");
                return;
            }

            // Change the title!
            setTitle.invoke(null, windowHandle, "Minecraft - LIMP Client Injected!");
            System.out.println("[LIMP] Window title changed!");

        } catch (Exception e) {
            System.err.println("[LIMP] Title change failed: " + e);
        }
    }

    // ── Send Chat Message ──
    private static void sendChatMessage(ClassLoader loader) {
        try {
            System.out.println("[LIMP] Sending chat message...");

            Object mc = findMinecraftInstance(loader);
            if (mc == null) return;

            // Schedule on MC's main thread
            Runnable task = new Runnable() {
                public void run() {
                    try {
                        doSendChat(mc, loader);
                    } catch (Exception e) {
                        System.err.println("[LIMP] Chat send error: " + e);
                    }
                }
            };

            // Try to execute on MC thread
            boolean scheduled = false;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Runnable.class) {
                    m.invoke(mc, task);
                    scheduled = true;
                    System.out.println("[LIMP] Chat task scheduled on MC thread");
                    break;
                }
            }
            // Also try "tell" method
            if (!scheduled) {
                for (Method m : mc.getClass().getMethods()) {
                    if (m.getName().equals("tell") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == Runnable.class) {
                        m.invoke(mc, task);
                        scheduled = true;
                        break;
                    }
                }
            }
            if (!scheduled) {
                System.err.println("[LIMP] Could not schedule on MC thread, running directly");
                task.run();
            }

        } catch (Exception e) {
            System.err.println("[LIMP] Chat message failed: " + e);
        }
    }

    private static void doSendChat(Object mc, ClassLoader loader) throws Exception {
        // Find the GUI/HUD object
        Object gui = findFieldByNames(mc, "gui", "field_1705", "f_91065_", "inGameHud");
        if (gui == null) {
            // Try by type: search for a field whose type name contains "Gui" or "Hud"
            gui = findFieldByTypeName(mc, "Gui", "Hud", "InGame");
        }
        if (gui == null) {
            System.err.println("[LIMP] Could not find GUI/HUD object");
            System.err.println("[LIMP] MC class: " + mc.getClass().getName());
            System.err.println("[LIMP] Fields:");
            for (Field f : mc.getClass().getDeclaredFields()) {
                System.err.println("[LIMP]   " + f.getName() + " : " + f.getType().getName());
            }
            return;
        }
        System.out.println("[LIMP] Found GUI: " + gui.getClass().getName());

        // Find chat component
        Object chat = findFieldByNames(gui, "chat", "field_2013", "f_93823_", "chatHud");
        if (chat == null) {
            chat = findFieldByTypeName(gui, "Chat");
        }
        if (chat == null) {
            // Try getter methods
            for (Method m : gui.getClass().getMethods()) {
                String mn = m.getName().toLowerCase();
                if ((mn.contains("chat") || mn.contains("getchathud")) && m.getParameterCount() == 0) {
                    try {
                        chat = m.invoke(gui);
                        if (chat != null) break;
                    } catch (Exception ignored) {}
                }
            }
        }
        if (chat == null) {
            System.err.println("[LIMP] Could not find chat component");
            return;
        }
        System.out.println("[LIMP] Found chat: " + chat.getClass().getName());

        // Create text component via Component.literal() or Text.literal()
        Object message = createTextComponent(loader,
            "§d§l[LIMP Client]§r §aInjected! Press RIGHT SHIFT for menu.");
        if (message == null) {
            System.err.println("[LIMP] Could not create text component");
            return;
        }

        // Call addMessage
        boolean sent = false;
        for (Method m : chat.getClass().getMethods()) {
            if (m.getName().equals("addMessage") && m.getParameterCount() == 1) {
                try {
                    m.invoke(chat, message);
                    sent = true;
                    System.out.println("[LIMP] Chat message sent!");
                    break;
                } catch (Exception e) {
                    System.err.println("[LIMP] addMessage failed: " + e);
                }
            }
        }
        if (!sent) {
            // Try any method that takes a Component/Text
            for (Method m : chat.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(message)) {
                    try {
                        m.invoke(chat, message);
                        System.out.println("[LIMP] Message sent via " + m.getName());
                        sent = true;
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static Object createTextComponent(ClassLoader loader, String text) {
        // Try multiple class names for the text Component interface
        String[] classNames = {
            "net.minecraft.network.chat.Component",
            "net.minecraft.text.Text",
            "net.minecraft.class_2561",
        };
        for (String cn : classNames) {
            try {
                Class<?> compClass = loader.loadClass(cn);
                // Try literal() static method
                for (Method m : compClass.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && compClass.isAssignableFrom(m.getReturnType())) {
                        try {
                            Object result = m.invoke(null, text);
                            if (result != null) return result;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    // ── Install Render Hook ──
    private static void installRenderHook(ClassLoader mcLoader, Instrumentation inst) {
        // This is a placeholder — in a full implementation, we'd use ASM
        // to modify the render method and draw our own overlay.
        // For now, we just log that we could do it.
        System.out.println("[LIMP] Instrumentation available: " + (inst != null));
        if (inst != null) {
            System.out.println("[LIMP] Can retransform: " + inst.isRetransformClassesSupported());
            System.out.println("[LIMP] Can redefine: " + inst.isRedefineClassesSupported());
            Class<?>[] loaded = inst.getAllLoadedClasses();
            int mcClasses = 0;
            for (Class<?> c : loaded) {
                if (c.getName().startsWith("net.minecraft.")) mcClasses++;
            }
            System.out.println("[LIMP] Loaded MC classes: " + mcClasses + " / " + loaded.length + " total");
        }
    }

    // ── Helper: Find Minecraft Instance ──
    private static Object findMinecraftInstance(ClassLoader loader) {
        // Try known class names
        String[] classNames = {
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.MinecraftClient",
            "net.minecraft.class_310",
        };
        for (String cn : classNames) {
            try {
                Class<?> mcClass = loader.loadClass(cn);
                Object mc = getStaticSelf(mcClass);
                if (mc != null) {
                    System.out.println("[LIMP] Found MC instance via " + cn);
                    return mc;
                }
            } catch (ClassNotFoundException ignored) {}
        }

        // Brute force: find a class with a static field of its own type (singleton)
        if (savedInst != null) {
            for (Class<?> c : savedInst.getAllLoadedClasses()) {
                if (!c.getName().startsWith("net.minecraft.client.")) continue;
                try {
                    Object instance = getStaticSelf(c);
                    if (instance != null) {
                        // Check if it has window-related fields (Minecraft has a Window field)
                        for (Field f : c.getDeclaredFields()) {
                            String ft = f.getType().getName().toLowerCase();
                            if (ft.contains("window") || ft.contains("glfw")) {
                                System.out.println("[LIMP] Found MC instance via brute force: " + c.getName());
                                return instance;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        System.err.println("[LIMP] Could not find Minecraft instance");
        return null;
    }

    private static Object getStaticSelf(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getType() == clazz && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                try {
                    Object val = f.get(null);
                    if (val != null) return val;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ── Helper: Find GLFW Window Handle ──
    private static long findWindowHandle(Object mc) {
        // First find the Window object
        Object window = null;

        // Try known field names
        window = findFieldByNames(mc, "window", "field_1Doc", "f_91069_");
        if (window == null) {
            // Search for a field whose type has a 'handle' long field
            for (Field f : mc.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(mc);
                    if (val == null) continue;
                    // Check if this object has a long field named "handle" or similar
                    for (Field wf : val.getClass().getDeclaredFields()) {
                        if (wf.getType() == long.class) {
                            wf.setAccessible(true);
                            long handle = wf.getLong(val);
                            // GLFW handles are always positive and large
                            if (handle > 0) {
                                System.out.println("[LIMP] Found window handle via " + f.getName()
                                    + "." + wf.getName() + " = " + handle);
                                return handle;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (window != null) {
            // Get handle from Window object
            for (Field f : window.getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    try {
                        long h = f.getLong(window);
                        if (h > 0) return h;
                    } catch (Exception ignored) {}
                }
            }
        }

        return 0;
    }

    // ── Helper: Find field by multiple possible names ──
    private static Object findFieldByNames(Object obj, String... names) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                for (String name : names) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        try { return f.get(obj); } catch (Exception ignored) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    // ── Helper: Find field by type name containing keyword ──
    private static Object findFieldByTypeName(Object obj, String... keywords) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String typeName = f.getType().getName();
                for (String kw : keywords) {
                    if (typeName.contains(kw)) {
                        f.setAccessible(true);
                        try {
                            Object val = f.get(obj);
                            if (val != null) return val;
                        } catch (Exception ignored) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    // ── Find Minecraft's ClassLoader ──
    private static ClassLoader findMinecraftClassLoader() {
        Thread[] threads = new Thread[Thread.activeCount() + 50];
        int count = Thread.enumerate(threads);

        // Priority: Render thread first
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t == null) continue;
            String name = t.getName().toLowerCase();
            if (name.contains("render") || name.contains("main") || name.contains("client")) {
                ClassLoader cl = t.getContextClassLoader();
                if (cl != null && canLoadMinecraft(cl)) {
                    System.out.println("[LIMP] Found MC classloader on thread: " + t.getName());
                    return cl;
                }
            }
        }

        // Try all threads
        for (int i = 0; i < count; i++) {
            if (threads[i] == null) continue;
            ClassLoader cl = threads[i].getContextClassLoader();
            if (cl != null && canLoadMinecraft(cl)) {
                System.out.println("[LIMP] Found MC classloader on thread: " + threads[i].getName());
                return cl;
            }
        }

        return null;
    }

    private static boolean canLoadMinecraft(ClassLoader cl) {
        String[] tryClasses = {
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.MinecraftClient",
            "net.minecraft.class_310",
        };
        for (String cn : tryClasses) {
            try {
                cl.loadClass(cn);
                return true;
            } catch (ClassNotFoundException ignored) {}
        }
        return false;
    }
}
