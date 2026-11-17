package main

import "fmt"

// pattern scanner — searches GameAssembly.dll memory for IL2CPP singleton
// accessors and extracts RIP-relative offsets.
//
// typical IL2CPP singleton getter (x64):
//   48 83 EC 28           sub rsp, 0x28
//   48 8B 05 XX XX XX XX  mov rax, [rip + offset]  ← static pointer
//   48 85 C0              test rax, rax
//   74 ??                 jz ...
//
// alternate form:
//   48 8B 0D XX XX XX XX  mov rcx, [rip + offset]
//   48 85 C9              test rcx, rcx

// findPattern scans data for a byte pattern with mask.
// pattern bytes where mask[i] == '?' are wildcards.
func findPattern(data []byte, pattern []byte, mask string) int {
	pLen := len(pattern)
	if pLen == 0 || len(mask) != pLen || len(data) < pLen {
		return -1
	}
	for i := 0; i <= len(data)-pLen; i++ {
		found := true
		for j := 0; j < pLen; j++ {
			if mask[j] != '?' && data[i+j] != pattern[j] {
				found = false
				break
			}
		}
		if found {
			return i
		}
	}
	return -1
}

// resolveRIPRelative extracts the 32-bit RIP-relative offset from an
// instruction at data[instrOffset..] and returns the absolute target address.
// ripBytesOffset is where the 4-byte displacement starts within the instruction.
// instrLen is the total instruction length.
func resolveRIPRelative(moduleBase uintptr, instrOffset int, data []byte, ripBytesOffset, instrLen int) uintptr {
	pos := instrOffset + ripBytesOffset
	if pos+4 > len(data) {
		return 0
	}
	disp := int32(uint32(data[pos]) | uint32(data[pos+1])<<8 | uint32(data[pos+2])<<16 | uint32(data[pos+3])<<24)
	instrAddr := moduleBase + uintptr(instrOffset)
	return instrAddr + uintptr(instrLen) + uintptr(disp)
}

// ScanOffsets reads GameAssembly.dll's .text section and searches for
// singleton accessor patterns to fill in static pointer offsets.
func ScanOffsets(proc *Proc, gaBase uintptr, gaSize uint32, cfg *Config) error {
	// read first 32MB or entire module, whichever is smaller
	readSize := int(gaSize)
	if readSize > 32*1024*1024 {
		readSize = 32 * 1024 * 1024
	}

	data, err := proc.ReadBytes(gaBase, readSize)
	if err != nil {
		return fmt.Errorf("GameAssembly okuma hatasi: %v", err)
	}

	found := 0

	// — AmongUsClient.Instance —
	// pattern: 48 83 EC 28 48 8B 05 ?? ?? ?? ?? 48 85 C0 74
	pat1 := []byte{0x48, 0x83, 0xEC, 0x28, 0x48, 0x8B, 0x05, 0x00, 0x00, 0x00, 0x00, 0x48, 0x85, 0xC0, 0x74}
	mask1 := "xxxxxxx????xxxx"
	if off := findPattern(data, pat1, mask1); off >= 0 {
		addr := resolveRIPRelative(gaBase, off+4, data, 3, 7)
		if addr != 0 {
			cfg.Statics.AmongUsClient = addr
			found++
			fmt.Printf("    AmongUsClient.Instance @ 0x%X\n", addr)
		}
	}

	// — GameData.Instance —
	// same prologue pattern, different location; we search after the first match
	// alternate: 48 8B 0D ?? ?? ?? ?? 48 85 C9 0F 84
	pat2 := []byte{0x48, 0x8B, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x48, 0x85, 0xC9, 0x0F, 0x84}
	mask2 := "xxx????xxxxx"
	scanStart := 0
	for attempt := 0; attempt < 20; attempt++ {
		off := findPattern(data[scanStart:], pat2, mask2)
		if off < 0 {
			break
		}
		absOff := scanStart + off
		addr := resolveRIPRelative(gaBase, absOff, data, 3, 7)
		if addr != 0 && addr != cfg.Statics.AmongUsClient {
			// verify this looks like a GameData pointer by reading it
			ptr, err := proc.ReadPtr(addr)
			if err == nil && ptr != 0 {
				cfg.Statics.GameData = addr
				found++
				fmt.Printf("    GameData.Instance @ 0x%X\n", addr)
				break
			}
		}
		scanStart = absOff + 7
	}

	if found < 1 {
		return fmt.Errorf("kritik offsetler bulunamadi (%d/2)", found)
	}
	cfg.Scanned = true
	return nil
}
