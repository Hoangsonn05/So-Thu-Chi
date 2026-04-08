package com.example.sothuchi

import java.text.Normalizer

/**
 * Vietnamese text utility for accent-insensitive search.
 *
 * Converts accented Vietnamese characters to their ASCII equivalents,
 * so that e.g. "Ăn uống" → "an uong", allowing the user to search
 * without typing diacritics.
 */
object VietUtils {

    /**
     * Remove Vietnamese diacritics/accents and convert to lowercase.
     * e.g. "Ăn uống" → "an uong"
     *      "Tiền điện" → "tien dien"
     *      "Mỹ phẩm"  → "my pham"
     */
    @JvmStatic
    fun removeAccents(input: String?): String {
        if (input.isNullOrEmpty()) return ""

        var text = input

        // Pre-process special Vietnamese characters that Normalizer doesn't handle
        text = text.replace("đ", "d").replace("Đ", "D")

        // Use Unicode Normalizer to decompose accented characters
        // NFD decomposes characters: ắ → a + breve + acute
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)

        // Remove all combining diacritical marks (Unicode category Mn)
        // This regex matches characters in the "Mark, non-spacing" Unicode category
        val stripped = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        return stripped.lowercase()
    }

    /**
     * Check if the target string contains the query after accent normalization.
     * @param target The text to search in (e.g., category name, note, date)
     * @param query The search query (may or may not have accents)
     * @return true if target contains query (accent-insensitive, case-insensitive)
     */
    @JvmStatic
    fun containsIgnoreAccent(target: String?, query: String?): Boolean {
        if (query.isNullOrEmpty()) return true
        if (target.isNullOrEmpty()) return false
        return removeAccents(target).contains(removeAccents(query))
    }
}
