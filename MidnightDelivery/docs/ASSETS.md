# Varlıklar ve lisanslar

## Harici varlık: yok

- **Modeller:** Hepsi bu projede Studio parçalarıyla (Part/Cylinder/Ball) kod üzerinden
  üretildi. Toolbox, Creator Store, Meshy veya başka üretim servisi kullanılmadı; kredi harcanmadı.
- **Dokular / Decal / Mesh:** Yok. Yüzeyler Roblox'un yerleşik malzemeleri (Plaster, Wood,
  WoodPlanks, Carpet, Fabric, Rubber, Metal, CorrodedMetal, Cardboard, SmoothPlastic, Neon).
  Tüm yüzeyler Smooth; stud dokusu yok.
- **Font:** Builder Sans (`rbxasset://fonts/families/BuilderSans.json`), Roblox'la gelen yerleşik font.
- **Betikler:** Hepsi bu projede yazıldı; üçüncü taraf kod, eklenti veya paket yok.

## Sesler: eksik

Ses dosyası eklenmedi. Lisansı doğrulanmış kaynak olmadığı için rastgele bir ses kimliği
kullanılmadı. Oyun ses kimliği boş olan sesleri atlar; korku olayındaki kapı sarsılması ve
lamba titremesi sessiz de çalışır. Output'ta bir kez bilgi satırı basılır:
`[MidnightDelivery] No audio assigned yet: ...`

| Anahtar (`Config.Sounds`) | Nerede çalar | Öneri |
|---|---|---|
| `Knock` | Korku: kapının arkasından kısa vurma (103) | 3 kısa, boğuk tahta vuruşu, ~0.6 sn |
| `Thud` | Korku: oyuncu uzaklaşınca kapıdan tek ses | tek, alçak, boğuk darbe |
| `Ding` | Asansör kapısı açılırken | kısa, yumuşak zil |
| `ElevatorDoor` | Asansör kapısı hareketi | ~1.4 sn kayma sesi |
| `Delivered` | Paket kapıya bırakılınca | kısa karton "tık" |
| `Pickup` | Paket arabadan alınınca | kısa karton sürtünmesi |

Eklerken: kendi yüklediğin ya da Creator Store'da kullanım izni açık olan sesleri seç,
kimliği `Config.Sounds` içine `"rbxassetid://..."` biçiminde yaz ve kaynağını bu tabloya ekle.
Ses seviyeleri düşük tutuldu (0.35–0.6); korku sesi ani ve yüksek olmamalı.
