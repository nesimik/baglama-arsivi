# Eski "Bağlama Arşivim"den yeni "Bağlama Arşivi"ne veri aktarma

Eski uygulama Google AI Studio'nun gizli imza anahtarıyla imzalandı. Android, farklı anahtarla imzalanmış
bir uygulamanın eskisinin üstüne kurulmasına izin vermiyor. Bu yüzden yeni uygulama **ayrı bir uygulama olarak**
(ikisi yan yana) kurulur ve veriler **bir kez** aktarılır. Bundan sonraki tüm GitHub sürümleri aynı sabit
anahtarla imzalandığı için **her zaman eskisinin üstüne güncelleme olarak** kurulur, veriler kaybolmaz.

---

## 1. yol (önerilen): AI Studio ile eski uygulamaya "Tam Yedek" ekle

AI Studio'da eski projeyi aç ve Gemini'ye aşağıdaki metni **olduğu gibi** yapıştır:

```
Uygulamaya "Tam Yedek Dışa Aktar" özelliği ekle. Başka hiçbir şeyi değiştirme.

1) app/src/main/java/com/example/util/FullBackupExporter.kt adında yeni bir dosya oluştur ve içine
   aşağıdaki kodu birebir koy.
2) SettingsScreen.kt içinde "Şimdi Yedekle" butonunun onClick'ini şöyle değiştir:
   - rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"))
     ile bir launcher oluştur; dönen uri null değilse
     scope.launch(Dispatchers.IO) { FullBackupExporter.export(context, uri) } çalıştır,
     bitince Toast ile "Tam yedek hazır" göster, hata olursa hatayı Toast ile göster.
   - Butona basınca launcher.launch("BaglamaArsivim_tam_yedek.zip") çağır.
3) Uygulama sürüm kodunu (versionCode) 1 artır.
```

Gemini'ye verilecek kod (`FullBackupExporter.kt`):

```kotlin
package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FullBackupExporter {
    fun export(context: Context, outUri: Uri) {
        val db = com.example.data.db.AppDatabase.getDatabase(context)
        try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (_: Exception) {}
        val out = context.contentResolver.openOutputStream(outUri, "wt")
            ?: throw IllegalStateException("Dosya oluşturulamadı")
        ZipOutputStream(out.buffered(256 * 1024)).use { zip ->
            val dbFile = context.getDatabasePath("baglama_arsivim.db")
            for (suffix in listOf("", "-wal")) {
                val f = File(dbFile.path + suffix)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("databases/baglama_arsivim.db$suffix"))
                    f.inputStream().use { it.copyTo(zip, 128 * 1024) }
                    zip.closeEntry()
                }
            }
            zip.setLevel(Deflater.NO_COMPRESSION)
            for (dir in listOf("videos", "documents", "thumbnails")) {
                val base = File(context.filesDir, dir)
                base.walkTopDown().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry("files/$dir/" + f.relativeTo(base).path))
                    f.inputStream().use { it.copyTo(zip, 128 * 1024) }
                    zip.closeEntry()
                }
            }
        }
    }
}
```

Sonra:
1. AI Studio'dan uygulamayı telefona kur. **Aynı anahtarla imzalandığı için eskisinin üstüne güncellenir, verilerin kalır.**
2. Eski uygulamada Ayarlar → **Şimdi Yedekle** → kaydedilecek yeri seç (ör. İndirilenler).
3. Yeni **Bağlama Arşivi** uygulamasını aç → Ayarlar → **Eski “Bağlama Arşivim” uygulamasından aktar** → oluşan zip dosyasını seç.
4. Her şeyin geldiğini kontrol et, sonra eski uygulamayı silebilirsin.

## 2. yol: Bilgisayar + USB kablo (adb)

Telefonda Geliştirici seçenekleri → USB hata ayıklama açık olmalı.

```
adb exec-out run-as com.aistudio.baglamaarsivim.zpkrv tar c databases files > eski_arsiv.tar
adb push eski_arsiv.tar /sdcard/Download/
```

Yeni uygulamada Ayarlar → Eski uygulamadan aktar → `eski_arsiv.tar` dosyasını seç.

## 3. yol: Videoları yeniden paylaş

WhatsApp'taki orijinal videolar hâlâ duruyorsa, WhatsApp'ta videoları seç → Paylaş → **Bağlama Arşivi**.
(Türkü bilgileri ve notlar bu yolla gelmez, elle girilir.)
