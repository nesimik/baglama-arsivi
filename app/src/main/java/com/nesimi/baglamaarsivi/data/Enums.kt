package com.nesimi.baglamaarsivi.data

enum class StudyStatus(val displayName: String, val badgeIcon: String) {
    CALISIYORUM("Çalışıyorum", "🔵"),
    OGRENILECEK("Öğrenilecek", "🟡"),
    OGRENILDI("Öğrendim", "🟢");

    companion object {
        fun fromString(value: String?): StudyStatus =
            entries.firstOrNull { it.name.equals(value, true) || it.displayName.equals(value, true) } ?: CALISIYORUM
    }
}

enum class DocumentCategory(val displayName: String) {
    NOTA("Nota"),
    SOZ("Söz"),
    AKOR("Akor"),
    RITIM("Ritim"),
    MAKAM("Makam"),
    TEKNIK("Teknik"),
    EGZERSIZ("Egzersiz"),
    DERS_NOTU("Ders Notu"),
    SES("Ses Kaydı"),
    DIGER("Diğer");

    companion object {
        fun fromString(value: String?): DocumentCategory =
            entries.firstOrNull { it.name.equals(value, true) || it.displayName.equals(value, true) } ?: DIGER
    }
}

enum class SortOption(val title: String) {
    MANUAL("Benim Sıram"),
    NAME_ASC("A → Z"),
    NAME_DESC("Z → A"),
    DATE_DESC("En Yeni"),
    DATE_ASC("En Eski"),
    LESSON_DATE_DESC("Son Ders Tarihi"),
    PRACTICE_DESC("En Çok Çalışılan"),
    FAVORITES_FIRST("Favoriler Önce"),
    STUDYING_FIRST("Çalışıyorum Önce")
}

enum class FilterOption(val title: String) {
    ALL("Tümü"),
    FAVORITES("Favoriler"),
    STUDYING("Çalışıyorum"),
    TO_LEARN("Öğrenilecek"),
    LEARNED("Öğrendim"),
    HAS_VIDEOS("Videolu"),
    HAS_DOCUMENTS("Belgeli")
}

enum class AppThemeMode(val title: String) {
    SYSTEM("Sistem"),
    LIGHT("Aydınlık"),
    DARK("Karanlık")
}

val LEVELS = listOf("Başlangıç", "Orta", "İleri")
