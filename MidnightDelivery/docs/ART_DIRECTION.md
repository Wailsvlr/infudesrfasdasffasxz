# Midnight Delivery — Sanat Yönü ve UI Yerleşim Taslağı (Bölüm 1)

Kullanıcı referansı verilmedi; bu not, brief'teki sanat yönünü esas alır. Uygulama bu
dosyadaki değerlere bağlıdır: harita `build/map/*`, UI `build/ui/Hud.luau`, renkler
`src/shared/Palette.luau` içinde tek yerden gelir.

## 1. Ton

Eski, bakımlı ama yıpranmış bir apartman koridoru; gece yarısı. İlk bakışta sıradan.
Tekinsizlik ayrıntılardan gelir: koridorun sonu biraz daha loş, 103'ün kapısı daha
eski, tavanda küçük bir su lekesi, ilan panosunda titreyen ışıktan şikâyet eden bir not.
Detaylı ama sade Roblox ortamı; fotogerçekçilik yok, stud dokusu yok (tüm yüzeyler Smooth).

## 2. Palet ve malzemeler

| Rol | Renk | Malzeme | Not |
|---|---|---|---|
| Duvar (üst) | `#C8BDA8` | Plaster | Brief rengi |
| Duvar (alt lambri) | `#AFA28A` | Plaster | Üstten bir ton koyu, göz hizasının altını ağırlaştırır |
| Tavan | `#BEB4A2` | Plaster | Duvardan biraz koyu: lamba çevresinde patlamasın |
| Ahşap trim (süpürgelik, pervaz, lambri çıtası) | `#5B4A3C` | Wood | Kapıları çerçeveler |
| Zemin | `#4E4036` | WoodPlanks | Mat koyu kahverengi |
| Koşucu halı | `#4A3431` | Carpet | Koyu, düşük doygunluk |
| Kapı | `#30493E` | SmoothPlastic | Brief rengi; 103 soluk: `#384B42` |
| Metal | `#62666A` | Metal | Asansör, pano, posta kutusu |
| Metal (koyu / açık) | `#4C5054` / `#7E8387` | Metal | Derz ve kenarlar |
| Pirinç | `#A88A55` | Metal | Numara plakası, kol, posta yuvası |
| Pas | `#7A4A2E` | CorrodedMetal | Yalnızca kenarlarda, az |
| Karton | `#A9835A` | Cardboard | Paket |
| Lamba difüzörü | `#E3D3B3` | Neon, %25 saydam | Görünen her lambada bir ışık kaynağı var |

Kural: her yüzey aynı parlaklıkta değil. En açık yüzey duvar üstü, en koyu zemin ve kapılar;
metal ile pirinç yalnızca küçük vurgular.

## 3. Ölçek (R15 karakter ≈ 5.2 stud)

| Öğe | Değer |
|---|---|
| Koridor iç genişlik × yükseklik × uzunluk | 13 × 11 × 50 stud |
| Daire kapı açıklığı | 5 × 8 stud, kapı yüzeyi duvardan 0.3 içeride (8.5, R15 yanında dev durduğu için düşürüldü) |
| Asansör kabini (iç) | 10 × 10 stud, tavan 9.5 |
| Asansör kapı açıklığı | 6 × 8.5 stud, ortadan açılır |
| Lambri çıtası / süpürgelik | 3.6 stud yükseklikte / 0.7 stud |
| Numara plakası merkezi | 6.4 stud (kamera odağı ≈ 4.5 stud) |
| Serbest yürüme şeridi | Koridor ortasında en az 9 stud genişlik |

Yerleşim (üstten, x: koridor boyunca):

```
            KUZEY DUVARI (z = +6.5)
  x: 0    3..7        15          27            39            50
  ┌─────┬─────────────────────────────────────────────────────┐
  │     │ posta      [101]       ilan         [103]            │
  │ AS. D kutuları                panosu                   pencere
  │ 10x10 ═════════════ koşucu halı ═════════════════════   + radyatör
  │     │ paket                    [102]      elektrik         │
  │     │ arabası                              panosu          │
  └─────┴─────────────────────────────────────────────────────┘
            GÜNEY DUVARI (z = -6.5)       lambalar: x = 6, 16, 26, 36, 46
```

Kapılar karşılıklı değil, şaşırtmalı: her kapının önünde kendi lambası var.
Daire kapıları aynı aileden ama aynı kopya değil:

- **101:** iki panelli, pirinç kol, dürbün, pirinç tekmelik; koko "WELCOME" paspası.
- **102:** dört panelli, topuz + ek kilit, pirinç mektup yuvası; kaburgalı gri lastik paspas.
- **103:** daha eski ve soluk, koyu demir kol, iki kilit, kilit çevresinde çizikler,
  hafif eğik plaka; eskimiş kiremit rengi paspas, hafif yamuk.

## 4. Işık

Önce normal ışık, sonra korku efekti.

- `Lighting.Technology = Future`, `ClockTime = 0`, `Ambient = #221F1C`
  (gölgeler siyaha gömülmesin), `EnvironmentDiffuseScale = 0`, sis ve Atmosphere yok, Bloom yok.
- Koridor: 5 tavan armatürü, her birinde bir `PointLight` (renk `255,231,196`, parlaklık 1.0,
  menzil 18, gölge açık). Renk sıcak ama turuncu değil (~3500K).
- Kabin: bir tavan paneli, biraz daha nötr (`255,240,222`), parlaklık 0.8.
- Koridor sonu penceresi: dışarıdan soğuk, çok zayıf bir ışık (`150,170,205`, 0.35).
  İçeri güneş girmez: bina tamamen kapalı bir kabuk.
- Son armatür (x = 46) biraz daha zayıf (0.8): koridorun sonu belli belirsiz daha loş.
- Hafif ColorCorrection: kontrast +0.04, doygunluk −0.06.

## 5. UI dili: "teslimat cihazı"

Tek font ailesi: **Builder Sans** (Regular / Medium / Bold). Boşluk ölçeği 4 · 8 · 12 · 16.
Köşe yarıçapı 6 (panel), 4 (tuş rozeti). Çizgiler 1 px. Gradient yok.

| Token | Değer |
|---|---|
| Gövde | `#202622`, %6 saydam |
| Çizgi | `#3A433D` |
| Metin | `#EEE9DC` (ikincil: %35 saydam, sönük: %55 saydam) |
| Vurgu | `#D6AD62` (aktif adım, kazanç, hedef işareti) |
| Uyarı | `#C9826B` (yalnızca "Wrong apartment" şeridi) |

Yazı hiyerarşisi: etiket 12 Bold BÜYÜK HARF · gövde 15 Regular · başlık 22 Bold ·
bildirim başlığı 17 Bold · kazanç değeri 16 Bold.

## 6. UI yerleşim taslağı (1920×1080 tasarım, piksel)

```
┌─ Roblox üst çubuğu (58 px) ─────────────────────────────────────────────────────┐
│ [≡][💬]  MIDNIGHT DELIVERY                          SHIFT EARNINGS   $25        │ ← TopbarSafeInsets
│          NIGHT 01                                                               │   (Roblox düğmeleriyle çakışmaz)
├──────────────────────────────────────────────────────────────────────────────────┤
│ (Roblox sohbet penceresi                      ┌─ 320 px ─────────────────────┐   │
│  varsayılan yerinde, sol üst)                 │ ● COURIER DEVICE       [TAB] │   │ ← cihaz: sağ üst,
│                                               ├──────────────────────────────┤   │   16 px kenar boşluğu
│                                               │ CURRENT DELIVERY       1 / 3 │   │
│                                               │ Apartment 102                │   │
│                                               │ Pick up the parcel.          │   │
│                                               │ 1 PICK UP   ·   2 DELIVER    │   │
│                                               └──────────────────────────────┘   │
│                                                                                  │
│                         (ekran merkezi boş: görüş alanı)                          │
│                                                                                  │
│                        ┌─[E]  Apartment 102 ───────┐   ← özel ProximityPrompt,     │
│                        │      Deliver parcel       │     hedefin üzerinde (dünyada)│
│                        └───────────────────────────┘                             │
│                     ┃ Delivery complete                ← sonuç bildirimi,          │
│                     ┃ +$25 · Apartment 102                y = %80, 2.4 sn          │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- **Cihaz kapalıyken** yalnızca başlık satırı kalır: `● COURIER DEVICE   Apt 102   [TAB]`.
  Mobilde `[TAB]` yerine `SHOW` / `HIDE` yazan aynı satır dokunmatik düğmedir.
- **Ölçek:** `UIScale = clamp(ekranYüksekliği / 720, 0.85, 1)`; genişlik ayrıca
  `min(320, ekranGenişliği × 0.42)`. Küçük ekranlarda (yükseklik < 500) cihaz yeni görevi
  gösterdikten 6 sn sonra kendini başlık satırına katlar.
- **Tek gösterim kuralı:** ProximityPrompt stili `Custom`; varsayılan Roblox istemi hiç
  çizilmez. Hedef işareti (küçük amber elmas + numara) oyuncu hedefe 9 stud'dan fazla
  uzakken görünür, yakında gizlenir; istem ile işaret aynı anda görünmez.
- **Kapatılan Roblox arayüzü:** PlayerList (sağ üstte cihazla çakışırdı), Backpack
  (envanter yok), Health (hasar yok). Sohbet açık ve varsayılan yerinde kalır.

## 7. Metinler (oyuncuya görünen, İngilizce)

`MIDNIGHT DELIVERY`, `NIGHT 01`, `SHIFT EARNINGS`, `COURIER DEVICE`, `CURRENT DELIVERY`,
`Apartment 102`, `Pick up the parcel.`, `Deliver to Apartment 102.`, `Delivery complete`,
`Wrong apartment`, `This parcel is for Apartment 102.`, `Shift complete`,
`All parcels delivered. Return to the parcel cart to start again.`,
`Pick up parcel`, `Deliver parcel`, `Restart shift`, `Call elevator`.
