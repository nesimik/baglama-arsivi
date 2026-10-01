package com.nesimi.baglamaarsivi.util

import com.nesimi.baglamaarsivi.data.VideoItem
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Tr {
    val LOCALE: Locale = Locale("tr", "TR")
    private val collator: Collator = Collator.getInstance(LOCALE).apply { strength = Collator.PRIMARY }

    /** Türkçe büyük/küçük harf ve şapka duyarsız normalleştirme (ı=i, ş=s ... aramada kolaylık) */
    fun norm(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val lower = text.lowercase(LOCALE)
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            sb.append(
                when (c) {
                    'ı' -> 'i'; 'ş' -> 's'; 'ğ' -> 'g'; 'ü' -> 'u'; 'ö' -> 'o'; 'ç' -> 'c'
                    'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun contains(source: String?, query: String?): Boolean {
        val q = norm(query).trim()
        if (q.isEmpty()) return true
        return norm(source).contains(q)
    }

    fun compare(a: String, b: String): Int = collator.compare(a, b)

    fun today(): String = SimpleDateFormat("dd.MM.yyyy", LOCALE).format(Date())

    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", LOCALE).format(Date(ms))

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", LOCALE).format(Date(ms))

    /** "25.09.2026" -> sıralanabilir sayı (20260925); geçersizse 0 */
    fun lessonDateKey(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val p = s.trim().split('.', '/', '-')
        if (p.size != 3) return 0
        val d = p[0].toIntOrNull() ?: return 0
        val m = p[1].toIntOrNull() ?: return 0
        val y = p[2].toIntOrNull() ?: return 0
        return y * 10000L + m * 100L + d
    }

    fun dayKey(ms: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun startOfWeek(): Long {
        val c = Calendar.getInstance(LOCALE)
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        if (c.timeInMillis > System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, -7)
        return c.timeInMillis
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Çalışma süresi: "1 sa 25 dk", "12 dk", "45 sn" */
    fun practice(sec: Long): String {
        if (sec <= 0) return "0 dk"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "$h sa $m dk"
            h > 0 -> "$h sa"
            m > 0 -> "$m dk"
            else -> "$sec sn"
        }
    }

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(LOCALE, "%.2f GB", gb)
            mb >= 1.0 -> String.format(LOCALE, "%.1f MB", mb)
            else -> String.format(LOCALE, "%.0f KB", kb)
        }
    }

    fun splitTags(tags: String): List<String> =
        tags.split(',', ';').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
}

/**
 * Videoların hiyerarşik sıra numarasına göre sıralanması:
 * 1 < 1.2 < 2 < 2/1 < 2,2 < 2.6 < 3 ; numaralılar numarasızlardan önce gelir.
 */
object VideoOrder : Comparator<VideoItem> {
    override fun compare(v1: VideoItem, v2: VideoItem): Int {
        val o1 = v1.displayOrderTag.removePrefix("#").trim()
        val o2 = v2.displayOrderTag.removePrefix("#").trim()
        val has1 = o1.isNotBlank()
        val has2 = o2.isNotBlank()
        if (has1 && !has2) return -1
        if (!has1 && has2) return 1
        if (!has1) {
            if (v1.uploadOrder > 0 && v2.uploadOrder > 0 && v1.uploadOrder != v2.uploadOrder) {
                return v1.uploadOrder.compareTo(v2.uploadOrder)
            }
            val c = v1.createdAt.compareTo(v2.createdAt)
            return if (c != 0) c else v1.id.compareTo(v2.id)
        }
        val cmp = compareTags(o1, o2)
        if (cmp != 0) return cmp
        return v1.createdAt.compareTo(v2.createdAt)
    }

    fun compareTags(s1: String, s2: String): Int {
        val a = segments(s1)
        val b = segments(s2)
        for (i in 0 until maxOf(a.size, b.size)) {
            val p1 = a.getOrNull(i) ?: return -1
            val p2 = b.getOrNull(i) ?: return 1
            val n1 = p1.toLongOrNull()
            val n2 = p2.toLongOrNull()
            val c = when {
                n1 != null && n2 != null -> n1.compareTo(n2)
                n1 != null -> -1
                n2 != null -> 1
                else -> p1.compareTo(p2, ignoreCase = true)
            }
            if (c != 0) return c
        }
        return 0
    }

    private val SEG = Regex("(\\d+|[^\\d\\s.,/\\-_#]+)")
    private fun segments(s: String): List<String> = SEG.findAll(s).map { it.value }.toList()

    fun section(tag: String): Int? = tag.trim().removePrefix("#").trim().takeWhile { it.isDigit() }.toIntOrNull()
}
