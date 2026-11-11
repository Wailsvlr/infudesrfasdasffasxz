#!/usr/bin/env python3
"""
Generates a minimal Windows PE64 executable that launches a JAR file.
The exe finds javaw.exe and runs: javaw -jar LimpClient.jar
No external dependencies, no AV triggers - just a tiny native launcher.
"""
import struct, sys, os

def le16(v): return struct.pack('<H', v)
def le32(v): return struct.pack('<I', v)
def le64(v): return struct.pack('<Q', v)

FILE_ALIGN = 0x200
SECT_ALIGN = 0x1000
IMAGE_BASE = 0x00400000

def align(v, a):
    return (v + a - 1) & ~(a - 1)

def build_exe(jar_name="LimpClient.jar"):
    # ── Strings for the code ──
    cmd_str = f'javaw -jar "{jar_name}"\x00'.encode('ascii')
    err_str = b'Java not found. Install Java 17+ from adoptium.net\x00'
    err_title = b'LIMP Client\x00'

    # ── Import table data ──
    dll_name = b'kernel32.dll\x00'
    dll_name2 = b'user32.dll\x00'
    func_winexec = b'\x00\x00WinExec\x00'       # hint(2 bytes) + name + null
    func_exit = b'\x00\x00ExitProcess\x00'
    func_msgbox = b'\x00\x00MessageBoxA\x00'

    # We'll put everything in one .text section for simplicity
    # Layout within .text section (RVA base = 0x1000):
    #   0x000: code
    #   0x100: IAT (Import Address Table) - 4 entries (2 funcs + 2 null terminators)
    #   0x130: ILT (Import Lookup Table) - mirrors IAT
    #   0x160: Import Directory Table (2 entries + null terminator)
    #   0x1C0: hint/name entries
    #   0x240: dll name strings
    #   0x280: command string & other strings

    TEXT_RVA = 0x1000

    IAT_OFF = 0x100
    ILT_OFF = 0x140
    IDT_OFF = 0x180  # Import Directory Table
    HINTS_OFF = 0x1E0
    DLLNAMES_OFF = 0x240
    STRINGS_OFF = 0x280

    # Hint/Name table entries (RVAs)
    hint_winexec_rva = TEXT_RVA + HINTS_OFF
    hint_exit_rva = TEXT_RVA + HINTS_OFF + len(func_winexec)
    hint_msgbox_rva = TEXT_RVA + HINTS_OFF + len(func_winexec) + len(func_exit)

    # IAT entries (will be filled by loader, initially same as ILT)
    # kernel32: WinExec, ExitProcess (2 entries + null)
    # user32: MessageBoxA (1 entry + null)
    iat_kernel32_off = IAT_OFF
    iat_user32_off = IAT_OFF + 3 * 8  # after 2 entries + null

    ilt_kernel32_off = ILT_OFF
    ilt_user32_off = ILT_OFF + 3 * 8

    # Build IAT/ILT data (identical initially)
    def make_thunk_table(entries):
        data = b''
        for rva in entries:
            data += le64(rva)
        data += le64(0)  # null terminator
        return data

    kernel32_thunks = make_thunk_table([hint_winexec_rva, hint_exit_rva])
    user32_thunks = make_thunk_table([hint_msgbox_rva])

    iat_data = kernel32_thunks + user32_thunks
    ilt_data = kernel32_thunks + user32_thunks  # identical copy

    # Import Directory Table (2 entries + null terminator)
    # Each entry: 20 bytes (OriginalFirstThunk, TimeDateStamp, ForwarderChain, Name, FirstThunk)
    idt_entry_k32 = struct.pack('<IIIII',
        TEXT_RVA + ilt_kernel32_off,  # OriginalFirstThunk (ILT)
        0,                            # TimeDateStamp
        0,                            # ForwarderChain
        TEXT_RVA + DLLNAMES_OFF,      # Name RVA
        TEXT_RVA + iat_kernel32_off,  # FirstThunk (IAT)
    )
    idt_entry_u32 = struct.pack('<IIIII',
        TEXT_RVA + ilt_user32_off,
        0, 0,
        TEXT_RVA + DLLNAMES_OFF + len(dll_name),
        TEXT_RVA + iat_user32_off,
    )
    idt_null = b'\x00' * 20
    idt_data = idt_entry_k32 + idt_entry_u32 + idt_null

    # Hint/Name entries
    hints_data = func_winexec + func_exit + func_msgbox

    # DLL name strings
    dllnames_data = dll_name + dll_name2

    # Command & error strings
    strings_data = cmd_str + err_str + err_title
    cmd_str_rva = TEXT_RVA + STRINGS_OFF
    err_str_rva = TEXT_RVA + STRINGS_OFF + len(cmd_str)
    err_title_rva = TEXT_RVA + STRINGS_OFF + len(cmd_str) + len(err_str)

    # IAT RVAs for the code to reference
    winexec_iat_rva = TEXT_RVA + iat_kernel32_off
    exitproc_iat_rva = TEXT_RVA + iat_kernel32_off + 8
    msgbox_iat_rva = TEXT_RVA + iat_user32_off

    # ── x86-64 Code ──
    # Windows x64 calling convention: rcx, rdx, r8, r9, then stack
    # Must align stack to 16 bytes, provide 32-byte shadow space

    code = bytearray()
    code_base_rva = TEXT_RVA  # code starts at beginning of .text

    def rip_rel(target_rva, current_off, instr_len):
        """Calculate RIP-relative offset. RIP = code_base_rva + current_off + instr_len"""
        return target_rva - (code_base_rva + current_off + instr_len)

    # sub rsp, 0x38 (56 = 32 shadow + 16 for 5th/6th args + 8 alignment)
    code += b'\x48\x83\xEC\x38'

    # lea rcx, [rip + cmd_str]  ; first arg = command string
    off = len(code)
    rel = rip_rel(cmd_str_rva, off, 7)
    code += b'\x48\x8D\x0D' + struct.pack('<i', rel)

    # mov edx, 5  ; SW_SHOW
    code += b'\xBA\x05\x00\x00\x00'

    # call [rip + WinExec IAT entry]
    off = len(code)
    rel = rip_rel(winexec_iat_rva, off, 6)
    code += b'\xFF\x15' + struct.pack('<i', rel)

    # test eax, eax  ; WinExec returns > 31 on success
    code += b'\x85\xC0'

    # cmp eax, 32
    code += b'\x83\xF8\x20'

    # jge success (skip error messagebox)
    code += b'\x7D\x2A'  # jge +42 bytes forward (we'll adjust)

    jge_patch_off = len(code)  # remember where to patch

    # ── Error path: show MessageBox ──
    # MessageBoxA(NULL, text, title, MB_OK | MB_ICONERROR = 0x10)
    # xor ecx, ecx  ; hWnd = NULL
    code += b'\x31\xC9'

    # lea rdx, [rip + err_str]  ; lpText
    off = len(code)
    rel = rip_rel(err_str_rva, off, 7)
    code += b'\x48\x8D\x15' + struct.pack('<i', rel)

    # lea r8, [rip + err_title]  ; lpCaption
    off = len(code)
    rel = rip_rel(err_title_rva, off, 7)
    code += b'\x4C\x8D\x05' + struct.pack('<i', rel)

    # mov r9d, 0x10  ; MB_ICONERROR
    code += b'\x41\xB9\x10\x00\x00\x00'

    # call [rip + MessageBoxA IAT]
    off = len(code)
    rel = rip_rel(msgbox_iat_rva, off, 6)
    code += b'\xFF\x15' + struct.pack('<i', rel)

    # ── Exit ──
    # success label is here - patch the jge
    success_off = len(code)
    # Go back and fix the jge offset
    jump_distance = success_off - jge_patch_off
    code[jge_patch_off - 1] = jump_distance & 0xFF

    # xor ecx, ecx  ; exit code 0
    code += b'\x31\xC9'

    # call [rip + ExitProcess IAT]
    off = len(code)
    rel = rip_rel(exitproc_iat_rva, off, 6)
    code += b'\xFF\x15' + struct.pack('<i', rel)

    # int3 (should never reach)
    code += b'\xCC'

    # Pad code to IAT_OFF
    code += b'\x00' * (IAT_OFF - len(code))

    # ── Build .text section content ──
    text_data = bytearray(code)

    # Ensure we're at the right offsets
    assert len(text_data) == IAT_OFF
    text_data += iat_data

    # Pad to ILT_OFF
    text_data += b'\x00' * (ILT_OFF - len(text_data))
    text_data += ilt_data

    # Pad to IDT_OFF
    text_data += b'\x00' * (IDT_OFF - len(text_data))
    text_data += idt_data

    # Pad to HINTS_OFF
    text_data += b'\x00' * (HINTS_OFF - len(text_data))
    text_data += hints_data

    # Pad to DLLNAMES_OFF
    text_data += b'\x00' * (DLLNAMES_OFF - len(text_data))
    text_data += dllnames_data

    # Pad to STRINGS_OFF
    text_data += b'\x00' * (STRINGS_OFF - len(text_data))
    text_data += strings_data

    text_raw_size = align(len(text_data), FILE_ALIGN)
    text_virt_size = len(text_data)

    # ── Headers ──
    num_sections = 1

    # DOS Header
    dos = bytearray(64)
    dos[0:2] = b'MZ'
    struct.pack_into('<I', dos, 60, 64)  # e_lfanew

    # PE Signature
    pe_sig = b'PE\x00\x00'

    # COFF Header (20 bytes)
    opt_hdr_size = 112 + 16 * 8  # PE32+ optional header base + 16 data directories
    coff = struct.pack('<HHIIIHH',
        0x8664,       # Machine: AMD64
        num_sections,  # NumberOfSections
        0x67335A00,   # TimeDateStamp (some date)
        0,            # PointerToSymbolTable
        0,            # NumberOfSymbols
        opt_hdr_size, # SizeOfOptionalHeader
        0x0022,       # Characteristics: EXECUTABLE_IMAGE | LARGE_ADDRESS_AWARE
    )

    headers_size = 64 + 4 + 20 + opt_hdr_size + 40 * num_sections
    size_of_headers = align(headers_size, FILE_ALIGN)
    text_file_off = size_of_headers
    size_of_image = align(TEXT_RVA + text_virt_size, SECT_ALIGN)

    # Optional Header (PE32+)
    opt = bytearray(opt_hdr_size)
    struct.pack_into('<H', opt, 0, 0x020B)       # Magic: PE32+
    opt[2] = 14                                    # MajorLinkerVersion
    opt[3] = 0                                     # MinorLinkerVersion
    struct.pack_into('<I', opt, 4, text_raw_size)  # SizeOfCode
    struct.pack_into('<I', opt, 8, 0)              # SizeOfInitializedData
    struct.pack_into('<I', opt, 12, 0)             # SizeOfUninitializedData
    struct.pack_into('<I', opt, 16, 0)             # AddressOfEntryPoint (RVA of code = 0x1000, relative to section)
    struct.pack_into('<I', opt, 20, TEXT_RVA)      # BaseOfCode
    struct.pack_into('<Q', opt, 24, IMAGE_BASE)    # ImageBase
    struct.pack_into('<I', opt, 32, SECT_ALIGN)    # SectionAlignment
    struct.pack_into('<I', opt, 36, FILE_ALIGN)    # FileAlignment
    struct.pack_into('<H', opt, 40, 6)             # MajorOSVersion
    struct.pack_into('<H', opt, 42, 0)             # MinorOSVersion
    struct.pack_into('<H', opt, 44, 0)             # MajorImageVersion
    struct.pack_into('<H', opt, 46, 0)             # MinorImageVersion
    struct.pack_into('<H', opt, 48, 6)             # MajorSubsystemVersion
    struct.pack_into('<H', opt, 50, 0)             # MinorSubsystemVersion
    struct.pack_into('<I', opt, 52, 0)             # Win32VersionValue
    struct.pack_into('<I', opt, 56, size_of_image) # SizeOfImage
    struct.pack_into('<I', opt, 60, size_of_headers) # SizeOfHeaders
    struct.pack_into('<I', opt, 64, 0)             # CheckSum
    struct.pack_into('<H', opt, 68, 2)             # Subsystem: GUI (2)
    struct.pack_into('<H', opt, 70, 0x8160)        # DllCharacteristics: DEP, ASLR, HIGHENTROPYVA, TERMINAL_SERVER_AWARE
    struct.pack_into('<Q', opt, 72, 0x100000)      # SizeOfStackReserve
    struct.pack_into('<Q', opt, 80, 0x1000)        # SizeOfStackCommit
    struct.pack_into('<Q', opt, 88, 0x100000)      # SizeOfHeapReserve
    struct.pack_into('<Q', opt, 96, 0x1000)        # SizeOfHeapCommit
    struct.pack_into('<I', opt, 104, 0)            # LoaderFlags
    struct.pack_into('<I', opt, 108, 16)           # NumberOfRvaAndSizes

    # Data directories (16 entries, 8 bytes each, starting at offset 112)
    # Entry 1 (index 1): Import Table
    struct.pack_into('<II', opt, 112 + 1 * 8, TEXT_RVA + IDT_OFF, len(idt_data))
    # Entry 12 (index 12): IAT
    struct.pack_into('<II', opt, 112 + 12 * 8, TEXT_RVA + IAT_OFF, len(iat_data))

    # Fix: AddressOfEntryPoint should be the RVA of the first instruction
    struct.pack_into('<I', opt, 16, TEXT_RVA)  # Entry point = start of .text

    # Section Header: .text
    text_hdr = bytearray(40)
    text_hdr[0:6] = b'.text\x00'
    struct.pack_into('<I', text_hdr, 8, text_virt_size)   # VirtualSize
    struct.pack_into('<I', text_hdr, 12, TEXT_RVA)         # VirtualAddress
    struct.pack_into('<I', text_hdr, 16, text_raw_size)    # SizeOfRawData
    struct.pack_into('<I', text_hdr, 20, text_file_off)    # PointerToRawData
    struct.pack_into('<I', text_hdr, 36, 0xE0000060)       # Characteristics: CODE|EXECUTE|READ|WRITE

    # ── Assemble the file ──
    exe = bytearray()
    exe += dos
    exe += pe_sig
    exe += coff
    exe += bytes(opt)
    exe += bytes(text_hdr)

    # Pad headers to alignment
    exe += b'\x00' * (size_of_headers - len(exe))

    # .text section
    exe += bytes(text_data)
    exe += b'\x00' * (text_raw_size - len(text_data))

    return bytes(exe)

if __name__ == '__main__':
    jar = sys.argv[1] if len(sys.argv) > 1 else 'LimpClient.jar'
    exe_path = sys.argv[2] if len(sys.argv) > 2 else 'LimpClient.exe'

    exe_data = build_exe(jar)
    with open(exe_path, 'wb') as f:
        f.write(exe_data)

    print(f"Created {exe_path} ({len(exe_data):,} bytes)")
    print(f"Launches: javaw -jar \"{jar}\"")
    print(f"Place {jar} next to {os.path.basename(exe_path)}")
