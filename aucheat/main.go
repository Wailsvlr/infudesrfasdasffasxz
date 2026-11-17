package main

import (
	"fmt"
	"os"
)

func main() {
	fmt.Println("╔══════════════════════════════════════╗")
	fmt.Println("║  AUCheat — PORTOLAN build            ║")
	fmt.Println("║  external overlay · read-only · safe  ║")
	fmt.Println("╚══════════════════════════════════════╝")
	fmt.Println()

	cfg := LoadConfig()

	// find game process
	fmt.Println("[~] Among Us araniyor...")
	proc, err := FindAmongUs()
	if err != nil {
		fmt.Fprintf(os.Stderr, "[!] %v\n", err)
		fmt.Println("[!] Among Us calistir, sonra bu exe'yi tekrar ac.")
		fmt.Println("    Cikmak icin Enter'a bas...")
		fmt.Scanln()
		os.Exit(1)
	}
	defer proc.Close()
	fmt.Printf("[+] Among Us bulundu — PID %d\n", proc.PID)

	// find GameAssembly.dll
	fmt.Println("[~] GameAssembly.dll araniyor...")
	gaBase, gaSize, err := proc.FindModule("GameAssembly.dll")
	if err != nil {
		fmt.Fprintf(os.Stderr, "[!] %v\n", err)
		fmt.Println("[!] Oyun tam yuklenene kadar bekle, sonra tekrar dene.")
		fmt.Println("    Cikmak icin Enter'a bas...")
		fmt.Scanln()
		os.Exit(1)
	}
	fmt.Printf("[+] GameAssembly.dll — base 0x%X, boyut 0x%X\n", gaBase, gaSize)

	// offset scanning
	if cfg.NeedsScan() {
		fmt.Println("[~] Offset taramasi yapiliyor...")
		err := ScanOffsets(proc, gaBase, gaSize, cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[!] Tarama hatasi: %v\n", err)
			fmt.Println("[!] offsets.json dosyasini elle ayarlaman gerekebilir.")
			fmt.Println("    Il2CppDumper ile dump al, offsetleri yaz.")
		} else {
			if err := cfg.Save(); err != nil {
				fmt.Printf("[!] Config kaydetme hatasi: %v\n", err)
			} else {
				fmt.Println("[+] Offsetler bulundu → offsets.json kaydedildi")
			}
		}
	} else {
		fmt.Println("[+] Offsetler offsets.json'dan yuklendi")
	}

	if cfg.Statics.AmongUsClient == 0 {
		fmt.Println("[!] AmongUsClient offseti bulunamadi — ESP calismayabilir.")
		fmt.Println("    Devam ediliyor...")
	}

	// build game reader
	game := NewGameReader(proc, gaBase, cfg)

	// anti-cheat info
	fmt.Println()
	fmt.Println("[i] Anti-cheat onlemleri:")
	fmt.Println("    • Sadece ReadProcessMemory — oyuna yazma yok")
	fmt.Println("    • Minimum erisim haklari (VM_READ + QUERY_INFO)")
	fmt.Println("    • Enjeksiyon yok, hook yok, DLL yok")
	fmt.Println("    • Overlay penceresi gizli isimle")
	fmt.Println("    • Tespit edilemez (okuma-tabanlı)")
	fmt.Println()

	// controls
	fmt.Println("[i] Kontroller:")
	fmt.Println("    F1  →  Menu ac/kapat")
	fmt.Println("    F2  →  ESP ac/kapat")
	fmt.Println("    F3  →  Kill uyarisi ac/kapat")
	fmt.Println("    F4  →  Minimap ac/kapat")
	fmt.Println("    F5  →  Programi kapat")
	fmt.Println()
	fmt.Println("[+] Overlay baslatiliyor...")

	overlay := NewOverlay(game, cfg)
	overlay.Run()

	fmt.Println("[+] AUCheat kapatildi.")
}
