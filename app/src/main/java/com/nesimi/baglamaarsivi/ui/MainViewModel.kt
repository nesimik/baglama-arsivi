package com.nesimi.baglamaarsivi.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nesimi.baglamaarsivi.BaglamaApp
import com.nesimi.baglamaarsivi.backup.BackupManager
import com.nesimi.baglamaarsivi.backup.BackupProgress
import com.nesimi.baglamaarsivi.data.AppThemeMode
import com.nesimi.baglamaarsivi.data.DocumentCategory
import com.nesimi.baglamaarsivi.data.DocumentItem
import com.nesimi.baglamaarsivi.data.FilterOption
import com.nesimi.baglamaarsivi.data.PracticeSession
import com.nesimi.baglamaarsivi.data.SortOption
import com.nesimi.baglamaarsivi.data.StudyStatus
import com.nesimi.baglamaarsivi.data.Turku
import com.nesimi.baglamaarsivi.data.TurkuWithDetails
import com.nesimi.baglamaarsivi.data.VideoItem
import com.nesimi.baglamaarsivi.data.VideoMarker
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Metronome
import com.nesimi.baglamaarsivi.util.StorageStats
import com.nesimi.baglamaarsivi.util.Tr
import com.nesimi.baglamaarsivi.util.PracticeTimer
import com.nesimi.baglamaarsivi.util.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data object Home : Screen()
    data object TurkuList : Screen()
    data object Practice : Screen()
    data object Documents : Screen()
    data object Settings : Screen()
    data object Favorites : Screen()
    data object Trash : Screen()
    data object Storage : Screen()
    data object Search : Screen()
    data object Migration : Screen()
    data class TurkuDetail(val turkuId: Long) : Screen()
    data class Player(val videoId: Long) : Screen()
    data class Import(
        val uris: List<Uri> = emptyList(),
        val isVideo: Boolean = true,
        val turkuId: Long? = null
    ) : Screen()
}

data class PracticeStats(val todaySec: Long = 0, val weekSec: Long = 0, val totalSec: Long = 0, val streakDays: Int = 0)

data class BusyState(val title: String, val progress: BackupProgress? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as BaglamaApp).repository
    private val prefs = app.getSharedPreferences("baglama_arsivi_ayarlar", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- Mesajlar
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun toast(msg: String) { _messages.tryEmit(msg) }

    // ---------------------------------------------------------------- Tema
    private val _theme = MutableStateFlow(
        runCatching { AppThemeMode.valueOf(prefs.getString("tema", AppThemeMode.LIGHT.name)!!) }.getOrDefault(AppThemeMode.LIGHT)
    )
    val theme: StateFlow<AppThemeMode> = _theme.asStateFlow()
    fun setTheme(m: AppThemeMode) { _theme.value = m; prefs.edit().putString("tema", m.name).apply() }

    // ---------------------------------------------------------------- Gezinme
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()
    fun navigate(s: Screen) { val cur = _stack.value; if (cur.lastOrNull() != s) _stack.value = cur + s }
    fun back(): Boolean { val cur = _stack.value; return if (cur.size > 1) { _stack.value = cur.dropLast(1); true } else false }
    fun switchTab(s: Screen) { _stack.value = listOf(s) }

    // ---------------------------------------------------------------- Liste filtreleri
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _filter = MutableStateFlow(FilterOption.ALL)
    val filter: StateFlow<FilterOption> = _filter.asStateFlow()
    fun setFilter(f: FilterOption) { _filter.value = f }

    private val _sort = MutableStateFlow(runCatching { SortOption.valueOf(prefs.getString("siralama", SortOption.MANUAL.name)!!) }.getOrDefault(SortOption.MANUAL))
    val sort: StateFlow<SortOption> = _sort.asStateFlow()
    fun setSort(s: SortOption) { _sort.value = s; prefs.edit().putString("siralama", s.name).apply() }

    private val _compact = MutableStateFlow(prefs.getBoolean("kompakt", false))
    val compact: StateFlow<Boolean> = _compact.asStateFlow()
    fun toggleCompact() { _compact.value = !_compact.value; prefs.edit().putBoolean("kompakt", _compact.value).apply() }

    // ---------------------------------------------------------------- Veriler
    val turkus: StateFlow<List<TurkuWithDetails>> = repo.turkusWithDetails.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val videos: StateFlow<List<VideoItem>> = repo.activeVideos.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents: StateFlow<List<DocumentItem>> = repo.activeDocs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentlyWatched: StateFlow<List<VideoItem>> = repo.recentlyWatched.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions: StateFlow<List<PracticeSession>> = repo.practiceSessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val deletedTurkus = repo.deletedTurkus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedVideos = repo.deletedVideos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedDocs = repo.deletedDocs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTurkus: StateFlow<List<TurkuWithDetails>> =
        combine(turkus, _query, _filter, _sort) { list, q, f, s ->
            var r = list
            if (q.isNotBlank()) r = r.filter {
                val t = it.turku
                Tr.contains(t.name, q) || Tr.contains(t.region, q) || Tr.contains(t.artist, q) || Tr.contains(t.makam, q) ||
                    Tr.contains(t.tags, q) || Tr.contains(t.description, q) || Tr.contains(t.personalNote, q)
            }
            r = when (f) {
                FilterOption.ALL -> r
                FilterOption.FAVORITES -> r.filter { it.turku.isFavorite }
                FilterOption.STUDYING -> r.filter { it.turku.status == StudyStatus.CALISIYORUM.name }
                FilterOption.TO_LEARN -> r.filter { it.turku.status == StudyStatus.OGRENILECEK.name }
                FilterOption.LEARNED -> r.filter { it.turku.status == StudyStatus.OGRENILDI.name }
                FilterOption.HAS_VIDEOS -> r.filter { it.videoCount > 0 }
                FilterOption.HAS_DOCUMENTS -> r.filter { it.documentCount > 0 }
            }
            when (s) {
                SortOption.MANUAL -> r.sortedBy { it.turku.manualOrder }
                SortOption.NAME_ASC -> r.sortedWith { a, b -> Tr.compare(a.turku.name, b.turku.name) }
                SortOption.NAME_DESC -> r.sortedWith { a, b -> Tr.compare(b.turku.name, a.turku.name) }
                SortOption.DATE_DESC -> r.sortedByDescending { it.turku.createdAt }
                SortOption.DATE_ASC -> r.sortedBy { it.turku.createdAt }
                SortOption.LESSON_DATE_DESC -> r.sortedByDescending { Tr.lessonDateKey(it.lastVideoDate) }
                SortOption.PRACTICE_DESC -> r.sortedByDescending { it.practiceSec }
                SortOption.FAVORITES_FIRST -> r.sortedWith(compareByDescending<TurkuWithDetails> { it.turku.isFavorite }.thenBy { it.turku.manualOrder })
                SortOption.STUDYING_FIRST -> r.sortedWith(compareByDescending<TurkuWithDetails> { it.turku.status == StudyStatus.CALISIYORUM.name }.thenBy { it.turku.manualOrder })
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val practiceStats: StateFlow<PracticeStats> = sessions.map { list ->
        val today = Tr.startOfToday()
        val week = Tr.startOfWeek()
        val days = list.map { Tr.dayKey(it.startedAt) }.toSet()
        var streak = 0
        val cal = java.util.Calendar.getInstance()
        if (!days.contains(Tr.dayKey(cal.timeInMillis))) cal.add(java.util.Calendar.DAY_OF_YEAR, -1) // bugün henüz çalışılmadıysa dünden say
        while (days.contains(Tr.dayKey(cal.timeInMillis))) {
            streak++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        PracticeStats(
            todaySec = list.filter { it.startedAt >= today }.sumOf { it.durationSec },
            weekSec = list.filter { it.startedAt >= week }.sumOf { it.durationSec },
            totalSec = list.sumOf { it.durationSec },
            streakDays = streak
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PracticeStats())

    fun turkuFlow(id: Long): Flow<Turku?> = repo.turku(id)
    fun videosFor(turkuId: Long): Flow<List<VideoItem>> = repo.videosForTurku(turkuId)
    fun docsFor(turkuId: Long): Flow<List<DocumentItem>> = repo.docsForTurku(turkuId)
    fun videoFlow(id: Long): Flow<VideoItem?> = repo.video(id)
    fun markersFor(videoId: Long): Flow<List<VideoMarker>> = repo.markers(videoId)
    fun practiceFor(turkuId: Long): Flow<List<PracticeSession>> = repo.practiceForTurku(turkuId)

    // ---------------------------------------------------------------- Depolama
    private val _storage = MutableStateFlow(StorageStats())
    val storage: StateFlow<StorageStats> = _storage.asStateFlow()
    fun refreshStorage() { viewModelScope.launch(Dispatchers.IO) { _storage.value = FileManager.stats(getApplication()) } }
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = FileManager.clearCache(getApplication())
            refreshStorage()
            toast("Önbellek temizlendi (${Tr.size(freed)})")
        }
    }

    init {
        PracticeTimer.load(app)
        com.nesimi.baglamaarsivi.util.TimerService.channels(app)
        viewModelScope.launch {
            runCatching { repo.purgeOldTrash() }
            refreshStorage()
            runCatching { repo.repairVideos() }
        }
    }

    // ---------------------------------------------------------------- Türkü işlemleri
    fun saveTurku(edited: Turku, openAfterCreate: Boolean = true, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            if (edited.id == 0L) {
                val id = repo.insertTurku(edited.copy(manualOrder = turkus.value.size, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                toast("\"${edited.name}\" eklendi")
                if (openAfterCreate) navigate(Screen.TurkuDetail(id))
                onSaved(id)
            } else {
                repo.updateTurku(edited.copy(updatedAt = System.currentTimeMillis()))
                toast("\"${edited.name}\" güncellendi")
                onSaved(edited.id)
            }
        }
    }

    fun toggleTurkuFavorite(t: Turku) = viewModelScope.launch {
        repo.setTurkuFavorite(t.id, !t.isFavorite)
        toast(if (!t.isFavorite) "Favorilere eklendi ⭐" else "Favorilerden çıkarıldı")
    }

    fun setTurkuStatus(id: Long, s: StudyStatus) = viewModelScope.launch { repo.setTurkuStatus(id, s); toast("Durum: ${s.displayName}") }
    fun saveNote(id: Long, note: String) = viewModelScope.launch { repo.setTurkuNote(id, note); toast("Not kaydedildi") }

    fun deleteTurku(id: Long) = viewModelScope.launch {
        repo.softDeleteTurku(id)
        toast("Türkü çöp kutusuna taşındı (30 gün saklanır)")
        back()
    }

    fun moveTurku(from: Int, to: Int) = viewModelScope.launch {
        val list = turkus.value.map { it.turku }.sortedBy { it.manualOrder }.toMutableList()
        if (from in list.indices && to in list.indices) {
            list.add(to, list.removeAt(from))
            repo.reorderTurkus(list)
        }
    }

    // ---------------------------------------------------------------- Video işlemleri
    fun toggleVideoFavorite(v: VideoItem) = viewModelScope.launch { repo.setVideoFavorite(v.id, !v.isFavorite) }
    fun setVideoOrderTag(id: Long, tag: String) = viewModelScope.launch {
        repo.setVideoOrderTag(id, tag)
        toast(if (tag.isBlank()) "Sıra numarası kaldırıldı" else "Sıra: #${tag.trim()}")
    }
    fun updateVideo(v: VideoItem) = viewModelScope.launch { repo.updateVideo(v); toast("Video bilgileri kaydedildi") }
    fun deleteVideo(id: Long) = viewModelScope.launch { repo.softDeleteVideo(id); toast("Video çöp kutusuna taşındı"); refreshStorage() }
    fun savePosition(id: Long, pos: Long) = viewModelScope.launch { repo.setVideoPosition(id, pos) }
    fun saveDuration(id: Long, dur: Long) = viewModelScope.launch { repo.setVideoDuration(id, dur) }

    /** Bir videoyu sıradaki numaraları kaydırarak yukarı/aşağı taşır (numarasızlara sıra numarası verir). */
    fun renumberVideos(ordered: List<VideoItem>) = viewModelScope.launch {
        ordered.forEachIndexed { i, v -> repo.setVideoOrderTag(v.id, (i + 1).toString()) }
        toast("Videolar 1'den ${ordered.size}'e kadar yeniden numaralandı")
    }

    fun addMarker(videoId: Long, pos: Long, label: String) = viewModelScope.launch {
        repo.addMarker(videoId, pos, label.ifBlank { "İşaret ${Tr.duration(pos)}" })
        toast("İşaret eklendi: ${Tr.duration(pos)}")
    }
    fun updateMarker(m: VideoMarker) = viewModelScope.launch { repo.updateMarker(m) }
    fun deleteMarker(id: Long) = viewModelScope.launch { repo.deleteMarker(id) }

    // ---------------------------------------------------------------- Belge işlemleri
    fun toggleDocFavorite(d: DocumentItem) = viewModelScope.launch { repo.setDocFavorite(d.id, !d.isFavorite) }
    fun updateDoc(d: DocumentItem) = viewModelScope.launch { repo.updateDoc(d); toast("Belge güncellendi") }
    fun deleteDoc(id: Long) = viewModelScope.launch { repo.softDeleteDoc(id); toast("Belge çöp kutusuna taşındı"); refreshStorage() }

    // ---------------------------------------------------------------- Çöp kutusu
    fun restoreTurku(id: Long) = viewModelScope.launch { repo.restoreTurku(id); toast("Geri yüklendi") }
    fun restoreVideo(id: Long) = viewModelScope.launch { repo.restoreVideo(id); toast("Geri yüklendi") }
    fun restoreDoc(id: Long) = viewModelScope.launch { repo.restoreDoc(id); toast("Geri yüklendi") }
    fun deleteTurkuForever(id: Long, deleteFiles: Boolean = false) = viewModelScope.launch {
        repo.deleteTurkuForever(id, deleteFiles); refreshStorage()
        toast(if (deleteFiles) "Türkü ve videoları silindi" else "Arşivden çıkarıldı (videolar telefonda duruyor)")
    }
    fun deleteVideoForever(v: VideoItem, deleteFile: Boolean = false) = viewModelScope.launch {
        repo.deleteVideoForever(v, deleteFile); refreshStorage()
        toast(if (deleteFile) "Video telefondan da silindi" else "Arşivden çıkarıldı (video telefonda duruyor)")
    }
    fun deleteDocForever(d: DocumentItem) = viewModelScope.launch { repo.deleteDocForever(d); refreshStorage(); toast("Kalıcı olarak silindi") }
    fun emptyTrash(deleteFiles: Boolean = false) = viewModelScope.launch {
        deletedVideos.value.forEach { repo.deleteVideoForever(it, deleteFiles) }
        deletedDocs.value.forEach { repo.deleteDocForever(it) }
        deletedTurkus.value.forEach { repo.deleteTurkuForever(it.id, deleteFiles) }
        refreshStorage()
        toast("Çöp kutusu boşaltıldı")
    }

    // ---------------------------------------------------------------- İçe aktarma
    data class StagedVideo(val uri: Uri, val title: String, val orderTag: String)

    private val _busy = MutableStateFlow<BusyState?>(null)
    val busy: StateFlow<BusyState?> = _busy.asStateFlow()

    // Orijinalleri silme (WhatsApp vb.) – içe aktarmadan sonra kullanıcıya sorulur
    private val _pendingOriginals = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val pendingOriginals: StateFlow<List<Pair<String, Long>>> = _pendingOriginals.asStateFlow()
    fun clearPendingOriginals() { _pendingOriginals.value = emptyList() }

    var deleteOriginalsPref: Boolean
        get() = prefs.getBoolean("orijinal_sil", true)
        set(v) { prefs.edit().putBoolean("orijinal_sil", v).apply() }

    fun importVideos(
        items: List<StagedVideo>, turkuId: Long, lessonDate: String, instructor: String,
        description: String, tags: String, status: StudyStatus?, favorite: Boolean,
        deleteOriginals: Boolean = false, onDone: () -> Unit
    ) {
        viewModelScope.launch {
            var ok = 0
            val dups = mutableListOf<String>()
            val sources = mutableListOf<Pair<String, Long>>()
            items.forEachIndexed { i, it ->
                _busy.value = BusyState("Video kaydediliyor ${i + 1}/${items.size}", BackupProgress(it.title, i.toLong(), items.size.toLong()))
                try {
                    val r = repo.importVideo(it.uri, turkuId, it.title, lessonDate, instructor, description, tags, it.orderTag, i + 1, favorite)
                    ok++
                    r.duplicateOf?.let { d -> dups += d }
                    r.source?.let { src -> sources += src }
                } catch (e: Exception) {
                    toast("“${it.title}” kaydedilemedi: ${e.message}")
                }
            }
            if (status != null) repo.setTurkuStatus(turkuId, status)
            _busy.value = null
            refreshStorage()
            toast(if (dups.isEmpty()) "$ok video arşive eklendi ✅" else "$ok video eklendi. Uyarı: ${dups.size} tanesi daha önce de eklenmiş olabilir.")
            onDone()
            if (deleteOriginals && sources.isNotEmpty() && com.nesimi.baglamaarsivi.util.VideoStore.galleryEnabled) _pendingOriginals.value = sources
        }
    }

    fun importDocuments(items: List<Pair<Uri, String>>, turkuId: Long?, category: DocumentCategory, favorite: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            var ok = 0
            items.forEachIndexed { i, (uri, title) ->
                _busy.value = BusyState("Belge kaydediliyor ${i + 1}/${items.size}", BackupProgress(title, i.toLong(), items.size.toLong()))
                try {
                    repo.importDocument(uri, turkuId, title, category, favorite); ok++
                } catch (e: Exception) {
                    toast("“$title” kaydedilemedi: ${e.message}")
                }
            }
            _busy.value = null
            refreshStorage()
            toast("$ok belge arşive eklendi ✅")
            onDone()
        }
    }

    private val _internalVideos = MutableStateFlow(0)
    val internalVideos: StateFlow<Int> = _internalVideos.asStateFlow()
    fun refreshInternalVideos() { viewModelScope.launch { _internalVideos.value = repo.internalVideoCount() } }

    fun moveVideosToGallery() {
        viewModelScope.launch {
            _busy.value = BusyState("Videolar telefonun galerisine taşınıyor…")
            val n = repo.moveInternalVideosToGallery { i, total ->
                _busy.value = BusyState("Videolar galeriye taşınıyor $i/$total", BackupProgress("", i.toLong(), total.toLong()))
            }
            _busy.value = null
            refreshStorage(); refreshInternalVideos()
            toast("$n video Filmler/Bağlama Arşivi klasörüne taşındı ✅")
        }
    }

    // ---------------------------------------------------------------- Klasör eşitleme
    private var lastSync = 0L
    /** Movies/Bağlama Arşivi klasöründeki yeni videoları arşive ekler (uygulama her öne geldiğinde). */
    fun syncFolder(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSync < 5000) return
        lastSync = now
        viewModelScope.launch {
            val n = runCatching { repo.syncArchiveFolder() }.getOrDefault(0)
            if (n > 0) { toast("📂 Klasörden $n yeni video arşive eklendi"); refreshStorage() }
            else if (force) toast("Klasörde yeni video yok")
            com.nesimi.baglamaarsivi.util.VideoStore.checkTick.value++
        }
    }


    fun relinkVideo(v: VideoItem, uri: Uri) {
        viewModelScope.launch {
            _busy.value = BusyState("Video bağlanıyor…")
            try {
                repo.relinkVideo(v, uri)
                toast("Video yeniden bağlandı ✅")
            } catch (e: Exception) {
                toast("Bağlanamadı: ${e.message}")
            } finally {
                _busy.value = null
                com.nesimi.baglamaarsivi.util.VideoStore.checkTick.value++
            }
        }
    }

    /** Dosyası silinmiş videoyu arşivden tamamen çıkarır. */
    fun removeMissingVideo(v: VideoItem) = viewModelScope.launch {
        repo.deleteVideoForever(v, deleteGalleryFile = false)
        toast("“${v.title}” arşivden kaldırıldı")
    }

    suspend fun createTurkuQuick(name: String, region: String): Long =
        repo.insertTurku(Turku(name = name.trim(), region = region.trim(), manualOrder = turkus.value.size))

    // ---------------------------------------------------------------- Yedekleme
    private val _lastBackup = MutableStateFlow(prefs.getLong("son_yedek", 0L))
    val lastBackup: StateFlow<Long> = _lastBackup.asStateFlow()

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = BusyState("Yedek alınıyor… Uygulamayı kapatmayın.")
            try {
                val n = withContext(Dispatchers.IO) {
                    BackupManager.export(getApplication(), uri) { p -> _busy.value = BusyState("Yedek alınıyor… Uygulamayı kapatmayın.", p) }
                }
                val now = System.currentTimeMillis()
                prefs.edit().putLong("son_yedek", now).apply()
                _lastBackup.value = now
                toast("Yedek hazır ✅ ($n dosya + veritabanı)")
            } catch (e: Exception) {
                toast("Yedek alınamadı: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    private val _restoreDone = MutableStateFlow<String?>(null)
    val restoreDone: StateFlow<String?> = _restoreDone.asStateFlow()

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            metronome.stop()
            _busy.value = BusyState("Geri yükleniyor… Uygulamayı kapatmayın.")
            try {
                val r = withContext(Dispatchers.IO) {
                    BackupManager.restore(getApplication(), uri) { p -> _busy.value = BusyState("Geri yükleniyor… Uygulamayı kapatmayın.", p) }
                }
                _restoreDone.value = "${r.turkuCount} türkü, ${r.videoCount} video ve ${r.documentCount} belge geri yüklendi (${r.fileCount} dosya).\n\nUygulama şimdi yeniden başlatılacak."
            } catch (e: Exception) {
                toast("Geri yükleme başarısız: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    // ---------------------------------------------------------------- Çalışma sayacı (ileri / geri sayım)
    private val ctx: Context get() = getApplication()
    val timer: StateFlow<TimerState> = PracticeTimer.state

    /** Geriye uyumluluk: sayaç çalışıyorsa başlangıç zamanı (>0), değilse 0 */
    val practiceStart: StateFlow<Long> = timer.map { if (it.isActive && !it.finished) it.startedAt.coerceAtLeast(1) else 0L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val practiceTurku: StateFlow<Long?> = timer.map { it.turkuId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var lastCountdownMinutes: Int
        get() = prefs.getInt("geri_sayim_dk", 10)
        set(v) { prefs.edit().putInt("geri_sayim_dk", v).apply() }

    fun selectPracticeTurku(id: Long?) = PracticeTimer.setTurku(ctx, id)

    /** [minutes] null ise ileri sayım (kronometre), değilse geri sayım. */
    fun startPractice(turkuId: Long?, minutes: Int? = null) {
        if (timer.value.isActive) return
        if (minutes != null) lastCountdownMinutes = minutes
        PracticeTimer.start(ctx, turkuId, minutes)
        toast(if (minutes != null) "$minutes dakikalık çalışma başladı ⏳" else "Çalışma başladı ⏱️")
    }

    fun pausePractice() = PracticeTimer.pause(ctx)
    /** Servis bir sebeple çalışmadıysa, ekran açıkken süre bitince yine de bitmiş say. */
    fun timerExpired() { if (!timer.value.finished) PracticeTimer.markFinished(ctx) }
    fun resumePractice() = PracticeTimer.resume(ctx)

    /** Sayacı durdurur; ardından "Kaydet / Kaydetme" sorulur. */
    fun stopPractice() = PracticeTimer.finish(ctx)

    fun savePractice(note: String) {
        val s = timer.value
        val sec = s.elapsed() / 1000
        com.nesimi.baglamaarsivi.util.TimerService.stopAlarm()
        PracticeTimer.reset(ctx)
        if (sec < 10) { toast("Çok kısa sürdü, kaydedilmedi"); return }
        viewModelScope.launch {
            repo.addPractice(s.turkuId, s.startedAt.takeIf { it > 0 } ?: (System.currentTimeMillis() - sec * 1000), sec, note)
            toast("${Tr.practice(sec)} çalışma kaydedildi 👏")
        }
    }

    fun discardPractice() {
        com.nesimi.baglamaarsivi.util.TimerService.stopAlarm()
        PracticeTimer.reset(ctx)
        toast("Çalışma kaydedilmedi")
    }

    fun addManualPractice(turkuId: Long?, minutes: Int, note: String) = viewModelScope.launch {
        if (minutes <= 0) return@launch
        repo.addPractice(turkuId, System.currentTimeMillis() - minutes * 60_000L, minutes * 60L, note)
        toast("$minutes dk çalışma eklendi")
    }

    fun deletePractice(id: Long) = viewModelScope.launch { repo.deletePractice(id) }

    // ---------------------------------------------------------------- Metronom
    val metronome = Metronome()
    private val _metroRunning = MutableStateFlow(false)
    val metroRunning: StateFlow<Boolean> = _metroRunning.asStateFlow()
    private val _metroBpm = MutableStateFlow(prefs.getInt("metro_bpm", 80))
    val metroBpm: StateFlow<Int> = _metroBpm.asStateFlow()
    private val _metroBeats = MutableStateFlow(prefs.getInt("metro_vurus", 4))
    val metroBeats: StateFlow<Int> = _metroBeats.asStateFlow()
    private val _metroBeat = MutableStateFlow(-1)
    val metroBeat: StateFlow<Int> = _metroBeat.asStateFlow()

    init {
        metronome.bpm = _metroBpm.value
        metronome.beatsPerBar = _metroBeats.value
        metronome.onBeat = { b -> _metroBeat.value = b }
    }

    fun setBpm(b: Int) {
        val v = b.coerceIn(30, 260)
        _metroBpm.value = v; metronome.bpm = v
        prefs.edit().putInt("metro_bpm", v).apply()
    }

    fun setBeats(n: Int) {
        val v = n.coerceIn(1, 12)
        _metroBeats.value = v; metronome.beatsPerBar = v
        prefs.edit().putInt("metro_vurus", v).apply()
    }

    fun toggleMetronome() {
        if (metronome.isRunning) { metronome.stop(); _metroBeat.value = -1 } else metronome.start()
        _metroRunning.value = metronome.isRunning
    }

    private val tapTimes = ArrayDeque<Long>()
    fun tapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) tapTimes.clear()
        tapTimes.addLast(now)
        while (tapTimes.size > 6) tapTimes.removeFirst()
        if (tapTimes.size >= 2) {
            val avg = (tapTimes.last() - tapTimes.first()).toDouble() / (tapTimes.size - 1)
            setBpm((60000.0 / avg).toInt())
        }
    }

    override fun onCleared() {
        metronome.stop()
        super.onCleared()
    }
}
