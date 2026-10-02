# 🎸 Bağlama Arşivi

Bağlama derslerinin videoları, notaları ve çalışma takibi için kişisel Android uygulaması
(Kotlin + Jetpack Compose). Tüm veriler telefonda saklanır, internet gerekmez.

## APK'yı indirme
- **Her zaman en son sürüm (sabit link):** https://github.com/nesimik/baglama-arsivi/releases/latest/download/BaglamaArsivi.apk
- **En kolayı:** Sağdaki **Releases** bölümünden en son sürümün `.apk` dosyasını telefondan indir ve kur.
- Ya da **Actions** sekmesi → en son başarılı derleme → *Artifacts*.

Her yeni sürüm **aynı sabit imza anahtarıyla** (`app/imza/baglama-arsivi.jks`) imzalanır ve sürüm numarası
otomatik artar. Bu yüzden yeni APK her zaman eskisinin **üstüne güncelleme olarak** kurulur, verilerin silinmez.
> ⚠️ `app/imza/` klasöründeki anahtar dosyasını silme veya değiştirme. Değişirse güncellemeler kurulamaz.

## Özellikler
- **Türküler:** yöre, makam, sanatçı, seviye, durum (Çalışıyorum / Öğrenilecek / Öğrendim), etiketler, kişisel notlar
- **Ders videoları:** WhatsApp'tan *Paylaş → Bağlama Arşivi* ile tek seferde 60 videoya kadar ekleme, toplu isim ve numara verme,
  hiyerarşik sıra numaraları (1, 1.2, 2/1 …) ve bölüm renkleri (açık zeminli kartlar)
- **Gelişmiş oynatıcı:** 0.25x–2x hız (ses perdesi korunur), **A-B bölüm tekrarı**, **ayna görüntü** (hocayı ayna gibi izle),
  çift dokunmayla ±5 sn, kaldığın yerden devam, otomatik sonraki video, tam ekran
- **Video işaretleri:** “1:23 sol el geçişi” gibi zaman işaretleri; dokununca o ana gider, A noktası yapılabilir
- **Çalışma köşesi:** süre tutucu, günlük/haftalık süre, 🔥 gün serisi, türkü bazında toplam çalışma
- **Metronom:** 30–260 BPM, 2–9 vuruşlu ölçüler (aksak ritimler), tempoya dokunarak BPM bulma
- **Notalar & belgeler:** kategori (Nota, Söz, Akor, Ses Kaydı …), seçilen uygulamayla açma ve hatırlama, paylaşma
- **Genel arama:** türkü, video, belge ve ders tarihine göre
- **Tam yedekleme / geri yükleme:** tüm veritabanı + videolar + belgeler tek bir `.zip` dosyasına
- Çöp kutusu (30 gün), depolama yönetimi, aydınlık / karanlık tema

## Güncellemeler ve verilerin
Yeni sürüm eskisinin üstüne kurulur; türküler, videolar, numaralar, işaretler ve çalışma kayıtları aynen kalır.
Veritabanı değişiklikleri yalnızca ekleme yapar (hiçbir zaman silmez) ve her güncellemeden önce veritabanının güvenlik kopyası alınır.
