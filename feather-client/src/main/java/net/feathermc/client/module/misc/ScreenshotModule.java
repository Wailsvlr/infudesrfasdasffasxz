package net.feathermc.client.module.misc;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class ScreenshotModule extends Module {
    private final BooleanSetting copyToClipboard = addSetting(new BooleanSetting("Copy to Clipboard", true));
    private final BooleanSetting playSound = addSetting(new BooleanSetting("Play Sound", true));
    private long lastScreenshotCheck;

    public ScreenshotModule() {
        super("Screenshot", "Enhanced screenshots with clipboard copy", Category.MISC);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!copyToClipboard.getValue()) return;
        if (mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScreenshotCheck < 500) return;
        lastScreenshotCheck = now;

        Path screenshotsDir = mc.gameDirectory.toPath().resolve("screenshots");
        if (!Files.exists(screenshotsDir)) return;

        try {
            Files.list(screenshotsDir)
                    .filter(p -> p.toString().endsWith(".png"))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() > now - 1000;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .ifPresent(this::copyImageToClipboard);
        } catch (IOException ignored) {}
    }

    private void copyImageToClipboard(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) return;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                    return image;
                }
            }, null);
            FeatherClient.LOGGER.info("Screenshot copied to clipboard");
        } catch (Exception e) {
            FeatherClient.LOGGER.error("Failed to copy screenshot to clipboard", e);
        }
    }
}
