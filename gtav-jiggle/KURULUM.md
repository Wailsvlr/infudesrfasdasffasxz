# Kurulum (Türkçe)

Sadece tek kişilik (offline) mod. Online'da kullanma.

## 1. Gerekenler
- GTA V (Epic, Enhanced) ve **Script Hook V**: <http://www.dev-c.com/gtav/scripthookv/>. `ScriptHookV.dll` ve paketle gelen ASI loader'ı oyunun `.exe` dosyasının yanına kopyala (paketin kendi okuma dosyasına bak).
- Script Hook V'nin senin oyun build'ini desteklemesi şart. Build numarasını `.exe` dosyasına sağ tık, Özellikler, Ayrıntılar'dan oku ve Script Hook V sayfasındaki desteklenen sürümle karşılaştır. Desteklenmiyorsa hiçbir `.asi` mod yüklenmez.

## 2. Dosyaları al
GitHub'da repo > **Actions** > `gtav-jiggle` > en son yeşil çalışma > en alttaki **Artifacts** > `Jiggle-asi`. İçinde `Jiggle.asi` ve `Jiggle.ini` var.

## 3. Kopyala
`Jiggle.asi` ve `Jiggle.ini` dosyalarını `.exe`'nin yanına koy. `Jiggle.log` aynı klasörde oluşur.

## 4. Oyunda (otomatik kurulum)
1. Tek kişilik moda gir. Hareket edecek kemikleri olan bir karakterle oyna (varsayılan Michael, Franklin ve Trevor'da göğüs kemiği yok, bu mod orada bir şey sallamaz).
2. Karakterle dik ve hareketsiz dur, **F7**'ye bas.
3. Mod, bellekteki kemik tablosunu kendisi bulur, `Jiggle.auto.ini` dosyasına yazar ve yaklaşık 2,5 saniye boyunca ayarlı kemikleri yukarı kaldırarak test eder.
4. Kemikler **zıpladıysa** kurulum doğru. **F6** ile fiziği aç/kapat.
5. **Zıplamadıysa** F7'ye tekrar bas, sıradaki adayı dener. Her adım `Jiggle.log` dosyasında yazılı.

Tuşlar: F6 aç/kapat, F7 kurulum, F8 INI'yi yeniden yükle, F9 basılı tut = zıplatma testi.

## 5. Kemik adları
`Jiggle.ini` içinde `SPR_L_Breast` ve `SPR_R_Breast` **doğrulanmamış aday adlar** (hatırladığım kadarıyla). İlk F7'den sonra `Jiggle.log` dosyasında `jiggle bone '...' -> index N` satırları çıkar. Numaralar 125 ve 126 ise adlar doğrudur. "not on this ped model" yazarsa adı `Jiggle.ini` içinde değiştir.

## Sorun olursa
`Jiggle.log` dosyasının tamamını bana yapıştır (en önemlisi `probe:` ve `gate:` satırları).

## Paket hakkında
Elindeki FiveM paketi (`EnhancedModelsFM`) bu mod değil. Tek kişilik Enhanced'a kurulumu için format dönüştürme ve paketleme gerekir ve bunu denemedim.
