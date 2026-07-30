package com.emoneyreader.app

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.emoneyreader.app.data.AppDatabase
import com.emoneyreader.app.data.TollGate
import com.emoneyreader.app.data.TransactionHistory
import com.emoneyreader.app.databinding.ActivityMainBinding
import com.emoneyreader.app.databinding.DialogAddTransactionBinding
import com.emoneyreader.app.util.NfcCardInfo
import com.emoneyreader.app.util.NfcHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var db: AppDatabase

    private var lastCardInfo: NfcCardInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            binding.tvStatus.text = "Perangkat ini tidak mendukung NFC"
        } else if (nfcAdapter?.isEnabled == false) {
            binding.tvStatus.text = "NFC belum aktif. Aktifkan NFC di pengaturan HP."
        }

        binding.btnScanReceipt.setOnClickListener {
            startActivity(Intent(this, ScanReceiptActivity::class.java))
        }

        binding.btnInputManual.setOnClickListener {
            lastCardInfo = null
            showAddTransactionDialog(null)
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnTollGate.setOnClickListener {
            startActivity(Intent(this, TollGateActivity::class.java))
        }

        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun enableNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null || !NfcHelper.isNfcIntent(intent)) return

        val cardInfo = NfcHelper.readTagInfo(intent)
        if (cardInfo == null) {
            binding.tvStatus.text = "Gagal membaca kartu, coba tempelkan ulang"
            return
        }

        lastCardInfo = cardInfo
        binding.tvStatus.text = "Kartu terdeteksi!"
        binding.tvCardInfo.text = "UID: ${cardInfo.uid}\nTipe: ${cardInfo.techList.joinToString(", ")}"

        // Saldo asli tidak bisa dibaca karena terenkripsi (lihat NfcHelper).
        // Lanjut ke input nominal transaksi manual, UID otomatis tersimpan untuk histori.
        showAddTransactionDialog(cardInfo)
    }

    private fun showAddTransactionDialog(cardInfo: NfcCardInfo?) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)

        lifecycleScope.launch {
            db.tollGateDao().getAll().collect { tollGates ->
                if (tollGates.isEmpty()) {
                    binding.tvStatus.text = "Tambahkan minimal 1 nama gerbang tol dahulu"
                    startActivity(Intent(this@MainActivity, TollGateActivity::class.java))
                    return@collect
                }

                val names = tollGates.map { it.name }
                val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
                dialogBinding.spinnerTollGate.adapter = spinnerAdapter

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (cardInfo != null) "Catat Transaksi (Kartu Terdeteksi)" else "Catat Transaksi Manual")
                    .setView(dialogBinding.root)
                    .setPositiveButton("Simpan") { _, _ ->
                        val selectedGate = tollGates[dialogBinding.spinnerTollGate.selectedItemPosition]
                        val nominalText = dialogBinding.etNominal.text.toString()
                        val nominal = nominalText.toLongOrNull()

                        if (nominal == null || nominal <= 0) {
                            binding.tvStatus.text = "Nominal tidak valid"
                            return@setPositiveButton
                        }

                        saveTransaction(selectedGate.name, nominal, cardInfo)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
                return@collect
            }
        }
    }

    private fun saveTransaction(tollGateName: String, nominal: Long, cardInfo: NfcCardInfo?) {
        lifecycleScope.launch {
            db.transactionDao().insert(
                TransactionHistory(
                    tollGateName = tollGateName,
                    nominal = nominal,
                    cardUid = cardInfo?.uid,
                    cardType = cardInfo?.techList?.joinToString(","),
                    timestamp = System.currentTimeMillis()
                )
            )
            binding.tvStatus.text = "Transaksi tersimpan di history"
        }
    }
}
