package main

import (
	"fmt"
	"syscall"
	"unsafe"
)

// ── constants ────────────────────────────────────────────────────────────────

const (
	PROCESS_VM_READ           = 0x0010
	PROCESS_QUERY_INFORMATION = 0x0400

	TH32CS_SNAPPROCESS  = 0x00000002
	TH32CS_SNAPMODULE   = 0x00000008
	TH32CS_SNAPMODULE32 = 0x00000010

	MAX_MODULE_NAME32 = 255
	MAX_PATH          = 260

	WS_EX_LAYERED    = 0x00080000
	WS_EX_TRANSPARENT = 0x00000020
	WS_EX_TOPMOST     = 0x00000008
	WS_EX_TOOLWINDOW  = 0x00000080
	WS_POPUP           = 0x80000000
	WS_VISIBLE         = 0x10000000

	WM_DESTROY = 0x0002
	WM_PAINT   = 0x000F
	WM_TIMER   = 0x0113
	WM_CLOSE   = 0x0010

	SW_SHOW = 5

	LWA_COLORKEY = 0x00000001

	SWP_SHOWWINDOW = 0x0040

	SM_CXSCREEN = 0
	SM_CYSCREEN = 1

	BK_TRANSPARENT = 1

	VK_SHIFT   = 0x10
	VK_RBUTTON = 0x02
	VK_INSERT  = 0x2D
	VK_HOME    = 0x24
	VK_END     = 0x23
	VK_DELETE  = 0x2E

	PM_REMOVE = 0x0001

	CS_VREDRAW = 0x0001
	CS_HREDRAW = 0x0002

	IDC_ARROW = 32512

	PS_SOLID = 0
	PS_DASH  = 1

	SRCCOPY = 0x00CC0020

	FW_BOLD   = 700
	FW_NORMAL = 400

	DEFAULT_CHARSET = 1
	OUT_DEFAULT_PRECIS = 0
	CLIP_DEFAULT_PRECIS = 0
	CLEARTYPE_QUALITY = 5
	VARIABLE_PITCH = 2

	COLOR_KEY_RGB = 0x00010101 // RGB(1,1,1) — this exact color becomes transparent
)

// ── structs ──────────────────────────────────────────────────────────────────

type PROCESSENTRY32W struct {
	Size            uint32
	Usage           uint32
	ProcessID       uint32
	DefaultHeapID   uintptr
	ModuleID        uint32
	Threads         uint32
	ParentProcessID uint32
	PriClassBase    int32
	Flags           uint32
	ExeFile         [MAX_PATH]uint16
}

type MODULEENTRY32W struct {
	Size         uint32
	ModuleID     uint32
	ProcessID    uint32
	GlblcntUsage uint32
	ProccntUsage uint32
	ModBaseAddr  uintptr
	ModBaseSize  uint32
	HModule      syscall.Handle
	ModuleName   [MAX_MODULE_NAME32 + 1]uint16
	ExePath      [MAX_PATH]uint16
}

type WNDCLASSEXW struct {
	Size       uint32
	Style      uint32
	WndProc    uintptr
	ClsExtra   int32
	WndExtra   int32
	Instance   syscall.Handle
	Icon       syscall.Handle
	Cursor     syscall.Handle
	Background syscall.Handle
	MenuName   *uint16
	ClassName  *uint16
	IconSm     syscall.Handle
}

type MSG struct {
	Hwnd    syscall.Handle
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	Pt      POINT
}

type POINT struct {
	X, Y int32
}

type RECT struct {
	Left, Top, Right, Bottom int32
}

type PAINTSTRUCT struct {
	HDC         syscall.Handle
	Erase       int32
	RcPaint     RECT
	Restore     int32
	IncUpdate   int32
	RgbReserved [32]byte
}

// ── DLL procs ────────────────────────────────────────────────────────────────

var (
	kernel32 = syscall.NewLazyDLL("kernel32.dll")
	user32   = syscall.NewLazyDLL("user32.dll")
	gdi32    = syscall.NewLazyDLL("gdi32.dll")

	pOpenProcess               = kernel32.NewProc("OpenProcess")
	pReadProcessMemory         = kernel32.NewProc("ReadProcessMemory")
	pCloseHandle               = kernel32.NewProc("CloseHandle")
	pCreateToolhelp32Snapshot  = kernel32.NewProc("CreateToolhelp32Snapshot")
	pProcess32FirstW           = kernel32.NewProc("Process32FirstW")
	pProcess32NextW            = kernel32.NewProc("Process32NextW")
	pModule32FirstW            = kernel32.NewProc("Module32FirstW")
	pModule32NextW             = kernel32.NewProc("Module32NextW")
	pGetModuleHandleW          = kernel32.NewProc("GetModuleHandleW")

	pRegisterClassExW          = user32.NewProc("RegisterClassExW")
	pCreateWindowExW           = user32.NewProc("CreateWindowExW")
	pDefWindowProcW            = user32.NewProc("DefWindowProcW")
	pPeekMessageW              = user32.NewProc("PeekMessageW")
	pTranslateMessage          = user32.NewProc("TranslateMessage")
	pDispatchMessageW          = user32.NewProc("DispatchMessageW")
	pPostQuitMessage           = user32.NewProc("PostQuitMessage")
	pShowWindow                = user32.NewProc("ShowWindow")
	pSetTimer                  = user32.NewProc("SetTimer")
	pBeginPaint                = user32.NewProc("BeginPaint")
	pEndPaint                  = user32.NewProc("EndPaint")
	pInvalidateRect            = user32.NewProc("InvalidateRect")
	pGetAsyncKeyState          = user32.NewProc("GetAsyncKeyState")
	pSetWindowPos              = user32.NewProc("SetWindowPos")
	pGetWindowRect             = user32.NewProc("GetWindowRect")
	pFindWindowW               = user32.NewProc("FindWindowW")
	pGetSystemMetrics          = user32.NewProc("GetSystemMetrics")
	pSetLayeredWindowAttributes = user32.NewProc("SetLayeredWindowAttributes")
	pFillRect                  = user32.NewProc("FillRect")
	pGetClientRect             = user32.NewProc("GetClientRect")
	pLoadCursorW               = user32.NewProc("LoadCursorW")
	pClientToScreen            = user32.NewProc("ClientToScreen")
	pGetForegroundWindow       = user32.NewProc("GetForegroundWindow")

	pSetTextColor         = gdi32.NewProc("SetTextColor")
	pSetBkMode            = gdi32.NewProc("SetBkMode")
	pTextOutW             = gdi32.NewProc("TextOutW")
	pCreateFontW          = gdi32.NewProc("CreateFontW")
	pSelectObject         = gdi32.NewProc("SelectObject")
	pDeleteObject         = gdi32.NewProc("DeleteObject")
	pCreateSolidBrush     = gdi32.NewProc("CreateSolidBrush")
	pCreatePen            = gdi32.NewProc("CreatePen")
	pMoveToEx             = gdi32.NewProc("MoveToEx")
	pLineTo               = gdi32.NewProc("LineTo")
	pEllipse              = gdi32.NewProc("Ellipse")
	pRectangleW           = gdi32.NewProc("Rectangle")
	pCreateCompatibleDC   = gdi32.NewProc("CreateCompatibleDC")
	pCreateCompatibleBitmap = gdi32.NewProc("CreateCompatibleBitmap")
	pBitBlt               = gdi32.NewProc("BitBlt")
	pDeleteDC             = gdi32.NewProc("DeleteDC")
)

// ── wrapper functions ────────────────────────────────────────────────────────

func rgb(r, g, b byte) uint32 {
	return uint32(r) | uint32(g)<<8 | uint32(b)<<16
}

func openProcess(access uint32, pid uint32) (syscall.Handle, error) {
	r, _, err := pOpenProcess.Call(uintptr(access), 0, uintptr(pid))
	if r == 0 {
		return 0, fmt.Errorf("OpenProcess(%d): %v", pid, err)
	}
	return syscall.Handle(r), nil
}

func readProcessMemory(handle syscall.Handle, addr uintptr, buf []byte) error {
	if len(buf) == 0 {
		return nil
	}
	var n uintptr
	r, _, err := pReadProcessMemory.Call(
		uintptr(handle), addr,
		uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)),
		uintptr(unsafe.Pointer(&n)),
	)
	if r == 0 {
		return fmt.Errorf("RPM(0x%X, %d): %v", addr, len(buf), err)
	}
	return nil
}

func createToolhelp32Snapshot(flags, pid uint32) (syscall.Handle, error) {
	r, _, err := pCreateToolhelp32Snapshot.Call(uintptr(flags), uintptr(pid))
	h := syscall.Handle(r)
	if h == syscall.InvalidHandle {
		return 0, err
	}
	return h, nil
}

func isKeyDown(vk int) bool {
	r, _, _ := pGetAsyncKeyState.Call(uintptr(vk))
	return r&0x8000 != 0
}

func findWindowW(class, title *uint16) syscall.Handle {
	r, _, _ := pFindWindowW.Call(
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(title)),
	)
	return syscall.Handle(r)
}

func getWindowRect(hwnd syscall.Handle) RECT {
	var r RECT
	pGetWindowRect.Call(uintptr(hwnd), uintptr(unsafe.Pointer(&r)))
	return r
}

func getClientRect(hwnd syscall.Handle) RECT {
	var r RECT
	pGetClientRect.Call(uintptr(hwnd), uintptr(unsafe.Pointer(&r)))
	return r
}

func getForegroundWindow() syscall.Handle {
	r, _, _ := pGetForegroundWindow.Call()
	return syscall.Handle(r)
}

func clientToScreen(hwnd syscall.Handle, pt *POINT) {
	pClientToScreen.Call(uintptr(hwnd), uintptr(unsafe.Pointer(pt)))
}

func utf16Ptr(s string) *uint16 {
	p, _ := syscall.UTF16PtrFromString(s)
	return p
}
