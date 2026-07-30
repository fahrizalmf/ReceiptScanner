package com.emoneyreader.app.util

data class ParsedReceipt(
    val nominal: Long?,
    val matchedTollGate: String?,
    val rawText: String,
    val confidence: String // "tinggi" / "rendah" - dipakai untuk pesan ke user
)

/**
 * Parser hasil OCR struk tol. Berbagai operator/gerbang tol pakai istilah
 * berbeda-beda di struknya (TARIF, TOTAL, BAYAR, NOMINAL, BIAYA, JUMLAH BAYAR, dst)
 * dan kualitas cetak thermal printer sering bervariasi — jadi parser ini mencoba
 * beberapa strategi berurutan dari yang paling bisa dipercaya ke paling lemah,
 * tapi HASIL AKHIR TETAP HARUS DIKONFIRMASI USER sebelum disimpan karena OCR
 * struk fisik tidak pernah 100% akurat.
 */
object ReceiptParser {

    // Strategi 1 (paling reliable): angka yang muncul tepat setelah kata kunci nominal
    private val keywordAmountRegex = Regex(
        """(?:TARIF|TOTAL|BAYAR|NOMINAL|BIAYA|JUMLAH|AMOUNT|SALDO\s*TERPAKAI)\s*[:.\-]?\s*(?:RP|Rp)?\.?\s*([0-9][0-9.,\s]{2,12}[0-9]|[0-9]{3,7})""",
        RegexOption.IGNORE_CASE
    )

    // Strategi 2: angka yang didahului simbol Rp/RP dimana pun posisinya
    private val rpPrefixRegex = Regex(
        """(?:RP|Rp|rp)\.?\s?([0-9][0-9.,\s]{2,12}[0-9]|[0-9]{3,7})"""
    )

    fun parse(rawText: String, knownTollGates: List<String>): ParsedReceipt {
        val fromKeyword = extractAmounts(keywordAmountRegex, rawText)
        val fromRpPrefix = extractAmounts(rpPrefixRegex, rawText)

        val nominal: Long?
        val confidence: String
        when {
            fromKeyword.isNotEmpty() -> {
                nominal = fromKeyword.max()
                confidence = "tinggi"
            }
            fromRpPrefix.isNotEmpty() -> {
                nominal = fromRpPrefix.max()
                confidence = "sedang"
            }
            else -> {
                nominal = null
                confidence = "rendah"
            }
        }

        val matchedGate = knownTollGates.firstOrNull { gateName ->
            rawText.contains(gateName, ignoreCase = true)
        }

        return ParsedReceipt(nominal = nominal, matchedTollGate = matchedGate, rawText = rawText, confidence = confidence)
    }

    private fun extractAmounts(regex: Regex, text: String): List<Long> {
        return regex.findAll(text)
            .mapNotNull { match ->
                val cleaned = match.groupValues[1]
                    .replace(".", "")
                    .replace(",", "")
                    .replace(" ", "")
                cleaned.toLongOrNull()
            }
            // Nominal tol wajar: antara Rp 1.000 - Rp 500.000
            .filter { it in 1_000..500_000 }
            .toList()
    }
}
