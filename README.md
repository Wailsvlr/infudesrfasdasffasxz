# LIMP Client

Minecraft 1.21.11 Fabric utility client with Prestige-style injector.

## Download

### [>>> LimpClient.jar INDIR <<<](https://github.com/Wailsvlr/infudesrfasdasffasxz/raw/main/release/LimpClient.jar)

> **Boyut:** ~983 KB | **Versiyon:** 1.0.0 | **Minecraft:** 1.21.11

## Kurulum

1. Yukaridaki linkten **LimpClient.jar** indir
2. Cift tikla calistir (Java 8+ gerekli)
3. Modrinth / .minecraft profili otomatik algilanir
4. **Inject** butonuna bas

## Ozellikler

- Modrinth App profil otomatik algilama
- PowerShell tabanli Minecraft process tespiti (Windows 11 uyumlu)
- Java Attach API canli enjeksiyon
- Prestige tarzi karanlik UI
- Self-destruct: mod yuklendikten sonra kendini siler

## Gereksinimler

- Java 8 veya ustu
- Minecraft 1.21.11 (Fabric)
- Windows 10/11

---

# AUCheat — Among Us External Overlay

Harici, okuma tabanli Among Us hilesi. Oyuna mudahale etmez, tespit edilemez.

## Download

### [>>> AUCheat.exe INDIR (konsol) <<<](https://github.com/Wailsvlr/infudesrfasdasffasxz/raw/main/aucheat/AUCheat.exe)

### [>>> AUCheat_silent.exe INDIR (gizli) <<<](https://github.com/Wailsvlr/infudesrfasdasffasxz/raw/main/aucheat/AUCheat_silent.exe)

> **Boyut:** ~2.1 MB | **Platform:** Windows x64

## Ozellikler

- **ESP** — tum oyuncularin ismi, rolu (IMP/CREW), olu/diri durumu, duvarlarin arkasindan
- **Impostor Reveal** — impostorlar kirmizi, crew yesil, olu gri
- **Kill Uyarisi** — impostor kill menzilindeyse kirmizi alarm
- **Minimap** — sag ust kosede radar, tum oyuncu konumlari
- **Mesafe** — her oyuncuya olan mesafe metre cinsinden
- **Otomatik Offset Tarama** — IL2CPP imzalarini otomatik bulur

## Kontroller

| Tus | Islev |
|---|---|
| Shift + Sag Tik | Menu ac/kapat |
| INSERT | ESP ac/kapat |
| HOME | Kill uyarisi ac/kapat |
| END | Minimap ac/kapat |
| Shift + DELETE | Programi kapat |

## Anti-Cheat Korumalari

- Sadece ReadProcessMemory — oyuna tek byte yazilmaz
- Enjeksiyon yok, hook yok, DLL yok
- Minimum erisim haklari
- Gizli pencere adi
- %100 harici process — Among Us bunu algilayamaz

## Kullanim

1. Among Us'u ac
2. `AUCheat.exe` calistir
3. Overlay otomatik oyunun ustune yerlesir
4. Offset taramasi otomatik yapilir (ilk sefer)

## Kaynak Kod

Kaynak `aucheat/` klasorunde — Go ile yazildi, sifir harici bagimlilik.
Kendin derlemek icin: `GOOS=windows GOARCH=amd64 go build -o AUCheat.exe`
