# Kurulum (Türkçe)

Sadece tek kişilik (single-player / offline) mod. Online'da kullanma.

## 1. Gerekenler

- GTA V (Script Hook V'nin desteklediği sürüm). Script Hook V'yi buradan indir: <http://www.dev-c.com/gtav/scripthookv/>
  Paketin içinden `ScriptHookV.dll` ve ASI loader dosyasını oyunun ana klasörüne, `GTA5.exe`'nin yanına kopyala (loader'ın adını hatırlayarak `dinput8.dll` diye yazıyorum, paketin kendi okuma dosyasına bak).
- Script Hook V'nin senin oyun sürümünü desteklediğini kontrol et. Ben bunu doğrulayamadım. Sürüm uyuşmuyorsa oyun açılırken hata verir veya mod hiç yüklenmez.
- Jiggle için kemiği olan bir ped. Vanilla pedlerde göğüs/kalça kemiği olmayabilir. Bu tür modlar genelde ekstra kemikli add-on ped kullanır. Kemik adlarını CodeWalker'da modelin iskeletinden oku.

## 2. Dosyayı al

GitHub'da repo > **Actions** > `gtav-jiggle` > son başarılı çalışma > **Artifacts** > `Jiggle-asi`. Zip'in içinde `Jiggle.asi` ve `Jiggle.ini` var.
(Kendin derlemek istersen `README.md` içindeki build komutları.)

## 3. Kopyala

`Jiggle.asi` ve `Jiggle.ini` dosyalarını `GTA5.exe`'nin yanına (ASI loader'ın okuduğu klasöre) koy. `Jiggle.log` aynı klasörde oluşur.

## 4. İlk çalıştırma: offsetleri yakala

Bu adımlar yapılmadan mod hiçbir şey yazmaz (kapalı kalır, bu kasıtlı).

1. `Jiggle.ini` içinde `[Probe]` bölümündeki `Bones=` satırına, oynadığın pedde var olan en az iki kemik adını yaz (virgülle ayır, aralarında en az ~0.1 m mesafe olsun).
2. Oyunu aç, tek kişilik moda gir, karakterle dik ve hareketsiz dur, **F7**'ye bas.
3. `Jiggle.log` dosyasını aç:
   - `main module:` satırındaki `SizeOfImage` ve `TimeDateStamp` değerlerini `[Build]` bölümündeki `ExpectedImageSize` ve `ExpectedTimeDateStamp` alanlarına yaz.
   - `probe: hit` satırlarından `chain`, `Bone0TranslationOffset` ve `stride` değerlerini `[Skeleton]` bölümündeki `Chain`, `Bone0TranslationOffset`, `MatrixStride` alanlarına yaz. `MaxBoneIndex` değerini ped iskeletinin en yüksek kemik indeksi olarak gir.
4. Sallanacak kemikleri `[Bone.Isim]` bölümlerine `Name=` ile ekle (uç/leaf kemik seç).
5. Oyunda **F8** ile INI'yi yeniden yükle.

## 5. Test

- **F9**'a basılı tut: ayarlı kemikler yaklaşık 10 cm yukarı zıplamalı. Zıplamıyorsa o hit işe yaramadı, `Jiggle.log`'daki sıradaki hit'i dene.
- Zıplıyorsa **F6** ile fiziği aç/kapat. Bip sesi: normal bip = açık/tamam, yıldız sesi = kapalı, el işareti sesi = kapalı kapı veya hata (sebep `Jiggle.log`'da).
- Fizik değerleri (`FrequencyHz`, `DampingRatio`, `MaxDisplacement`, `Gain`) kalibre edilmemiş başlangıç değerleridir, gözle ayarla.

## Sorun olursa

`Jiggle.log` dosyasının içeriğini (özellikle `main module:` ve `probe:` satırlarını) bana yapıştır.
