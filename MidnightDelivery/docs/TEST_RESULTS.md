# Test sonuçları — Bölüm 1

Tarih: 2026-09-29. Ortam: Linux bulut konteyneri; **Roblox Studio yok**, Studio'ya bağlanan
bir araç (MCP/eklenti) yok. Studio Windows/macOS'ta çalıştığı için burada açılamaz.
Bu yüzden aşağıdaki bölümler ayrı tutuldu.

## 1. Otomatik testler (gerçekten çalıştırıldı) — 38/38 geçti

Komut: `lune run tests/run.luau` (Lune 0.10.5, Roblox API veritabanı 0.728).
Bu testler Roblox motorunu çalıştırmaz. Oyun kuralları saf Luau olarak, sunucu doğrulaması
sahte oyuncularla, yer dosyası ise diskten yeniden açılarak test edildi.

**Durum makinesi (DeliveryLogic)**
- [x] Yeni vardiya ilk adreste, paketsiz başlar
- [x] Rota üç kapıyı birer kez kullanır
- [x] Paketsiz teslimat reddedilir, ödeme yok
- [x] Yanlış kapı: doğru daire bildirilir, paket elde kalır
- [x] Doğru kapı bir kez öder; tekrarlanan istek ödemez
- [x] Bilinmeyen/bozuk kapı kimlikleri reddedilir
- [x] Paket taşırken ikinci alma reddedilir
- [x] Tam vardiya: 3 teslimat, korku yalnızca ayarlanan teslimatta ve bir kez
- [x] Yeniden başlatma yalnızca vardiya bitince, temiz durumla
- [x] Paketi düşürme (ölüm/respawn) aynı teslimatın alma adımına döner
- [x] İstemciye yansıtılan anlık görüntü doğru

**Sunucu doğrulaması (DeliveryService, sahte oyuncular)**
- [x] Asansörden (uzaktan) paket alma reddedilir
- [x] Arabada alma: ilk adres için paket takılır
- [x] Paketsiz kapıda teslimat hiçbir şey yapmaz
- [x] Yanlış kapı geri bildirimi doğru daireyi söyler, paket kalır
- [x] Doğru kapıya 10 kez art arda istek: tek ödeme (bekleme süresi + durum koruması)
- [x] Uzaktan sahte teslimat isteği ve konum olmayan değer reddedilir
- [x] Ölü/karaktersiz oyuncu etkileşemez
- [x] Paket taşırken ölüm: paket silinir, bir kez "Parcel dropped", yeniden alınabilir
- [x] Paket taşırken çıkış: paket ve durum silinir
- [x] Eli olmayan karakter: alma geri alınır
- [x] Servis üzerinden tam vardiya: 3 ödeme, 1 korku, arabada yeniden başlatma
- [x] İki oyuncu: paket, hedef, kazanç, bekleme süresi ve korku birbirinden ayrı

**Kaydedilmiş dosya (`MidnightDelivery.rbxlx` diskten yeniden açıldı)**
- [x] Harita dosyada statik; Play'de üretilmiyor
- [x] Dosya güncel kaynaklarla aynı; derlemenin atadığı ~6.000 özelliğin hepsi kayıttan sonra korunmuş
- [x] 14 betiğin kaynağı `src/` ile birebir aynı ve derleniyor (sözdizimi)
- [x] Stud dokusu yok, varsayılan Plastic yok, haritadaki her parça sabit (anchored), görünmez duvar yok
- [x] Koridor 13 × 11 × 50 stud
- [x] Kapı açıklıkları 5 × 8 stud; numara plakaları daire kimlikleriyle aynı
- [x] Asansör: ~10×10 kabin, iki sürgülü kapı, doluluk alanı, çağırma düğmesi, kapılara bakan tek doğma noktası
- [x] Yürüme şeridi (z −4..4) çarpışan eşyalardan boş
- [x] Her görünen lambanın ışığı, her noktasal ışığın görünen lambası var
- [x] Işık: Future, yalnızca iç mekân, sis/atmosfer/bloom yok
- [x] İstemler yalnızca Custom stil, E / ButtonX, menzil sunucu kontrolünün içinde
- [x] Paket şablonu kütlesiz, çarpışmasız, kaynaklı (weld)
- [x] İstemci betiklerinin aradığı tüm UI öğeleri mevcut; cihaz genişliği 320 px
- [x] Tek font ailesi (Builder Sans), oyuncuya görünen tüm metinler İngilizce
- [x] Yalnızca düzenleme modundaki yardımcılar oyun başında silinir; çakışan Roblox arayüzü kapatılır

### Testlerin yakalayıp düzelttirdiği gerçek sorunlar

Bunlar Studio'da sessizce bozuk görünecekti:

1. Lune'da `Instance.new("Part")` varsayılanı **Studs** yüzeydir. Tüm parçalar açıkça Smooth yapıldı.
2. `Attachment.Position` dosyaya **kaydedilmiyor** (yalnızca `CFrame` kaydediliyor). İstem, işaret ve ışık noktaları `CFrame` ile yazıldı.
3. Eski `Font` enum'u Builder Sans için bozuk dosya üretiyordu. Yalnızca `FontFace` kullanıldı.
4. `ScreenGui.IgnoreGuiInset` artık kaydedilmiyor (`ScreenInsets`'ten türetiliyor). Kaldırıldı; `ScreenInsets` açıkça ayarlı.
5. `UICorner.CornerRadius` güncel formatta dört köşe özelliği olarak kaydediliyor. Test bunu köşe köşe doğruluyor.
6. Paket arabasının alt çerçevesi yürüme şeridine 0.006 stud taşıyordu. Çarpışması kapatıldı.
7. Boş metinli iki düğmede font LegacyArial kalmıştı. Builder Sans yapıldı.

## 2. Görsel kontrol (yaklaşık önizleme; Roblox görüntüsü değil)

`tools/preview` aynı yer verisini (`build.luau --dump`) three.js ve HTML ile çizer: parçalar,
ışıklar, yüzey yazıları ve UI ağacı. Kamera Roblox varsayılanı FOV 70'tir. Mavi blok karakter
R15 ölçülerinde bir yer tutucudur, gerçek avatar değildir. Future ışığı, Roblox malzeme
dokuları, Builder Sans ve Roblox'un kendi arayüzü **taklit edilmez**: üst çubuk düğmeleri,
sohbet, joystick ve zıplama düğmesi kesikli çizgili yaklaşık kutulardır. Her görselde
"NOT A ROBLOX SCREENSHOT" filigranı vardır.

| Görsel | İstenen |
|---|---|
| `previews/01_elevator_to_corridor.png` | Asansörden koridora bakış |
| `previews/02_door_and_parcel_player_height.png` | Oyuncu yüksekliğinden kapı ve paket |
| `previews/03_desktop_device_open_1920x1080.png` | Teslimat cihazı açıkken masaüstü |
| `previews/04_mobile_844x390_device_open.png`, `04b_…_collapsed.png` | Mobil ekran oranında UI |
| `previews/05_delivery_complete.png` | Teslimat tamamlanma durumu |
| `previews/06_corridor_back_to_elevator.png`, `07_doors_103_101_player_height.png` | Ek: ölçek ve kapı detayları |

Önizlemeye bakınca yapılan düzeltmeler:
- Kapı yüksekliği 8.5 → **8.0** stud (R15 yanında dev duruyordu).
- Boş üst duvarlara tekrarlayan pilaster modülleri eklendi.
- "ELECTRICAL" levhası sığmıyordu; levha genişletildi, yazı küçültüldü.
- Doğma noktası kapıya yaklaştırıldı (−6.5 → −4.5). 10×10 kabinde kamera karakterin hemen arkasına sıkışıyordu.
- Kabin duvarları bir ton açıldı, kabin ışığı 0.8 → 1.0.
- Sonuç bildirimi %72 → %80 yüksekliğe indi; karakterin gövdesini kapatmıyor.

## 3. Test EDİLMEYENLER (Studio gerektirir)

- Play modunda gerçek oynanış: paket alma, taşıma, teslim, yanlış kapı, spam, respawn, iki oyuncu.
- Future ışığında gerçek görünüm: parlaklık, Neon lamba parlaması, metal/beyaz yüzeylerde patlama, duvar renginin turuncuya kayıp kaymadığı.
- Paketin gerçek R15 avatarın elindeki konumu (`Config.ParcelGrip`) ve yürürken bacağa girip girmediği.
- Mobil dokunmatik istem, `SHOW/HIDE`, Tab bağlaması, gamepad.
- `TopbarSafeInsets` içinde başlık ve kazanç çiplerinin gerçek yeri; Roblox'un açılan üst çubuğuyla (unibar) etkileşimi.
- Asansör kapısı tween'i, otomatik kapanma, çağırma düğmesi.
- Korku olayı (titreme, kapı sarsılması, ikinci ses tetiklemesi). Ses dosyası yok.
- Output penceresi: hata/uyarı var mı, görülmedi.

## 4. Studio'da ilk açılış kontrol listesi (sende, ~10 dk)

1. Dosyayı aç, Output'u aç (View → Output). Düzenleme modunda koridor, kapılar ve mavi ölçek mankeni görünmeli.
2. F5. Beklenen: kabinde doğarsın; ~1 sn sonra kapı açılır; sağ üstte cihaz "Apartment 102 / Pick up the parcel.", arabanın üstünde "PARCELS" işareti.
3. Arabaya git → E. Paket sağ elde, karakter savrulmamalı, kamera kapanmamalı. Cihaz "Deliver to Apartment 102."
4. Önce 101'e git → E basılı tut → "Wrong apartment / This parcel is for Apartment 102."
5. 102 → E basılı tut → paket paspasa bırakılır, "Delivery complete +$25", sağ üst "$25". E'ye tekrar tekrar bas: kazanç artmamalı.
6. 103 teslimatı: ~1 sn sonra kapı hafifçe sarsılır ve en yakın lamba kısa titrer. Kapıdan ~13 stud uzaklaşınca kapı bir kez daha sarsılır.
7. Paket elindeyken karakteri sıfırla (Esc → Reset): "Parcel dropped", cihaz "Pick up the parcel.", arabadan yeniden alınabilmeli.
8. Üç teslimat sonrası "Shift complete", arabada "Restart shift".
9. Test → Clients and Servers → 2 oyuncu: birinin paketi/hedefi/kazancı diğerini etkilememeli; korku yalnızca teslim eden oyuncuda.
10. Device emülatöründe telefon yatay: cihaz ekranın sağ üçte birinde, 6 sn sonra başlığa katlanmalı; istem balonuna dokunmak çalışmalı.
11. Output'ta yalnızca `[MidnightDelivery] No audio assigned yet: ...` bilgi satırı olmalı.

Bir adım beklenenden farklıysa Output'taki satırı ve adımı bana ilet; oradan devam ederim.

## 5. Açık kalan eksikler ve riskler

- **Sesler yok** (bkz. `ASSETS.md`); korku olayı şu an yalnızca görsel.
- **Işık değerleri doğrulanmadı.** Başlangıç değerleri: noktasal ışık 1.0 / menzil 18, ambient `#221F1C`. Studio'da ince ayar gerekebilir.
- **Paket tutuş ofseti** gerçek avatarda denenmedi (`Config.ParcelGrip.R15`).
- Dosya güncel Roblox formatında yazıldı (API 0.728; UICorner köşe özellikleri). Güncel Studio'da açılmalı.
- Asansör 10×10 olduğu için doğuşta kamera karaktere yakın; önizlemede kabul edilebilir göründü, Studio'da bakılmalı.
