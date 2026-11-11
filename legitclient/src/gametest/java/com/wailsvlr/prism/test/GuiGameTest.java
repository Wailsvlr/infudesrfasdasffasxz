package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.gui.ClickGuiScreen;
import com.wailsvlr.prism.modules.ClickGuiModule;
import com.wailsvlr.prism.modules.StorageFinder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick ClickGUI look check (Sidebar style, the default): the category list, a row click, the settings dots, and
 * the HUD's text module list. Screenshots in screenshots/. Run: -PprismOnly=gui
 */
public class GuiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!System.getProperty("prism.only", "").equals("gui")) return;
		List<String> log = new ArrayList<>();
		ctx.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			ctx.runOnClient(mc -> mc.options.guiScale().set(2));
			ctx.waitTicks(10);
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(40);
			double s = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
			float w = ctx.computeOnClient(mc -> PrismClient.modules().get(ClickGuiModule.class).panelWidth.getFloat());
			// the logo box starts folded in the top right; its search lists modules
			float[] np0 = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.panelPosForTest("nav") : new float[2]);
			boolean folded = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui && !gui.navOpenForTest());
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.setSearchForTest("finder"); });
			ctx.getInput().setCursorPos(10, 1070);
			ctx.waitTicks(15);
			List<String> found = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.navResultsForTest() : List.<String>of());
			ctx.takeScreenshot("gui_logo_search");
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.setSearchForTest(""); });
			log.add((folded && np0[0] > 600 && found.contains("StorageFinder") ? "PASS" : "FAIL") + "  the logo box sits folded in the top right and its search lists modules ("
				+ np0[0] + ", " + found + ")");
			ctx.waitTicks(10);
			ctx.takeScreenshot("gui_layout");
			// its arrow unfolds the category list
			ctx.getInput().setCursorPos((np0[0] + w - 8) * s, (np0[1] + 10) * s);
			ctx.waitTicks(3);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(15);
			boolean unfolded = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui && gui.navOpenForTest());
			log.add((unfolded ? "PASS" : "FAIL") + "  the arrow on the logo box shows the categories");
			float nx = np0[0] + 24, ny = np0[1];
			float[] rp = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.catPosForTest(com.wailsvlr.prism.module.Category.RENDER) : new float[2]);
			float panelX = rp[0], rowY = rp[1] + 20 + 2 + 10;
			// a row click toggles the module (its row fills with colour)
			ctx.getInput().setCursorPos((panelX + 40) * s, rowY * s);
			ctx.waitTicks(5);
			boolean before = ctx.computeOnClient(mc -> PrismClient.modules().get(StorageFinder.class).isEnabled());
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			boolean after = ctx.computeOnClient(mc -> PrismClient.modules().get(StorageFinder.class).isEnabled());
			log.add((before != after ? "PASS" : "FAIL") + "  clicking the row toggles StorageFinder (" + before + " -> " + after + ")");
			// the dots open the settings without toggling
			ctx.getInput().setCursorPos((panelX + w - 8) * s, rowY * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(20);
			boolean still = ctx.computeOnClient(mc -> PrismClient.modules().get(StorageFinder.class).isEnabled());
			log.add((still == after ? "PASS" : "FAIL") + "  clicking the dots opens the settings and does not toggle");
			// chips: a click on the first chip (Chests) hides chests, a second click shows them again
			float chipX = panelX + 6 + 12, chipY = rp[1] + 20 + 2 + 20 + 2 + 14 + 3 + 6;
			ctx.getInput().setCursorPos(chipX * s, chipY * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(5);
			boolean chestsOff = ctx.computeOnClient(mc -> !PrismClient.modules().get(StorageFinder.class).isOn(StorageFinder.Kind.CHEST));
			ctx.getInput().pressMouse(1);
			ctx.waitTicks(15);
			ctx.takeScreenshot("gui_chips_picker");
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(5);
			boolean chestsOn = ctx.computeOnClient(mc -> PrismClient.modules().get(StorageFinder.class).isOn(StorageFinder.Kind.CHEST));
			log.add((chestsOff && chestsOn ? "PASS" : "FAIL") + "  clicking the Chests chip hides and shows chests (" + chestsOff + ", " + chestsOn + ")");
			ctx.getInput().pressMouse(1);   // close the colour again
			ctx.waitTicks(10);
			// turn a few more on so the colour runs down the panels
			for (String n : new String[] {"OreFinder", "PlayerFinder", "Fullbright", "AutoSell"}) {
				ctx.runOnClient(mc -> PrismClient.modules().all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(true)));
			}
			// key chips on an enabled and a disabled row
			ctx.runOnClient(mc -> {
				PrismClient.modules().get(StorageFinder.class).setKey(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
				PrismClient.modules().get(com.wailsvlr.prism.modules.SpawnerFinder.class).setKey(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
			});
			ctx.getInput().setCursorPos(10, 1070);
			ctx.waitTicks(20);
			ctx.takeScreenshot("gui_sidebar");
			// the category list closes a panel
			float navRow2 = ny + 20 + 4 + 20 + 10;   // the second category (World)
			ctx.getInput().setCursorPos(nx * s, navRow2 * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(20);
			ctx.takeScreenshot("gui_sidebar_world_closed");
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			// profiles: open the panel from the category list, save a new profile, change something, load it back
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Profiles.delete("Profile"));
			ctx.getInput().setCursorPos(nx * s, (ny + 20 + 4 + 5 * 20 + 4 + 14 + 10) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(15);
			float[] pp = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.panelPosForTest("profiles") : new float[2]);
			ctx.getInput().setCursorPos((pp[0] + 6 + (w - 15) / 4) * s, (pp[1] + 20 + 4 + 7) * s);   // the CREATE NEW button
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			boolean saved = ctx.computeOnClient(mc -> com.wailsvlr.prism.util.Profiles.list().contains("Profile"));
			log.add((saved ? "PASS" : "FAIL") + "  '+ New profile' saved a profile file (" + ctx.computeOnClient(mc -> com.wailsvlr.prism.util.Profiles.list()) + ")");
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.OreFinder.class).setEnabled(false));
			ctx.getInput().setCursorPos((pp[0] + 40) * s, (pp[1] + 20 + 4 + 19 + 10) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			boolean back = ctx.computeOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.OreFinder.class).isEnabled());
			log.add((back ? "PASS" : "FAIL") + "  loading the profile turned OreFinder back on");
			ctx.takeScreenshot("gui_profiles");
			// tooltip by the pointer: rest on BaseHunter (first row of the World panel)
			float[] wp = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.catPosForTest(com.wailsvlr.prism.module.Category.WORLD) : new float[2]);
			ctx.getInput().setCursorPos((wp[0] + 40) * s, (wp[1] + 20 + 2 + 10) * s);
			ctx.waitTicks(20);
			ctx.takeScreenshot("gui_tooltip");
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Profiles.delete("Profile"));
			// friends: open the panel from the Misc section, type a name, add it, then remove it with a right click
			float misc = ny + 20 + 4 + 5 * 20 + 4 + 14;
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Friends.remove("Notch"));
			ctx.getInput().setCursorPos(nx * s, (misc + 20 + 10) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.setSearchForTest("Notch"); });
			float[] fp = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.panelPosForTest("friends") : new float[2]);
			ctx.getInput().setCursorPos((fp[0] + w / 2) * s, (fp[1] + 20 + 4 + 16 + 7) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(10);
			boolean added = ctx.computeOnClient(mc -> com.wailsvlr.prism.util.Friends.isFriend("Notch"));
			log.add((added ? "PASS" : "FAIL") + "  typing a name and clicking '+ Add' makes a friend");
			ctx.getInput().setCursorPos(10, 1070);
			ctx.waitTicks(15);
			ctx.takeScreenshot("gui_friends");
			ctx.getInput().setCursorPos((fp[0] + 40) * s, (fp[1] + 20 + 4 + 16 + 19 + 11) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(1);
			ctx.waitTicks(10);
			boolean removed = !ctx.computeOnClient(mc -> com.wailsvlr.prism.util.Friends.isFriend("Notch"));
			log.add((removed ? "PASS" : "FAIL") + "  right click on a friend removes them");
			// Edit HUD: drag the info box somewhere else
			ctx.getInput().setCursorPos(nx * s, (misc + 40 + 10) * s);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(20);
			boolean editor = ctx.computeOnClient(mc -> mc.screen instanceof com.wailsvlr.prism.gui.HudEditorScreen);
			log.add((editor ? "PASS" : "FAIL") + "  'Edit HUD' opens the HUD editor");
			float[] infoBefore = ctx.computeOnClient(mc -> com.wailsvlr.prism.gui.PrismHud.BOUNDS.get("info"));
			if (infoBefore != null) {
				ctx.getInput().setCursorPos((infoBefore[0] + 5) * s, (infoBefore[1] + 4) * s);
				ctx.waitTicks(3);
				ctx.getInput().holdMouse(0);
				ctx.waitTicks(3);
				for (int i = 1; i <= 10; i++) {
					ctx.getInput().setCursorPos((infoBefore[0] + 5 + i * 30) * s, (infoBefore[1] + 4 + i * 20) * s);
					ctx.waitTicks(2);
				}
				ctx.getInput().releaseMouse(0);
				ctx.waitTicks(10);
				float[] infoAfter = ctx.computeOnClient(mc -> com.wailsvlr.prism.gui.PrismHud.BOUNDS.get("info"));
				boolean moved = infoAfter != null && Math.abs(infoAfter[0] - infoBefore[0] - 300) < 6 && Math.abs(infoAfter[1] - infoBefore[1] - 200) < 6;
				log.add((moved ? "PASS" : "FAIL") + "  dragging the info box moves it (" + infoBefore[0] + "," + infoBefore[1] + " -> "
					+ (infoAfter == null ? "gone" : infoAfter[0] + "," + infoAfter[1]) + ")");
				ctx.takeScreenshot("hud_editor");
			} else log.add("FAIL  the info box was not drawn");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(10);
			boolean backToGui = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen);
			log.add((backToGui ? "PASS" : "FAIL") + "  Esc in the HUD editor goes back to the menu");
			// closing plays an animation first
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
			ctx.waitTicks(1);
			boolean stillOpen = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen);
			ctx.waitTicks(15);
			boolean closed = ctx.computeOnClient(mc -> mc.screen == null);
			log.add((stillOpen && closed ? "PASS" : "FAIL") + "  closing fades out first, then the menu is gone (" + stillOpen + ", " + closed + ")");
			ctx.setScreen(() -> null);
			// effects and armour for the Cards HUD
			for (String c : new String[]{"effect give @p speed 120 1", "effect give @p strength 45", "effect give @p night_vision 8",
				"item replace entity @p armor.head with diamond_helmet[damage=200]", "item replace entity @p armor.chest with netherite_chestplate",
				"item replace entity @p armor.legs with iron_leggings[damage=150]", "item replace entity @p armor.feet with diamond_boots",
				"item replace entity @p weapon.mainhand with diamond_sword[damage=900]", "item replace entity @p weapon.offhand with totem_of_undying 3"})
				sp.getServer().runCommand(c);
			ctx.runOnClient(mc -> {
				com.wailsvlr.prism.gui.Notifications.quiet = true;   // no toggle cards: the card test below counts them
				PrismClient.modules().get(com.wailsvlr.prism.modules.StorageFinder.class).setEnabled(true);
				PrismClient.modules().get(com.wailsvlr.prism.modules.SpawnerFinder.class).setEnabled(true);
				com.wailsvlr.prism.gui.Notifications.quiet = false;
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot("hud_text_list");
			// toggling by key (here: directly) shows a card in the corner; flicking twice keeps one card
			ctx.runOnClient(mc -> {
				var pf = PrismClient.modules().get(com.wailsvlr.prism.modules.PlayerFinder.class);
				pf.toggle();
				pf.toggle();
				PrismClient.modules().get(com.wailsvlr.prism.modules.Fullbright.class).setEnabled(false);
			});
			ctx.waitTicks(6);
			int cards = ctx.computeOnClient(mc -> com.wailsvlr.prism.gui.Notifications.count());
			log.add((cards == 2 ? "PASS" : "FAIL") + "  toggles show corner cards, one per module (" + cards + ")");
			ctx.takeScreenshot("hud_notifications");
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Profiles.save("Quiet"));
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.Fullbright.class).setEnabled(true));
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Profiles.load("Quiet"));
			int quiet = ctx.computeOnClient(mc -> com.wailsvlr.prism.gui.Notifications.count());
			log.add((quiet == 0 ? "PASS" : "FAIL") + "  loading a profile does not flood the corner (" + quiet + ")");
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.Profiles.delete("Quiet"));
			// searching for a setting opens the modules that have it, and closes them again when the search is cleared
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.setSearchForTest("opacity"); });
			ctx.waitTicks(20);
			int opened = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.searchOpenedForTest() : -1);
			ctx.getInput().setCursorPos(10, 1070);
			ctx.waitTicks(5);
			ctx.takeScreenshot("gui_search_settings");
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.setSearchForTest(""); });
			ctx.waitTicks(10);
			int left = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.searchOpenedForTest() : -1);
			log.add((opened >= 3 && left == 0 ? "PASS" : "FAIL") + "  searching 'opacity' opens the modules with that setting, clearing closes them (" + opened + ", " + left + ")");
			ctx.setScreen(() -> null);
			ctx.waitTicks(10);
			// a smaller window: the panels wrap into rows instead of running off the side
			ctx.getInput().resizeWindow(1280, 720);
			ctx.waitTicks(10);
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(40);
			boolean fits = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui && gui.panelsFitForTest());
			log.add((fits ? "PASS" : "FAIL") + "  at 1280x720 (GUI scale 2) every panel starts inside the window");
			// a click in the Render panel brings it in front of the Combat panel under it
			float[] rp2 = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.catPosForTest(com.wailsvlr.prism.module.Category.RENDER) : new float[2]);
			ctx.getInput().setCursorPos((rp2[0] + w - 8) * s, (rp2[1] + 20 + 2 + 10) * s);
			ctx.waitTicks(3);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(15);
			var top = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.topPanelForTest() : null);
			log.add((top == com.wailsvlr.prism.module.Category.RENDER ? "PASS" : "FAIL") + "  the clicked panel comes to the front (" + top + ")");
			ctx.getInput().setCursorPos(5, 715);
			ctx.waitTicks(5);
			ctx.takeScreenshot("gui_small_window");
			ctx.setScreen(() -> null);
			// the settings controls up close: ClickGUI's own settings with the accent picker open
			ctx.getInput().resizeWindow(1920, 1080);
			ctx.waitTicks(10);
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				ClickGuiModule cg = PrismClient.modules().get(ClickGuiModule.class);
				if (mc.screen instanceof ClickGuiScreen gui) gui.expandForTest(cg, cg.accent);
			});
			ctx.getInput().setCursorPos(10, 1070);
			ctx.waitTicks(30);
			ctx.takeScreenshot("gui_settings_controls");
			ctx.setScreen(() -> null);
		}
		try {
			Files.write(Path.of("gui-test-report.txt"), log);
		} catch (Exception ignored) { }
	}
}
