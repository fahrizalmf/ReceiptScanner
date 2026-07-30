package com.emoneyreader.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_history")
data class TransactionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tollGateName: String,
    val nominal: Long,          // nominal transaksi (Rupiah)
    val cardUid: String?,       // UID kartu hasil scan NFC (jika ada)
    val cardType: String?,      // jenis teknologi kartu (NfcA / MifareClassic / dst)
    val timestamp: Long,        // epoch millis
    val photoPath: String? = null,  // path foto struk hasil scan kamera (jika ada)
    val note: String? = null
)
