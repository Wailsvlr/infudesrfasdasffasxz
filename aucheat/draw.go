package main

import (
	"fmt"
	"math"
	"runtime"
	"syscall"
	"time"
	"unsafe"
)

type Overlay struct {
	hwnd    syscall.Handle
	game    *GameReader
	cfg     *Config
	running bool

	menuOpen bool

	fontBig   syscall.Handle
	fontSmall syscall.Handle
	bgBrush   syscall.Handle

	gameHwnd syscall.Handle
	gameRect RECT
	clientW  int32
	clientH  int32

	// per-key debounce — true while held
	held [256]bool
}

func NewOverlay(game *GameReader, cfg *Config) *Overlay {
	return &Overlay{
		game:     game,
		cfg:      cfg,
		menuOpen: true,
		running:  true,
	}
}

var gOverlay *Overlay

// keyPressed returns true once per press (rising edge)
func (o *Overlay) keyPressed(vk int) bool {
	down := isKeyDown(vk)
	if down && !o.held[vk] {
		o.held[vk] = true
		return true
	}
	if !down {
		o.held[vk] = false
	}
	return false
}

func (o *Overlay) Run() {
	runtime.LockOSThread()
	gOverlay = o

	hInst, _, _ := pGetModuleHandleW.Call(0)
	className := utf16Ptr("SysMonRender_7f3a")
	cursor, _, _ := pLoadCursorW.Call(0, uintptr(IDC_ARROW))
	o.bgBrush = syscall.Handle(createSolidBrush(COLOR_KEY_RGB))

	wc := WNDCLASSEXW{
		Style:      CS_HREDRAW | CS_VREDRAW,
		WndProc:    syscall.NewCallback(wndProc),
		Instance:   syscall.Handle(hInst),
		Cursor:     syscall.Handle(cursor),
		Background: o.bgBrush,
		ClassName:  className,
	}
	wc.Size = uint32(unsafe.Sizeof(wc))
	pRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc)))

	screenW, _, _ := pGetSystemMetrics.Call(SM_CXSCREEN)
	screenH, _, _ := pGetSystemMetrics.Call(SM_CYSCREEN)

	hwnd, _, _ := pCreateWindowExW.Call(
		WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_TOPMOST|WS_EX_TOOLWINDOW,
		uintptr(unsafe.Pointer(className)),
		uintptr(unsafe.Pointer(utf16Ptr("SysMon"))),
		WS_POPUP|WS_VISIBLE,
		0, 0, screenW, screenH,
		0, 0, hInst, 0,
	)
	o.hwnd = syscall.Handle(hwnd)

	pSetLayeredWindowAttributes.Call(hwnd, uintptr(COLOR_KEY_RGB), 0, LWA_COLORKEY)
	pShowWindow.Call(hwnd, SW_SHOW)
	pSetTimer.Call(hwnd, 1, 33, 0) // ~30 FPS

	o.fontBig = createFont(18, FW_BOLD)
	o.fontSmall = createFont(13, FW_NORMAL)

	go func() {
		for o.running {
			o.game.Update()
			time.Sleep(time.Duration(o.cfg.UpdateRateMs) * time.Millisecond)
		}
	}()

	var msg MSG
	for o.running {
		r, _, _ := pPeekMessageW.Call(uintptr(unsafe.Pointer(&msg)), 0, 0, 0, PM_REMOVE)
		if r != 0 {
			if msg.Message == 0x0012 { // WM_QUIT
				break
			}
			pTranslateMessage.Call(uintptr(unsafe.Pointer(&msg)))
			pDispatchMessageW.Call(uintptr(unsafe.Pointer(&msg)))
		} else {
			time.Sleep(1 * time.Millisecond)
		}
	}
	o.running = false
	deleteObject(o.fontBig)
	deleteObject(o.fontSmall)
	deleteObject(syscall.Handle(o.bgBrush))
}

func wndProc(hwnd, msg, wParam, lParam uintptr) uintptr {
	o := gOverlay
	if o == nil {
		r, _, _ := pDefWindowProcW.Call(hwnd, msg, wParam, lParam)
		return r
	}
	switch msg {
	case WM_TIMER:
		o.handleInput()
		o.trackGameWindow()
		pInvalidateRect.Call(hwnd, 0, 1)
	case WM_PAINT:
		var ps PAINTSTRUCT
		hdc, _, _ := pBeginPaint.Call(hwnd, uintptr(unsafe.Pointer(&ps)))
		if hdc != 0 {
			o.paint(syscall.Handle(hdc))
		}
		pEndPaint.Call(hwnd, uintptr(unsafe.Pointer(&ps)))
	case WM_DESTROY:
		o.running = false
		pPostQuitMessage.Call(0)
	default:
		r, _, _ := pDefWindowProcW.Call(hwnd, msg, wParam, lParam)
		return r
	}
	return 0
}

// ── input — F1-F5, all with rising-edge debounce ────────────────────────────

const (
	VK_F1 = 0x70
	VK_F2 = 0x71
	VK_F3 = 0x72
	VK_F4 = 0x73
	VK_F5 = 0x74
	VK_F6 = 0x75
)

func (o *Overlay) handleInput() {
	if o.keyPressed(VK_F1) {
		o.menuOpen = !o.menuOpen
	}
	if o.keyPressed(VK_F2) {
		o.cfg.ESPEnabled = !o.cfg.ESPEnabled
	}
	if o.keyPressed(VK_F3) {
		o.cfg.AlertsEnabled = !o.cfg.AlertsEnabled
	}
	if o.keyPressed(VK_F4) {
		o.cfg.MinimapEnabled = !o.cfg.MinimapEnabled
	}
	if o.keyPressed(VK_F6) {
		o.cfg.RoleListEnabled = !o.cfg.RoleListEnabled
	}
	if o.keyPressed(VK_F5) {
		o.running = false
		pPostQuitMessage.Call(0)
	}
}

// ── game window tracking ────────────────────────────────────────────────────

func (o *Overlay) trackGameWindow() {
	if o.gameHwnd == 0 {
		o.gameHwnd = findWindowW(utf16Ptr("UnityWndClass"), utf16Ptr("Among Us"))
	}
	if o.gameHwnd == 0 {
		// fallback: fullscreen
		sw, _, _ := pGetSystemMetrics.Call(SM_CXSCREEN)
		sh, _, _ := pGetSystemMetrics.Call(SM_CYSCREEN)
		o.gameRect = RECT{Left: 0, Top: 0, Right: int32(sw), Bottom: int32(sh)}
		o.clientW = int32(sw)
		o.clientH = int32(sh)
		return
	}
	o.gameRect = getWindowRect(o.gameHwnd)
	cr := getClientRect(o.gameHwnd)
	o.clientW = cr.Right
	o.clientH = cr.Bottom
}

// ── paint ───────────────────────────────────────────────────────────────────

func (o *Overlay) paint(hdc syscall.Handle) {
	var rc RECT
	pGetClientRect.Call(uintptr(o.hwnd), uintptr(unsafe.Pointer(&rc)))
	pFillRect.Call(uintptr(hdc), uintptr(unsafe.Pointer(&rc)), uintptr(o.bgBrush))
	pSetBkMode.Call(uintptr(hdc), BK_TRANSPARENT)

	state := o.game.State()
	state.mu.RLock()
	players := make([]PlayerInfo, len(state.Players))
	copy(players, state.Players)
	inGame := state.InGame
	localX := state.LocalX
	localY := state.LocalY
	lastErr := state.LastError
	errCount := state.ErrCount
	state.mu.RUnlock()

	// ── status bar (always visible) ─────────────────────────────────────
	o.drawStatusBar(hdc, inGame, len(players), errCount, lastErr)

	// ── ESP ──────────────────────────────────────────────────────────────
	if inGame && o.cfg.ESPEnabled && len(players) > 0 {
		orthoSize := float32(3.0)
		aspect := float32(o.clientW) / float32(o.clientH)
		if aspect <= 0 {
			aspect = 16.0 / 9.0
		}
		scale := float32(o.clientH) / (2.0 * orthoSize)

		gcx := float32(o.gameRect.Left+o.gameRect.Right) / 2.0
		gcy := float32(o.gameRect.Top+o.gameRect.Bottom) / 2.0

		pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))

		for _, p := range players {
			if p.IsDisconnected {
				continue
			}

			sx := int32(gcx + (p.WorldX-localX)*scale)
			sy := int32(gcy - (p.WorldY-localY)*scale)

			if sx < o.gameRect.Left-80 || sx > o.gameRect.Right+80 ||
				sy < o.gameRect.Top-40 || sy > o.gameRect.Bottom+40 {
				continue
			}

			setTextColor(hdc, roleColor(p))

			dead := ""
			if p.IsDead {
				dead = " X"
			}

			label := fmt.Sprintf("%s [%s]%s", p.Name, p.RoleName, dead)
			drawText(hdc, sx-50, sy-22, label)

			pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
			drawText(hdc, sx-18, sy+2, fmt.Sprintf("%.1fm", p.Distance))
			pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
		}
	}

	// ── kill range alert ────────────────────────────────────────────────
	if inGame && o.cfg.AlertsEnabled {
		for _, p := range players {
			if p.IsImpostor && p.InKillRange && !p.IsDead {
				setTextColor(hdc, rgb(255, 0, 0))
				pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
				cx := (o.gameRect.Left + o.gameRect.Right) / 2
				drawText(hdc, cx-120, o.gameRect.Top+50, fmt.Sprintf("!! IMPOSTOR YAKININDA: %s (%.1fm) !!", p.Name, p.Distance))
			}
		}
	}

	// ── minimap ─────────────────────────────────────────────────────────
	if inGame && o.cfg.MinimapEnabled && len(players) > 0 {
		o.drawMinimap(hdc, players, localX, localY)
	}

	// ── role list (independent of world positions) ──────────────────────
	if inGame && o.cfg.RoleListEnabled && len(players) > 0 {
		o.drawRoleList(hdc, players)
	}

	// ── menu ────────────────────────────────────────────────────────────
	if o.menuOpen {
		o.drawMenu(hdc, inGame, len(players))
	}
}

func (o *Overlay) drawStatusBar(hdc syscall.Handle, inGame bool, playerCount, errCount int, lastErr string) {
	bx := o.gameRect.Left + 5
	by := o.gameRect.Bottom - 22
	bw := int32(300)
	bh := int32(18)

	drawFilledRect(hdc, bx, by, bx+bw, by+bh, rgb(5, 5, 10))
	pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))

	if inGame {
		setTextColor(hdc, rgb(80, 255, 120))
		drawText(hdc, bx+4, by+1, fmt.Sprintf("AUCheat | Oyunda | %d oyuncu | F1=Menu", playerCount))
	} else if errCount > 0 && lastErr != "" {
		setTextColor(hdc, rgb(255, 100, 50))
		short := lastErr
		if len(short) > 40 {
			short = short[:40]
		}
		drawText(hdc, bx+4, by+1, fmt.Sprintf("AUCheat | HATA: %s", short))
	} else {
		setTextColor(hdc, rgb(255, 200, 50))
		drawText(hdc, bx+4, by+1, "AUCheat | Oyun bekleniyor... | F1=Menu")
	}
}

func (o *Overlay) drawMenu(hdc syscall.Handle, inGame bool, playerCount int) {
	mx := o.gameRect.Left + 10
	my := o.gameRect.Top + 10
	mw := int32(240)
	mh := int32(228)

	drawFilledRect(hdc, mx, my, mx+mw, my+mh, rgb(10, 10, 20))
	drawRect(hdc, mx, my, mx+mw, my+mh, rgb(80, 80, 140))

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
	setTextColor(hdc, rgb(255, 200, 50))
	drawText(hdc, mx+10, my+8, "AUCheat")

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
	y := my + 32

	// status
	if inGame {
		setTextColor(hdc, rgb(80, 255, 120))
		drawText(hdc, mx+10, y, fmt.Sprintf("Durum: Oyunda (%d oyuncu)", playerCount))
	} else {
		setTextColor(hdc, rgb(255, 100, 50))
		drawText(hdc, mx+10, y, "Durum: Offset bulunamadi / Oyun yok")
	}
	y += 22

	onoff := func(b bool) string {
		if b {
			return "ON"
		}
		return "OFF"
	}

	items := []struct {
		key, label string
		on         bool
	}{
		{"F2", "ESP (roller goster)", o.cfg.ESPEnabled},
		{"F3", "Kill Uyarisi", o.cfg.AlertsEnabled},
		{"F4", "Minimap", o.cfg.MinimapEnabled},
		{"F6", "Rol listesi", o.cfg.RoleListEnabled},
	}

	for _, l := range items {
		if l.on {
			setTextColor(hdc, rgb(100, 255, 130))
		} else {
			setTextColor(hdc, rgb(100, 100, 100))
		}
		drawText(hdc, mx+10, y, fmt.Sprintf("[%s] %s: %s", l.key, l.label, onoff(l.on)))
		y += 18
	}

	y += 6
	setTextColor(hdc, rgb(180, 180, 180))
	drawText(hdc, mx+10, y, "F1 = Menu goster/gizle")
	y += 16
	drawText(hdc, mx+10, y, "F5 = Kapat")
	y += 20
	setTextColor(hdc, rgb(100, 180, 100))
	drawText(hdc, mx+10, y, "* sadece okuma — ban riski yok *")
}

func roleColor(p PlayerInfo) uint32 {
	switch {
	case p.IsDead:
		return rgb(130, 130, 130)
	case !p.RoleKnown || p.Team == TeamUnknown:
		return rgb(255, 210, 40)
	case p.Team == TeamImpostor:
		return rgb(255, 30, 30)
	default:
		return rgb(30, 255, 90)
	}
}

// drawRoleList prints every connected player's role with the raw enum value in
// parentheses, so the role table can be checked against a dump at a glance.
func (o *Overlay) drawRoleList(hdc syscall.Handle, players []PlayerInfo) {
	const rowH = int32(16)
	pw := int32(250)
	px := o.gameRect.Right - pw - 10
	py := o.gameRect.Top + 196

	rows := 0
	for _, p := range players {
		if !p.IsDisconnected {
			rows++
		}
	}
	ph := int32(rows)*rowH + 26

	drawFilledRect(hdc, px, py, px+pw, py+ph, rgb(5, 5, 10))
	drawRect(hdc, px, py, px+pw, py+ph, rgb(80, 80, 120))

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
	setTextColor(hdc, rgb(180, 180, 180))
	drawText(hdc, px+6, py+4, "ROLLER")

	y := py + 22
	for _, p := range players {
		if p.IsDisconnected {
			continue
		}
		setTextColor(hdc, roleColor(p))
		raw := "?"
		if p.RoleKnown {
			raw = fmt.Sprintf("%d", p.RoleRaw)
		}
		dead := ""
		if p.IsDead {
			dead = " X"
		}
		drawText(hdc, px+6, y, fmt.Sprintf("%-12.12s %s (%s)%s", p.Name, p.RoleName, raw, dead))
		y += rowH
	}
}

func (o *Overlay) drawMinimap(hdc syscall.Handle, players []PlayerInfo, lx, ly float32) {
	mmX := o.gameRect.Right - 170
	mmY := o.gameRect.Top + 10
	mmW := int32(160)
	mmH := int32(160)
	mmScale := float32(8.0)

	drawFilledRect(hdc, mmX, mmY, mmX+mmW, mmY+mmH, rgb(5, 5, 10))
	drawRect(hdc, mmX, mmY, mmX+mmW, mmY+mmH, rgb(80, 80, 120))

	for _, p := range players {
		if p.IsDisconnected || p.IsDead {
			continue
		}
		dx := (p.WorldX - lx) / mmScale
		dy := -(p.WorldY - ly) / mmScale
		px := mmX + mmW/2 + int32(dx*float32(mmW)/2)
		py := mmY + mmH/2 + int32(dy*float32(mmH)/2)
		px = clampI32(px, mmX+2, mmX+mmW-2)
		py = clampI32(py, mmY+2, mmY+mmH-2)

		col := rgb(30, 255, 90)
		if p.IsImpostor {
			col = rgb(255, 30, 30)
		}
		drawFilledRect(hdc, px-3, py-3, px+3, py+3, col)
	}

	drawFilledRect(hdc, mmX+mmW/2-2, mmY+mmH/2-2, mmX+mmW/2+2, mmY+mmH/2+2, rgb(255, 255, 255))

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
	setTextColor(hdc, rgb(180, 180, 180))
	drawText(hdc, mmX+2, mmY+mmH+2, "MINIMAP")
}

// ── GDI helpers ─────────────────────────────────────────────────────────────

func createFont(size int32, weight int32) syscall.Handle {
	h, _, _ := pCreateFontW.Call(
		uintptr(size), 0, 0, 0, uintptr(weight),
		0, 0, 0,
		DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
		CLEARTYPE_QUALITY, VARIABLE_PITCH,
		uintptr(unsafe.Pointer(utf16Ptr("Consolas"))),
	)
	return syscall.Handle(h)
}

func createSolidBrush(color uint32) uintptr {
	r, _, _ := pCreateSolidBrush.Call(uintptr(color))
	return r
}

func setTextColor(hdc syscall.Handle, color uint32) {
	pSetTextColor.Call(uintptr(hdc), uintptr(color))
}

func drawText(hdc syscall.Handle, x, y int32, text string) {
	u16, _ := syscall.UTF16FromString(text)
	if len(u16) > 1 {
		pTextOutW.Call(uintptr(hdc), uintptr(x), uintptr(y),
			uintptr(unsafe.Pointer(&u16[0])), uintptr(len(u16)-1))
	}
}

func drawRect(hdc syscall.Handle, x1, y1, x2, y2 int32, color uint32) {
	pen, _, _ := pCreatePen.Call(PS_SOLID, 1, uintptr(color))
	nb := createSolidBrush(COLOR_KEY_RGB)
	old, _, _ := pSelectObject.Call(uintptr(hdc), pen)
	oldBr, _, _ := pSelectObject.Call(uintptr(hdc), nb)
	pRectangleW.Call(uintptr(hdc), uintptr(x1), uintptr(y1), uintptr(x2), uintptr(y2))
	pSelectObject.Call(uintptr(hdc), old)
	pSelectObject.Call(uintptr(hdc), oldBr)
	deleteObject(syscall.Handle(pen))
	deleteObject(syscall.Handle(nb))
}

func drawFilledRect(hdc syscall.Handle, x1, y1, x2, y2 int32, color uint32) {
	br := createSolidBrush(color)
	r := RECT{Left: x1, Top: y1, Right: x2, Bottom: y2}
	pFillRect.Call(uintptr(hdc), uintptr(unsafe.Pointer(&r)), br)
	deleteObject(syscall.Handle(br))
}

func deleteObject(h syscall.Handle) {
	pDeleteObject.Call(uintptr(h))
}

func clampI32(v, lo, hi int32) int32 {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

var _ = math.Pi
