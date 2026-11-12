import java.lang.instrument.Instrumentation;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;

/**
 * Java Agent — injected into RUNNING Minecraft JVM via Attach API.
 * Finds Fabric's classloader, loads our mod JAR, and initializes it live.
 * Game stays running. No restart needed.
 */
public class LimpAgent {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[LIMP] Agent loaded into running JVM!");

        try {
            // args = "modsDir|modJarPath"
            String modsDir = null;
            String modJarPath = null;
            if (args != null && !args.isEmpty()) {
                String[] parts = args.split("\\|", 2);
                modsDir = parts[0];
                modJarPath = parts.length > 1 ? parts[1] : null;
            }

            // Step 1: Copy mod to mods folder for persistence
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

            // Step 2: Find Minecraft/Fabric classloader
            ClassLoader mcLoader = findMinecraftClassLoader();
            if (mcLoader == null) {
                System.err.println("[LIMP] Could not find Minecraft classloader — showing chat message only");
                showChatMessage(null);
                return;
            }
            System.out.println("[LIMP] Found Minecraft classloader: " + mcLoader.getClass().getName());

            // Step 3: Load our mod JAR into a classloader parented to Fabric's
            URL modUrl = null;
            if (modJarPath != null && Files.exists(Paths.get(modJarPath))) {
                modUrl = Paths.get(modJarPath).toUri().toURL();
            } else if (modsDir != null) {
                Path inMods = Paths.get(modsDir, MOD_JAR);
                if (Files.exists(inMods)) modUrl = inMods.toUri().toURL();
            }

            if (modUrl != null) {
                // Create classloader with our mod, parented to Fabric's loader
                URLClassLoader modLoader = new URLClassLoader(new URL[]{modUrl}, mcLoader);

                // Step 4: Try to initialize PrismClient (our mod's entry point)
                boolean initialized = tryInitializeMod(modLoader, mcLoader);
                if (initialized) {
                    System.out.println("[LIMP] Mod initialized successfully!");
                    showChatMessage(mcLoader, true);
                    return;
                }
            }

            // If mod init failed, at least show chat message
            showChatMessage(mcLoader, false);

        } catch (Exception e) {
            System.err.println("[LIMP] Agent error: " + e);
            e.printStackTrace();
        }
    }

    private static boolean tryInitializeMod(URLClassLoader modLoader, ClassLoader mcLoader) {
        // Try to load and call our mod's entry point
        String[] entryPoints = {
            "com.wailsvlr.prism.PrismClient",
            "com.wailsvlr.prism.LimpRuntime"
        };

        for (String className : entryPoints) {
            try {
                Class<?> modClass = modLoader.loadClass(className);
                System.out.println("[LIMP] Loaded " + className);

                // Try onInitializeClient() (Fabric entry point)
                try {
                    Method init = modClass.getMethod("onInitializeClient");
                    Object instance = null;

                    // Try to get INSTANCE field (singleton)
                    try {
                        Field instanceField = modClass.getDeclaredField("INSTANCE");
                        instanceField.setAccessible(true);
                        instance = instanceField.get(null);
                    } catch (NoSuchFieldException ignored) {}

                    if (instance == null) {
                        // Create new instance
                        try {
                            instance = modClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            instance = modClass.newInstance();
                        }
                    }

                    init.invoke(instance);
                    System.out.println("[LIMP] onInitializeClient() called!");
                    return true;
                } catch (NoSuchMethodException e) {
                    System.err.println("[LIMP] No onInitializeClient in " + className);
                }

                // Try static init method
                try {
                    Method staticInit = modClass.getMethod("init");
                    staticInit.invoke(null);
                    System.out.println("[LIMP] init() called!");
                    return true;
                } catch (NoSuchMethodException ignored) {}

            } catch (ClassNotFoundException e) {
                System.err.println("[LIMP] Class not found: " + className);
            } catch (Exception e) {
                System.err.println("[LIMP] Failed to init " + className + ": " + e);
                e.printStackTrace();
            }
        }
        return false;
    }

    private static void showChatMessage(ClassLoader mcLoader) {
        showChatMessage(mcLoader, false);
    }

    private static void showChatMessage(ClassLoader loader, boolean fullInit) {
        try {
            if (loader == null) loader = findMinecraftClassLoader();
            if (loader == null) return;

            Class<?> mcClass = loader.loadClass("net.minecraft.client.Minecraft");

            // Get Minecraft instance via static field (type == Minecraft)
            Object mc = null;
            for (String methodName : new String[]{"getInstance", "method_1551", "m_91087_"}) {
                try {
                    mc = mcClass.getMethod(methodName).invoke(null);
                    if (mc != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (mc == null) {
                for (Field f : mcClass.getDeclaredFields()) {
                    if (f.getType() == mcClass && Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        mc = f.get(null);
                        if (mc != null) break;
                    }
                }
            }
            if (mc == null) return;

            final Object mcInst = mc;
            final ClassLoader fLoader = loader;
            final boolean fFullInit = fullInit;

            Runnable task = new Runnable() {
                public void run() {
                    try { doSendChat(mcInst, fLoader, fFullInit); }
                    catch (Exception e) { System.err.println("[LIMP] Chat failed: " + e); }
                }
            };

            // Run on Minecraft's main thread
            boolean scheduled = false;
            for (String m : new String[]{"execute", "method_18858", "m_18707_", "tell"}) {
                try {
                    mcClass.getMethod(m, Runnable.class).invoke(mc, task);
                    scheduled = true;
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (!scheduled) task.run();

        } catch (Exception e) {
            System.err.println("[LIMP] showChatMessage error: " + e);
        }
    }

    private static void doSendChat(Object mc, ClassLoader loader, boolean fullInit) throws Exception {
        Object gui = getFieldValue(mc, "gui", "field_1705", "f_91065_");
        if (gui == null) return;

        Object chat = getFieldValue(gui, "chat", "field_2013", "f_93823_");
        if (chat == null) {
            for (String m : new String[]{"getChat", "method_2162", "m_93820_"}) {
                try { chat = gui.getClass().getMethod(m).invoke(gui); if (chat != null) break; }
                catch (NoSuchMethodException ignored) {}
            }
        }
        if (chat == null) return;

        // Component.literal()
        Class<?> compClass = null;
        for (String cn : new String[]{
            "net.minecraft.network.chat.Component",
            "net.minecraft.class_2561",
            "net.minecraft.text.Text"
        }) {
            try { compClass = loader.loadClass(cn); break; } catch (ClassNotFoundException ignored) {}
        }
        if (compClass == null) return;

        String text = fullInit
                ? "§d§l[LIMP Client]§r §aInjected & loaded! Press RIGHT SHIFT for menu."
                : "§d§l[LIMP Client]§r §7Injected! Restart once for full features.";

        Object message = null;
        for (String m : new String[]{"literal", "method_43470", "m_237113_"}) {
            try { message = compClass.getMethod(m, String.class).invoke(null, text); if (message != null) break; }
            catch (NoSuchMethodException ignored) {}
        }
        if (message == null) return;

        for (String m : new String[]{"addMessage", "method_932", "m_93791_"}) {
            try {
                chat.getClass().getMethod(m, compClass).invoke(chat, message);
                System.out.println("[LIMP] Chat message sent!");
                return;
            } catch (NoSuchMethodException ignored) {}
        }
    }

    private static Object getFieldValue(Object obj, String... names) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
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

    private static ClassLoader findMinecraftClassLoader() {
        Thread[] threads = new Thread[Thread.activeCount() + 20];
        int count = Thread.enumerate(threads);

        // Priority: Render/Main/Client threads first
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t == null) continue;
            String name = t.getName().toLowerCase();
            if (name.contains("render") || name.contains("main") || name.contains("client")) {
                ClassLoader cl = t.getContextClassLoader();
                if (cl != null && canLoadMinecraft(cl)) return cl;
            }
        }

        // Try all threads
        for (int i = 0; i < count; i++) {
            if (threads[i] == null) continue;
            ClassLoader cl = threads[i].getContextClassLoader();
            if (cl != null && canLoadMinecraft(cl)) return cl;
        }

        // System classloader chain
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (canLoadMinecraft(cl)) return cl;
            cl = cl.getParent();
        }
        return null;
    }

    private static boolean canLoadMinecraft(ClassLoader cl) {
        try {
            cl.loadClass("net.minecraft.client.Minecraft");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
