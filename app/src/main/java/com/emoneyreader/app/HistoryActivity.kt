package com.emoneyreader.app

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.emoneyreader.app.adapter.HistoryAdapter
import com.emoneyreader.app.data.AppDatabase
import com.emoneyreader.app.databinding.ActivityHistoryBinding
import com.emoneyreader.app.util.ExportHelper
import com.emoneyreader.app.util.PdfReportHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: HistoryAdapter

    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
    private var startCal: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }
    private var endCal: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "History Transaksi Tol"

        db = AppDatabase.getInstance(this)

        adapter = HistoryAdapter(emptyList()) { transaction ->
            lifecycleScope.launch {
                db.transactionDao().delete(transaction)
                loadFiltered()
            }
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        updateDateButtons()

        binding.btnStartDate.setOnClickListener { pickDate(startCal) { loadFiltered() } }
        binding.btnEndDate.setOnClickListener { pickDate(endCal) { loadFiltered() } }

        binding.btnExport.setOnClickListener { exportCurrentPeriod() }
        binding.btnExportPdf.setOnClickListener { exportCurrentPeriodPdf() }

        loadFiltered()
    }

    private fun pickDate(calendar: Calendar, onPicked: () -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                updateDateButtons()
                onPicked()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateButtons() {
        binding.btnStartDate.text = "Mulai: ${displayFormat.format(startCal.time)}"
        binding.btnEndDate.text = "Akhir: ${displayFormat.format(endCal.time)}"
    }

    private fun loadFiltered() {
        lifecycleScope.launch {
            val list = db.transactionDao().getByPeriod(startCal.timeInMillis, endCal.timeInMillis)
            adapter.updateData(list)

            val total = list.sumOf { it.nominal }
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            binding.tvSummary.text = "${list.size} transaksi | Total: ${currencyFormat.format(total)}"
        }
    }

    private fun exportCurrentPeriod() {
        lifecycleScope.launch {
            val list = db.transactionDao().getByPeriod(startCal.timeInMillis, endCal.timeInMillis)
            if (list.isEmpty()) {
                Toast.makeText(this@HistoryActivity, "Tidak ada data pada periode ini", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val periodLabel = currentPeriodLabel()
            val file = ExportHelper.exportToXlsx(this@HistoryActivity, list, periodLabel)
            shareFile(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Bagikan file Excel")
        }
    }

    private fun exportCurrentPeriodPdf() {
        lifecycleScope.launch {
            val list = db.transactionDao().getByPeriod(startCal.timeInMillis, endCal.timeInMillis)
            if (list.isEmpty()) {
                Toast.makeText(this@HistoryActivity, "Tidak ada data pada periode ini", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val periodLabel = currentPeriodLabel()
            val file = PdfReportHelper.exportToPdf(this@HistoryActivity, list, periodLabel)
            shareFile(file, "application/pdf", "Bagikan laporan PDF")
        }
    }

    private fun currentPeriodLabel(): String {
        return "${displayFormat.format(startCal.time)}_${displayFormat.format(endCal.time)}".replace("/", "-")
    }

    private fun shareFile(file: java.io.File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            this@HistoryActivity,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
