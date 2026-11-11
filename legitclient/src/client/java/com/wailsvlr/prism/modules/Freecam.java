package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera from the player and lets it fly freely (through blocks, like spectator mode).
 * Purely client-side: the player stays where it is, its movement input is blocked and mouse movement turns the
 * camera instead of the player, so the server never sees a different position or rotation.
 * The hooks live in the mixins (CameraMixin, EntityMixin, KeyboardInputMixin, GameRendererMixin, MinecraftMixin).
 */
public class Freecam extends Module {
	private static Freecam instance;

	private final NumberSetting speed, sprintMultiplier, verticalSpeed, smoothness;
	private final ModeSetting flyMode;
	private final BoolSetting allowInteract, disableOnDeath, disableOnDamage, rotateOnExit;

	private Vec3 pos = Vec3.ZERO, prevPos = Vec3.ZERO, velocity = Vec3.ZERO;
	private float yaw, pitch;
	private ClientLevel level;
	private float lastHealth;

	public Freecam() {
		super("Freecam", "Fly the camera around freely (through blocks). Only your view moves: the server keeps seeing you standing still", Category.RENDER);
		instance = this;
		add(new SectionSetting("Movement"));
		speed = add(new NumberSetting("Speed", "Flying speed", 12, 1, 60, 0.5, " b/s"));
		sprintMultiplier = add(new NumberSetting("Sprint Multiplier", "Speed multiplier while holding the sprint key", 2.5, 1, 6, 0.25, "x"));
		verticalSpeed = add(new NumberSetting("Vertical Speed", "Speed of Jump (up) and Sneak (down), relative to Speed", 1.0, 0.2, 2, 0.1, "x"));
		flyMode = add(new ModeSetting("Fly Mode", "Camera: W flies where you look. Horizontal: W stays level, Jump/Sneak go up and down", "Camera", "Camera", "Horizontal"));
		smoothness = add(new NumberSetting("Smoothness", "How gently the camera speeds up and slows down", 0.55, 0, 0.95, 0.05, ""));
		add(new SectionSetting("Behaviour"));
		allowInteract = add(new BoolSetting("Allow Interaction", "Let clicks still break / use blocks at your real player's crosshair", false));
		disableOnDeath = add(new BoolSetting("Disable On Death", "Turn freecam off when you die", true));
		disableOnDamage = add(new BoolSetting("Disable On Damage", "Turn freecam off when your player takes damage", false));
		rotateOnExit = add(new BoolSetting("Rotate On Exit", "Turn your player to face where the camera was looking when you leave freecam", false));
	}

	/** The freecam module when it is on, otherwise null (used by the mixins every frame). */
	public static Freecam active() {
		return instance != null && instance.isEnabled() && mc.player != null ? instance : null;
	}

	@Override
	protected void onEnable() {
		if (mc.player == null || mc.level == null) { setEnabled(false); return; }
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		pos = prevPos = cam.equals(Vec3.ZERO) ? mc.player.getEyePosition() : cam;
		velocity = Vec3.ZERO;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		level = mc.level;
		lastHealth = mc.player.getHealth();
	}

	@Override
	protected void onDisable() {
		if (rotateOnExit.isOn() && mc.player != null) {
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
		}
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level != level) { setEnabled(false); return; }
		if (disableOnDeath.isOn() && mc.player.isDeadOrDying()) { setEnabled(false); return; }
		float hp = mc.player.getHealth();
		if (disableOnDamage.isOn() && hp < lastHealth) { setEnabled(false); return; }
		lastHealth = hp;

		double fwd = 0, strafe = 0, up = 0;
		if (mc.screen == null) {
			if (mc.options.keyUp.isDown()) fwd += 1;
			if (mc.options.keyDown.isDown()) fwd -= 1;
			if (mc.options.keyLeft.isDown()) strafe += 1;
			if (mc.options.keyRight.isDown()) strafe -= 1;
			if (mc.options.keyJump.isDown()) up += 1;
			if (mc.options.keyShift.isDown()) up -= 1;
		}
		double perTick = speed.get() / 20.0;
		if (mc.screen == null && mc.options.keySprint.isDown()) perTick *= sprintMultiplier.get();

		float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
		Vec3 forward = flyMode.is("Camera")
			? new Vec3(-Mth.sin(yr) * Mth.cos(pr), -Mth.sin(pr), Mth.cos(yr) * Mth.cos(pr))
			: new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
		Vec3 left = new Vec3(Mth.cos(yr), 0, Mth.sin(yr));
		Vec3 move = forward.scale(fwd).add(left.scale(strafe));
		if (move.lengthSqr() > 1) move = move.normalize();
		Vec3 target = move.scale(perTick).add(0, up * perTick * verticalSpeed.get(), 0);

		double k = 1 - smoothness.get();
		velocity = velocity.add(target.subtract(velocity).scale(Math.max(0.05, k)));
		if (target.lengthSqr() == 0 && velocity.lengthSqr() < 1e-6) velocity = Vec3.ZERO;
		prevPos = pos;
		pos = pos.add(velocity);
	}

	/** Mouse look (called instead of turning the player). Same sensitivity as vanilla. */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
	}

	public Vec3 position(float partialTick) {
		return new Vec3(Mth.lerp(partialTick, prevPos.x, pos.x), Mth.lerp(partialTick, prevPos.y, pos.y), Mth.lerp(partialTick, prevPos.z, pos.z));
	}

	/** Puts the camera at a position and rotation (no smoothing). */
	public void moveTo(Vec3 p, float yaw, float pitch) {
		pos = prevPos = p;
		velocity = Vec3.ZERO;
		this.yaw = yaw;
		this.pitch = Mth.clamp(pitch, -90f, 90f);
	}

	public float yaw() { return yaw; }
	public float pitch() { return pitch; }
	public boolean allowsInteraction() { return allowInteract.isOn(); }

	@Override
	public String getInfo() {
		if (mc.player == null) return null;
		return Math.round(pos.distanceTo(mc.player.getEyePosition())) + "m";
	}
}
