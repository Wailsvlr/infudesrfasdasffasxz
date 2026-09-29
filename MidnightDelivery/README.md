# Midnight Delivery — Bölüm 1 (ilk oynanabilir kat)

Gece vardiyasında bir apartmana paket teslim eden kurye. Bu klasör yalnızca ilk küçük
bölümü içerir: asansör kabini (başlangıç alanı), tek koridor, 101/102/103 kapıları, paket
arabası, üç teslimatlık tekrar oynanabilir vardiya, bir kez çalışan küçük korku olayı,
HUD ve teslimat cihazı. Önceki projelere (Altar, StealAnEgg) dokunulmadı; bu depodaki
mevcut Minecraft paketleri de değiştirilmedi.

## Durum: ne yapıldı, ne yapılmadı

| Durum | Sonuç |
|---|---|
| **Dosya üretildi** | Evet. `MidnightDelivery.rbxlx` Lune 0.10.5 (rbx-dom, Roblox API 0.728) ile üretildi. |
| **Dosya yeniden açılıp doğrulandı** | Evet, **Lune ile**: 38/38 otomatik kontrol geçti (bkz. `docs/TEST_RESULTS.md`). |
| **Roblox Studio'da açıldı / Play modunda oynandı** | **Hayır.** Bu oturumda Studio erişimi yok (Linux bulut konteyneri, Roblox Studio MCP bağlantısı yok). |
| **Görsel olarak kontrol edildi** | **Kısmen.** Aynı yer verisinden üretilen yaklaşık three.js önizlemeleriyle ölçek, yerleşim ve UI düzeni kontrol edildi. Bunlar Roblox ekran görüntüsü değildir; Future ışığı, malzemeler ve fontlar Studio'da görülmedi. |

## Kurulum (Studio)

1. `MidnightDelivery.rbxlx` dosyasını Roblox Studio ile aç (File → Open from File).
2. Yeni bir yer olarak kaydet (File → Publish to Roblox As… → yeni deneyim). Altar/StealAnEgg
   deneyimlerinin üzerine yayınlama.
3. Önerilen Game Settings (dosyada saklanmaz, Studio'dan ayarlanır):
   Avatar → **R15**. Ölçek aralığını dar tut (ör. Height 0.95–1.05); kapı ve paket ölçüsü
   varsayılan R15'e (≈5.2 stud) göre ayarlı.
4. Play (F5). İki oyuncu testi için: Test → Clients and Servers → 2 Players.
5. Mobil görünüm: Test → Device emülatörü (ör. iPhone 14, yatay).

İlk Studio açılışında kontrol edilecekler `docs/TEST_RESULTS.md` sonundaki listede.

## Kontroller

| Eylem | Masaüstü | Mobil | Gamepad |
|---|---|---|---|
| Paketi al / teslim et / asansörü çağır | **E** (istem hedefin üzerinde çıkar) | İstem balonuna dokun | **X** |
| Teslimat cihazını aç/kapat | **Tab** veya cihaz başlığına tıkla | Cihaz başlığındaki **SHOW / HIDE** | – (açık kalır) |

Oynanış: asansörde doğarsın, kapı açılır, cihaz ilk adresi gösterir. Asansörün hemen
solundaki arabadan paketi al; paket sağ elinde ipinden taşınır. Cihazdaki daireye git ve
kapıda E'ye basılı tut. Yanlış kapıda "Wrong apartment" ve doğru daire yazılır. Üç teslimattan
sonra arabaya dönüp "Restart shift" ile vardiyayı baştan oynayabilirsin. İkinci teslimatta
(103) kısa bir korku olayı bir kez olur.

## Ayarlanabilir değerler

| Ne | Nerede |
|---|---|
| Teslimat ödülü (25), rota (102 → 103 → 101), korkunun hangi teslimatta olacağı, mesafe/bekleme sınırları, paket tutuş ofseti, asansör süreleri, ses kimlikleri | `ReplicatedStorage.MidnightDelivery.Config` (`src/shared/Config.luau`) |
| Tüm oyuncu metinleri (İngilizce) | `Strings` (`src/shared/Strings.luau`) |
| Renkler (harita + UI) | `Palette` (`src/shared/Palette.luau`) |
| Koridor ölçüleri, kapı konumları, lamba parlaklıkları | `build/map/Layout.luau` (derlemeden önce) veya Studio'da doğrudan |

Işık ince ayarı için Studio'da `Workspace.MidnightDelivery.Map.Corridor.Lights.CorridorLight_N.Housing.LightAnchor.Light`
(`Brightness`, `Range`) ve `Lighting.Ambient` yeterli. Korku efekti, lambanın `BaseBrightness`
özniteliğini okur; parlaklığı değiştirirsen bu özniteliği de güncelle.

## Proje yapısı

```
MidnightDelivery.rbxlx        açılabilir yer dosyası (harita düzenleme modunda görünür)
src/shared/                   Config, Strings, Palette, DeliveryLogic (saf durum makinesi)
src/server/                   DeliveryService (doğrulama), DeliveryServer, ElevatorServer
src/client/                   ClientMain + DeviceUI, Toast, PromptUI, TargetMarker, WorldFx, ScareFx
build/                        .rbxlx'i üreten Lune betikleri (harita, UI, servis ayarları)
tests/                        Lune testleri (mantık, sunucu doğrulaması, kaydedilmiş dosya)
tools/preview/                yaklaşık önizleme oluşturucu (three.js + Playwright)
docs/                         sanat yönü, test sonuçları, varlık listesi, önizlemeler
```

Yer dosyasındaki yerleşim:

- `Workspace.MidnightDelivery.Map`: Corridor, Elevator, Apartments, Props. Statik parçalar; Play'de üretilmez.
- `Workspace.MidnightDelivery._DevOnly.ScaleReference_R15`: yalnızca düzenleme modunda görünen R15 ölçek mankeni (oyun başlarken sunucu siler).
- `ReplicatedStorage.MidnightDelivery`: modüller, Parcel şablonu, RemoteEvent'ler, istem/işaret şablonları.
- `ServerScriptService.MidnightDelivery`, `StarterPlayer.StarterPlayerScripts.MidnightDelivery`: betikler.
- `StarterGui.MidnightHUD`, `StarterGui.TopbarHUD`: HUD; düzenleme modunda görünür ve düzenlenebilir.

## Yeniden derleme ve testler (isteğe bağlı)

Yalnızca kaynak dosyalardan yeniden üretmek istersen gerekir. [Lune](https://github.com/lune-org/lune) 0.10.x ile:

```
cd MidnightDelivery
lune run build/build.luau      # MidnightDelivery.rbxlx'i yeniden yazar
lune run tests/run.luau        # 38 kontrol
```

Dikkat: Studio'da `.rbxlx` üzerinde değişiklik yapmaya başladıktan sonra derleme betiğini
çalıştırmak o değişikliklerin üzerine yazar. Tek bir kaynak seç: ya Studio (önerilen:
bundan sonra Studio'da devam et, betik değişikliklerini `src/` dosyalarına da yansıt) ya da
`build/` + `src/`.
