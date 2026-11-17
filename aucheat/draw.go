package main

import (
	"fmt"
	"math"
	"runtime"
	"syscall"
	"time"
	"unsafe"
)

// ── overlay ──────────────────────────────────────────────────────────────────

type Overlay struct {
	hwnd     syscall.Handle
	game     *GameReader
	cfg      *Config

	menuOpen bool
	running  bool

	// GDI resources
	fontBig   syscall.Handle
	fontSmall syscall.Handle
	bgBrush   syscall.Handle

	// game window tracking
	gameHwnd  syscall.Handle
	gameRect  RECT
	clientW   int32
	clientH   int32

	// hotkey debounce
	hotkeyHeld bool
	insHeld    bool
}

func NewOverlay(game *GameReader, cfg *Config) *Overlay {
	return &Overlay{
		game:    game,
		cfg:     cfg,
		menuOpen: true,
		running:  true,
	}
}

var overlayInstance *Overlay

func (o *Overlay) Run() {
	runtime.LockOSThread()
	overlayInstance = o

	hInst, _, _ := pGetModuleHandleW.Call(0)

	// random-ish class name — avoids detection by window class scanning
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

	// full-screen overlay
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

	// color-key transparency — COLOR_KEY_RGB becomes see-through
	pSetLayeredWindowAttributes.Call(hwnd, uintptr(COLOR_KEY_RGB), 0, LWA_COLORKEY)
	pShowWindow.Call(hwnd, SW_SHOW)

	// 33ms timer ≈ 30 FPS redraw
	pSetTimer.Call(hwnd, 1, 33, 0)

	// GDI fonts
	o.fontBig = createFont(16, FW_BOLD)
	o.fontSmall = createFont(12, FW_NORMAL)

	// game reader goroutine
	go func() {
		for o.running {
			o.game.Update()
			time.Sleep(time.Duration(o.cfg.UpdateRateMs) * time.Millisecond)
		}
	}()

	// message pump
	var msg MSG
	for {
		r, _, _ := pPeekMessageW.Call(
			uintptr(unsafe.Pointer(&msg)), 0, 0, 0, PM_REMOVE,
		)
		if r != 0 {
			if msg.Message == WM_CLOSE || msg.Message == 0x0012 { // WM_QUIT
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
	o := overlayInstance
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
		pPostQuitMessage.Call(0)

	default:
		r, _, _ := pDefWindowProcW.Call(hwnd, msg, wParam, lParam)
		return r
	}
	return 0
}

// ── input handling ───────────────────────────────────────────────────────────

func (o *Overlay) handleInput() {
	// Shift + Right Click → toggle menu
	if isKeyDown(VK_SHIFT) && isKeyDown(VK_RBUTTON) {
		if !o.hotkeyHeld {
			o.menuOpen = !o.menuOpen
			o.hotkeyHeld = true
		}
	} else {
		o.hotkeyHeld = false
	}

	// Insert → toggle ESP
	if isKeyDown(VK_INSERT) {
		if !o.insHeld {
			o.cfg.ESPEnabled = !o.cfg.ESPEnabled
			o.insHeld = true
		}
	} else {
		o.insHeld = false
	}

	// Home → toggle alerts
	if isKeyDown(VK_HOME) {
		o.cfg.AlertsEnabled = !o.cfg.AlertsEnabled
	}

	// End → toggle minimap
	if isKeyDown(VK_END) {
		o.cfg.MinimapEnabled = !o.cfg.MinimapEnabled
	}

	// Delete → quit
	if isKeyDown(VK_DELETE) && isKeyDown(VK_SHIFT) {
		o.running = false
		pPostQuitMessage.Call(0)
	}
}

// ── game window tracking ─────────────────────────────────────────────────────

func (o *Overlay) trackGameWindow() {
	if o.gameHwnd == 0 {
		o.gameHwnd = findWindowW(utf16Ptr("UnityWndClass"), utf16Ptr("Among Us"))
	}
	if o.gameHwnd == 0 {
		return
	}
	o.gameRect = getWindowRect(o.gameHwnd)
	cr := getClientRect(o.gameHwnd)
	o.clientW = cr.Right
	o.clientH = cr.Bottom
}

// ── rendering ────────────────────────────────────────────────────────────────

func (o *Overlay) paint(hdc syscall.Handle) {
	// fill with transparent color
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
	state.mu.RUnlock()

	if !inGame || o.gameHwnd == 0 {
		pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
		setTextColor(hdc, rgb(255, 200, 50))
		drawText(hdc, 20, 20, "[AUCheat] Among Us oyunda degil / baglanti bekleniyor...")
		o.drawMenu(hdc)
		return
	}

	// world-to-screen helper using game window coords
	// Among Us camera: orthographic, orthoSize ≈ 3.0, centered on local player
	orthoSize := float32(3.0)
	aspect := float32(o.clientW) / float32(o.clientH)
	if aspect == 0 {
		aspect = 16.0 / 9.0
	}

	w2s := func(wx, wy float32) (int32, int32) {
		// screen origin → game window top-left
		sx := (wx - localX) / (orthoSize * aspect) * float32(o.clientW) / 2
		sy := -(wy - localY) / orthoSize * float32(o.clientH) / 2

		// add game window offset on screen
		pt := POINT{X: o.gameRect.Left, Y: o.gameRect.Top}
		// convert client center to screen
		cx := float32(o.gameRect.Left) + float32(o.clientW)/2 + sx
		cy := float32(o.gameRect.Top) + float32(o.clientH)/2 + sy
		_ = pt
		return int32(cx), int32(cy)
	}

	// ── ESP ──────────────────────────────────────────────────────────────
	if o.cfg.ESPEnabled {
		pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))

		for _, p := range players {
			if p.IsDisconnected {
				continue
			}

			sx, sy := w2s(p.WorldX, p.WorldY)

			// off-screen check
			if sx < o.gameRect.Left-50 || sx > o.gameRect.Right+50 ||
				sy < o.gameRect.Top-50 || sy > o.gameRect.Bottom+50 {
				continue
			}

			var col uint32
			switch {
			case p.IsDead:
				col = rgb(120, 120, 120)
			case p.IsImpostor:
				col = rgb(255, 40, 40)
			default:
				col = rgb(40, 255, 100)
			}
			setTextColor(hdc, col)

			tag := "CREW"
			if p.IsImpostor {
				tag = "IMP"
			}
			dead := ""
			if p.IsDead {
				dead = " X"
			}

			label := fmt.Sprintf("%s [%s]%s", p.Name, tag, dead)
			drawText(hdc, sx-40, sy-20, label)

			// distance
			pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
			dist := fmt.Sprintf("%.1fm", p.Distance)
			drawText(hdc, sx-15, sy+2, dist)
			pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
		}
	}

	// ── kill range alert ─────────────────────────────────────────────────
	if o.cfg.AlertsEnabled {
		for _, p := range players {
			if p.IsImpostor && p.InKillRange && !p.IsDead {
				setTextColor(hdc, rgb(255, 0, 0))
				pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))

				cx := (o.gameRect.Left + o.gameRect.Right) / 2
				alertY := o.gameRect.Top + 30
				drawText(hdc, cx-100, alertY, fmt.Sprintf("⚠ IMPOSTOR YAKININDA: %s (%.1fm)", p.Name, p.Distance))
			}
		}
	}

	// ── minimap ──────────────────────────────────────────────────────────
	if o.cfg.MinimapEnabled {
		mmX := o.gameRect.Right - 170
		mmY := o.gameRect.Top + 10
		mmW := int32(160)
		mmH := int32(160)
		mmScale := float32(8.0) // world units visible in minimap

		// background
		drawFilledRect(hdc, mmX, mmY, mmX+mmW, mmY+mmH, rgb(0, 0, 0))
		drawRect(hdc, mmX, mmY, mmX+mmW, mmY+mmH, rgb(100, 100, 100))

		pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))

		for _, p := range players {
			if p.IsDisconnected || p.IsDead {
				continue
			}

			dx := (p.WorldX - localX) / mmScale
			dy := -(p.WorldY - localY) / mmScale

			px := mmX + mmW/2 + int32(dx*float32(mmW)/2)
			py := mmY + mmH/2 + int32(dy*float32(mmH)/2)

			if px < mmX || px > mmX+mmW || py < mmY || py > mmY+mmH {
				// clamp to edge
				px = clampI32(px, mmX+2, mmX+mmW-2)
				py = clampI32(py, mmY+2, mmY+mmH-2)
			}

			col := rgb(40, 255, 100)
			if p.IsImpostor {
				col = rgb(255, 40, 40)
			}

			drawFilledRect(hdc, px-3, py-3, px+3, py+3, col)
		}

		// local player (white dot center)
		drawFilledRect(hdc, mmX+mmW/2-2, mmY+mmH/2-2, mmX+mmW/2+2, mmY+mmH/2+2, rgb(255, 255, 255))

		setTextColor(hdc, rgb(200, 200, 200))
		drawText(hdc, mmX+2, mmY+mmH+2, "MINIMAP")
	}

	// ── menu (always last — on top) ──────────────────────────────────────
	o.drawMenu(hdc)
}

func (o *Overlay) drawMenu(hdc syscall.Handle) {
	if !o.menuOpen {
		return
	}

	mx := o.gameRect.Left + 10
	my := o.gameRect.Top + 10
	mw := int32(220)
	mh := int32(170)

	drawFilledRect(hdc, mx, my, mx+mw, my+mh, rgb(10, 10, 15))
	drawRect(hdc, mx, my, mx+mw, my+mh, rgb(80, 80, 120))

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontBig))
	setTextColor(hdc, rgb(255, 200, 50))
	drawText(hdc, mx+10, my+8, "AUCheat — PORTOLAN")

	pSelectObject.Call(uintptr(hdc), uintptr(o.fontSmall))
	y := my + 32

	onoff := func(b bool) string {
		if b { return "ON" }
		return "OFF"
	}

	lines := []struct{ key, label string; on bool }{
		{"INS", "ESP (roller)", o.cfg.ESPEnabled},
		{"HOME", "Kill Uyarisi", o.cfg.AlertsEnabled},
		{"END", "Minimap", o.cfg.MinimapEnabled},
	}

	for _, l := range lines {
		col := rgb(100, 100, 100)
		if l.on {
			col = rgb(100, 255, 130)
		}
		setTextColor(hdc, col)
		drawText(hdc, mx+10, y, fmt.Sprintf("[%s] %s: %s", l.key, l.label, onoff(l.on)))
		y += 18
	}

	setTextColor(hdc, rgb(180, 180, 180))
	drawText(hdc, mx+10, y+8, "Shift+Sag Tik = Menu")
	drawText(hdc, mx+10, y+24, "Shift+DEL    = Kapat")
	drawText(hdc, mx+10, y+42, "* sadece okuma — ban riski yok *")
}

// ── GDI helpers ──────────────────────────────────────────────────────────────

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
	s := utf16Ptr(text)
	n := 0
	for p := s; *(*uint16)(unsafe.Pointer(uintptr(unsafe.Pointer(p)) + uintptr(n)*2)) != 0; n++ {}
	pTextOutW.Call(uintptr(hdc), uintptr(x), uintptr(y), uintptr(unsafe.Pointer(s)), uintptr(n))
}

func drawRect(hdc syscall.Handle, x1, y1, x2, y2 int32, color uint32) {
	pen, _, _ := pCreatePen.Call(PS_SOLID, 1, uintptr(color))
	nullBrush := createSolidBrush(COLOR_KEY_RGB)
	old, _, _ := pSelectObject.Call(uintptr(hdc), pen)
	oldBr, _, _ := pSelectObject.Call(uintptr(hdc), nullBrush)
	pRectangleW.Call(uintptr(hdc), uintptr(x1), uintptr(y1), uintptr(x2), uintptr(y2))
	pSelectObject.Call(uintptr(hdc), old)
	pSelectObject.Call(uintptr(hdc), oldBr)
	deleteObject(syscall.Handle(pen))
	deleteObject(syscall.Handle(nullBrush))
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
	if v < lo { return lo }
	if v > hi { return hi }
	return v
}

// suppress unused import
var _ = math.Pi
