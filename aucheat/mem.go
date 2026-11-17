package main

import (
	"encoding/binary"
	"fmt"
	"math"
	"strings"
	"syscall"
	"unicode/utf16"
	"unsafe"
)

// ── process handle ───────────────────────────────────────────────────────────

type Proc struct {
	PID    uint32
	Handle syscall.Handle
}

func FindAmongUs() (*Proc, error) {
	snap, err := createToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)
	if err != nil {
		return nil, fmt.Errorf("snapshot: %v", err)
	}
	defer pCloseHandle.Call(uintptr(snap))

	var pe PROCESSENTRY32W
	pe.Size = uint32(unsafe.Sizeof(pe))

	r, _, _ := pProcess32FirstW.Call(uintptr(snap), uintptr(unsafe.Pointer(&pe)))
	if r == 0 {
		return nil, fmt.Errorf("Process32First failed")
	}

	for {
		name := syscall.UTF16ToString(pe.ExeFile[:])
		if strings.EqualFold(name, "Among Us.exe") {
			h, err := openProcess(PROCESS_VM_READ|PROCESS_QUERY_INFORMATION, pe.ProcessID)
			if err != nil {
				return nil, err
			}
			return &Proc{PID: pe.ProcessID, Handle: h}, nil
		}
		r, _, _ = pProcess32NextW.Call(uintptr(snap), uintptr(unsafe.Pointer(&pe)))
		if r == 0 {
			break
		}
	}
	return nil, fmt.Errorf("Among Us.exe bulunamadi")
}

func (p *Proc) FindModule(modName string) (base uintptr, size uint32, err error) {
	snap, err := createToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32, p.PID)
	if err != nil {
		return 0, 0, fmt.Errorf("module snapshot: %v", err)
	}
	defer pCloseHandle.Call(uintptr(snap))

	var me MODULEENTRY32W
	me.Size = uint32(unsafe.Sizeof(me))

	r, _, _ := pModule32FirstW.Call(uintptr(snap), uintptr(unsafe.Pointer(&me)))
	if r == 0 {
		return 0, 0, fmt.Errorf("Module32First failed")
	}

	for {
		name := syscall.UTF16ToString(me.ModuleName[:])
		if strings.EqualFold(name, modName) {
			return me.ModBaseAddr, me.ModBaseSize, nil
		}
		r, _, _ = pModule32NextW.Call(uintptr(snap), uintptr(unsafe.Pointer(&me)))
		if r == 0 {
			break
		}
	}
	return 0, 0, fmt.Errorf("%s bulunamadi", modName)
}

func (p *Proc) Close() {
	if p.Handle != 0 {
		pCloseHandle.Call(uintptr(p.Handle))
	}
}

// ── typed memory reads ───────────────────────────────────────────────────────

func (p *Proc) ReadBytes(addr uintptr, n int) ([]byte, error) {
	buf := make([]byte, n)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return nil, err
	}
	return buf, nil
}

func (p *Proc) ReadPtr(addr uintptr) (uintptr, error) {
	buf := make([]byte, 8)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return 0, err
	}
	return uintptr(binary.LittleEndian.Uint64(buf)), nil
}

func (p *Proc) ReadU32(addr uintptr) (uint32, error) {
	buf := make([]byte, 4)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return 0, err
	}
	return binary.LittleEndian.Uint32(buf), nil
}

func (p *Proc) ReadI32(addr uintptr) (int32, error) {
	v, err := p.ReadU32(addr)
	return int32(v), err
}

func (p *Proc) ReadF32(addr uintptr) (float32, error) {
	buf := make([]byte, 4)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return 0, err
	}
	bits := binary.LittleEndian.Uint32(buf)
	return math.Float32frombits(bits), nil
}

func (p *Proc) ReadBool(addr uintptr) (bool, error) {
	buf := make([]byte, 1)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return false, err
	}
	return buf[0] != 0, nil
}

func (p *Proc) ReadByte(addr uintptr) (byte, error) {
	buf := make([]byte, 1)
	if err := readProcessMemory(p.Handle, addr, buf); err != nil {
		return 0, err
	}
	return buf[0], nil
}

// IL2CPP string: int32 length at +0x10, UTF-16 chars starting at +0x14
func (p *Proc) ReadIL2CppString(addr uintptr) (string, error) {
	if addr == 0 {
		return "", nil
	}
	length, err := p.ReadI32(addr + 0x10)
	if err != nil {
		return "", err
	}
	if length <= 0 || length > 128 {
		return "", fmt.Errorf("bad string length %d", length)
	}
	buf := make([]byte, int(length)*2)
	if err := readProcessMemory(p.Handle, addr+0x14, buf); err != nil {
		return "", err
	}
	u16 := make([]uint16, length)
	for i := int32(0); i < length; i++ {
		u16[i] = binary.LittleEndian.Uint16(buf[i*2:])
	}
	return string(utf16.Decode(u16)), nil
}

// follow a chain of pointers: base → [off0] → [off1] → ...
func (p *Proc) ReadChain(base uintptr, offsets ...uintptr) (uintptr, error) {
	addr := base
	for _, off := range offsets {
		ptr, err := p.ReadPtr(addr + off)
		if err != nil {
			return 0, err
		}
		if ptr == 0 {
			return 0, fmt.Errorf("null at chain offset 0x%X (from 0x%X)", off, addr)
		}
		addr = ptr
	}
	return addr, nil
}
