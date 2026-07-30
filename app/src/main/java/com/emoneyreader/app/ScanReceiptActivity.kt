package com.emoneyreader.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.emoneyreader.app.data.AppDatabase
import com.emoneyreader.app.data.TransactionHistory
import com.emoneyreader.app.databinding.ActivityScanReceiptBinding
import com.emoneyreader.app.databinding.DialogAddTransactionBinding
import com.emoneyreader.app.util.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * Alur kerja: user tap "Ambil Foto Struk" berulang kali untuk tiap struk fisik.
 * Tiap foto langsung di-OCR di HP (offline, ML Kit), nominal & nama gerbang tol
 * dicoba dideteksi otomatis, user tinggal konfirmasi/koreksi lalu lanjut ke
 * struk berikutnya. Total otomatis terakumulasi di layar & tersimpan ke history.
 */
class ScanReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanReceiptBinding
    private lateinit var db: AppDatabase
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private var pendingPhotoUri: Uri? = null
    private var pendingPhotoFile: File? = null

    private var scannedCount = 0
    private var totalNominal = 0L

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoUri != null) {
            processPhoto(pendingPhotoUri!!, pendingPhotoFile!!)
        } else {
            binding.tvStatus.text = "Pengambilan foto dibatalkan"
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, "Izin kamera dibutuhkan untuk scan struk", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Scan Struk Tol"

        db = AppDatabase.getInstance(this)

        binding.btnScan.setOnClickListener { checkPermissionAndScan() }
        binding.btnSelesai.setOnClickListener { finish() }

        updateSummary()
    }

    private fun checkPermissionAndScan() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val receiptsDir = File(getExternalFilesDir(null), "receipts")
        if (!receiptsDir.exists()) receiptsDir.mkdirs()

        val photoFile = File(receiptsDir, "receipt_${System.currentTimeMillis()}.jpg")
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)

        pendingPhotoUri = photoUri
        pendingPhotoFile = photoFile

        binding.tvStatus.text = "Membuka kamera..."
        takePictureLauncher.launch(photoUri)
    }

    private fun processPhoto(uri: Uri, file: File) {
        binding.tvStatus.text = "Membaca teks struk..."

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            binding.tvStatus.text = "Gagal memuat foto, coba lagi"
            return
        }
        binding.ivPreview.setImageBitmap(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                lifecycleScope.launch {
                    val gates = db.tollGateDao().getAll()
                    gates.collect { tollGates ->
                        val names = tollGates.map { it.name }
                        val parsed = ReceiptParser.parse(visionText.text, names)
                        showConfirmDialog(parsed.nominal, parsed.matchedTollGate, parsed.confidence, names, file.absolutePath)
                        return@collect
                    }
                }
            }
            .addOnFailureListener {
                binding.tvStatus.text = "OCR gagal: ${it.message}. Kamu tetap bisa input manual."
                lifecycleScope.launch {
                    db.tollGateDao().getAll().collect { tollGates ->
                        showConfirmDialog(null, null, "rendah", tollGates.map { it.name }, file.absolutePath)
                        return@collect
                    }
                }
            }
    }

    private fun showConfirmDialog(
        detectedNominal: Long?,
        detectedGateName: String?,
        confidence: String,
        tollGateNames: List<String>,
        photoPath: String
    ) {
        if (tollGateNames.isEmpty()) {
            Toast.makeText(this, "Tambahkan nama gerbang tol dahulu di menu Kelola Gerbang Tol", Toast.LENGTH_LONG).show()
            binding.tvStatus.text = "Belum ada gerbang tol tersimpan"
            return
        }

        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tollGateNames)
        dialogBinding.spinnerTollGate.adapter = adapter

        // Pre-fill hasil deteksi otomatis
        detectedGateName?.let {
            val idx = tollGateNames.indexOf(it)
            if (idx >= 0) dialogBinding.spinnerTollGate.setSelection(idx)
        }
        detectedNominal?.let {
            dialogBinding.etNominal.setText(it.toString())
        }

        val statusMsg = when {
            detectedNominal != null && confidence == "tinggi" ->
                "Nominal terdeteksi (keyakinan tinggi): ${currencyFormat.format(detectedNominal)}. Periksa sekilas lalu simpan."
            detectedNominal != null ->
                "Nominal terdeteksi (belum pasti akurat): ${currencyFormat.format(detectedNominal)}. Mohon dicek ulang."
            else ->
                "Nominal tidak terdeteksi otomatis dari struk ini, silakan isi manual."
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Struk")
            .setMessage(statusMsg)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan & Lanjut") { _, _ ->
                val selectedGate = tollGateNames[dialogBinding.spinnerTollGate.selectedItemPosition]
                val nominal = dialogBinding.etNominal.text.toString().toLongOrNull()

                if (nominal == null || nominal <= 0) {
                    Toast.makeText(this, "Nominal tidak valid, struk tidak disimpan", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                saveTransaction(selectedGate, nominal, photoPath)
            }
            .setNegativeButton("Buang Struk Ini", null)
            .setCancelable(false)
            .show()
    }

    private fun saveTransaction(tollGateName: String, nominal: Long, photoPath: String) {
        lifecycleScope.launch {
            db.transactionDao().insert(
                TransactionHistory(
                    tollGateName = tollGateName,
                    nominal = nominal,
                    cardUid = null,
                    cardType = null,
                    timestamp = System.currentTimeMillis(),
                    photoPath = photoPath
                )
            )
            scannedCount++
            totalNominal += nominal
            updateSummary()
            binding.tvStatus.text = "Tersimpan. Siap scan struk berikutnya."
        }
    }

    private fun updateSummary() {
        binding.tvSummary.text = "$scannedCount struk discan | Total: ${currencyFormat.format(totalNominal)}"
    }
}
