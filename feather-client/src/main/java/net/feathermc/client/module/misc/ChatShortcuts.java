package net.feathermc.client.module.misc;

import net.feathermc.client.module.Module;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChatShortcuts extends Module {
    private final Map<String, String> shortcuts = new LinkedHashMap<>();

    public ChatShortcuts() {
        super("Chat Shortcuts", "Expands short phrases into full messages", Category.MISC);
        shortcuts.put("/gl", "Good luck!");
        shortcuts.put("/hf", "Have fun!");
        shortcuts.put("/gg", "Good game!");
        shortcuts.put("/gf", "Good fight!");
        shortcuts.put("/wp", "Well played!");
        shortcuts.put("/ty", "Thank you!");
        shortcuts.put("/ns", "Nice shot!");
        shortcuts.put("/brb", "Be right back!");
        shortcuts.put("/ez", "That was easy!");
    }

    public String processMessage(String message) {
        if (!isEnabled()) return message;
        String trimmed = message.trim();
        String replacement = shortcuts.get(trimmed);
        if (replacement != null) return replacement;
        return message;
    }

    public Map<String, String> getShortcuts() {
        return shortcuts;
    }
}
