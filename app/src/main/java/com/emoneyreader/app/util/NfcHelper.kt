package com.emoneyreader.app.util

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Helper untuk membaca kartu NFC yang ditempelkan.
 *
 * PENTING: Kartu e-money bank (Flazz/BCA, Brizzi/BRI, e-toll/Mandiri, TapCash/BNI)
 * menggunakan chip Mifare DESFire/Classic dengan kunci enkripsi milik masing-masing
 * bank yang tidak dipublikasikan. Karena itu SALDO ASLI di dalam chip tidak bisa
 * dibaca oleh aplikasi pihak ketiga manapun (termasuk aplikasi ini) tanpa kunci resmi.
 *
 * Yang BISA dan LEGAL dilakukan: membaca UID (nomor identitas unik) kartu dan jenis
 * teknologinya untuk keperluan pencatatan/pelacakan transaksi. Nominal transaksi
 * tetap diinput manual oleh pengguna (sesuai struk/layar EDC saat transaksi tol).
 */
data class NfcCardInfo(
    val uid: String,
    val techList: List<String>
)

object NfcHelper {

    fun readTagInfo(intent: Intent): NfcCardInfo? {
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        val uidBytes = tag.id ?: return null
        val uidHex = uidBytes.joinToString(separator = "") { String.format("%02X", it) }
        return NfcCardInfo(
            uid = uidHex,
            techList = tag.techList.map { it.substringAfterLast('.') }
        )
    }

    fun isNfcIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        return action == NfcAdapter.ACTION_TECH_DISCOVERED ||
                action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                action == NfcAdapter.ACTION_NDEF_DISCOVERED
    }
}
