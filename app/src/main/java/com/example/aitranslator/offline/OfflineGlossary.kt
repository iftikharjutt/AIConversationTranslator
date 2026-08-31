package com.example.aitranslator.offline

object OfflineGlossary {

    // Conservative Malay -> Urdu contextual term mapping
    private val malayToUrduTerms = listOf(
        Regex("(?i)\\bHari Raya Aidilfitri\\b") to "عید الفطر",
        Regex("(?i)\\bHari Raya Aidiladha\\b") to "عید الاضحیٰ",
        Regex("(?i)\\bHari Raya\\b") to "عید",
        Regex("(?i)\\bsolat\\b") to "نماز",
        Regex("(?i)\\bsembahyang\\b") to "نماز",
        Regex("(?i)\\bpuasa\\b") to "روزہ",
        Regex("(?i)\\bsurau\\b") to "مسجد",
        Regex("(?i)\\bmasjid\\b") to "مسجد",
        Regex("(?i)\\bzakat\\b") to "زکوٰۃ",
        Regex("(?i)\\bsedekah\\b") to "صدقہ",
        Regex("(?i)\\bdoa\\b") to "دعا",
        Regex("(?i)\\bkenduri\\b") to "دعوت",
        Regex("(?i)\\bterima kasih\\b") to "شکریہ",
        Regex("(?i)\\bsama-sama\\b") to "خوش آمدید / کوئی بات نہیں",
        Regex("(?i)\\bselamat pagi\\b") to "صبح بخیر",
        Regex("(?i)\\bselamat petang\\b") to "شام بخیر",
        Regex("(?i)\\bselamat malam\\b") to "شب بخیر",
        Regex("(?i)\\bapa khabar\\b") to "آپ کیسے ہیں؟",
        Regex("(?i)\\bkhabar baik\\b") to "میں ٹھیک ہوں"
    )

    // Conservative Urdu -> Malay contextual term mapping
    private val urduToMalayTerms = listOf(
        Regex("عید الفطر") to "Hari Raya Aidilfitri",
        Regex("عید الاضحیٰ") to "Hari Raya Aidiladha",
        Regex("نماز") to "solat",
        Regex("روزہ") to "puasa",
        Regex("مسجد") to "masjid",
        Regex("صبح بخیر") to "selamat pagi",
        Regex("شام بخیر") to "selamat petang",
        Regex("شب بخیر") to "selamat malam",
        Regex("شکریہ") to "terima kasih",
        Regex("آپ کیسے ہیں[؟?]?") to "apa khabar?"
    )

    fun applyMalayToUrduGlossary(text: String): String {
        var result = text
        for ((pattern, replacement) in malayToUrduTerms) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    fun applyUrduToMalayGlossary(text: String): String {
        var result = text
        for ((pattern, replacement) in urduToMalayTerms) {
            result = pattern.replace(result, replacement)
        }
        return result
    }
}
