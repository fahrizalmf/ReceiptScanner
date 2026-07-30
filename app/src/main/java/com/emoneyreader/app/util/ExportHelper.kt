package com.emoneyreader.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.emoneyreader.app.data.TransactionHistory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Membuat file .xlsx (format Office Open XML) secara manual, murni pakai
 * java.util.zip bawaan Android — tanpa dependency tambahan (Apache POI dsb
 * bermasalah dijalankan di Android). Hasilnya file .xlsx asli yang bisa
 * dibuka langsung di Microsoft Excel / Google Sheets / WPS, LENGKAP dengan
 * thumbnail foto struk yang ter-embed langsung di dalam sel.
 *
 * Kolom: Gerbang Tol, Jam, Nominal, Foto Struk.
 */
object ExportHelper {

    private val jamFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID"))

    // Konversi satuan gambar: 1 pixel (96 DPI) = 9525 EMU (satuan resmi OOXML)
    private const val EMU_PER_PIXEL = 9525
    private const val THUMB_MAX_SIDE_PX = 160

    private data class EmbeddedImage(val rowIndex1Based: Int, val jpegBytes: ByteArray, val widthPx: Int, val heightPx: Int)

    fun exportToXlsx(
        context: Context,
        transactions: List<TransactionHistory>,
        periodLabel: String
    ): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val fileName = "history_tol_${periodLabel}_${System.currentTimeMillis()}.xlsx"
        val outFile = File(exportDir, fileName)

        // Siapkan thumbnail untuk setiap transaksi yang punya foto struk
        val images = mutableListOf<EmbeddedImage>()
        transactions.forEachIndexed { index, t ->
            val rowNum = index + 2 // baris 1 = header
            val path = t.photoPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                val thumb = decodeScaledThumbnail(path)
                if (thumb != null) {
                    val (bytes, w, h) = thumb
                    images.add(EmbeddedImage(rowNum, bytes, w, h))
                }
            }
        }

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", relsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(transactions, images))

            if (images.isNotEmpty()) {
                writeEntry(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRelsXml())
                writeEntry(zip, "xl/drawings/drawing1.xml", drawingXml(images))
                writeEntry(zip, "xl/drawings/_rels/drawing1.xml.rels", drawingRelsXml(images.size))
                images.forEachIndexed { i, img ->
                    zip.putNextEntry(ZipEntry("xl/media/image${i + 1}.jpeg"))
                    zip.write(img.jpegBytes)
                    zip.closeEntry()
                }
            }
        }

        return outFile
    }

    /** Decode foto struk lalu perkecil jadi thumbnail JPEG (supaya file xlsx tidak membengkak) */
    private fun decodeScaledThumbnail(path: String): Triple<ByteArray, Int, Int>? {
        return try {
            val original = BitmapFactory.decodeFile(path) ?: return null
            val scale = THUMB_MAX_SIDE_PX.toFloat() / maxOf(original.width, original.height)
            val targetW = (original.width * scale).toInt().coerceAtLeast(1)
            val targetH = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            Triple(baos.toByteArray(), targetW, targetH)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="jpeg" ContentType="image/jpeg"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>
</Types>"""
    }

    private fun relsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="History Tol" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0"/></cellStyleXfs>
<cellXfs count="2">
<xf numFmtId="0" fontId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" xfId="0" applyFont="1"/>
</cellXfs>
</styleSheet>"""

    private fun sheetRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>"""

    private fun drawingRelsXml(imageCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..imageCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$i.jpeg"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun drawingXml(images: List<EmbeddedImage>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        images.forEachIndexed { i, img ->
            val relId = "rId${i + 1}"
            val xdrRow = img.rowIndex1Based - 1 // xdr pakai index 0-based
            val cx = img.widthPx * EMU_PER_PIXEL
            val cy = img.heightPx * EMU_PER_PIXEL
            sb.append("""<xdr:oneCellAnchor>""")
            sb.append("""<xdr:from><xdr:col>3</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$xdrRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""")
            sb.append("""<xdr:ext cx="$cx" cy="$cy"/>""")
            sb.append("""<xdr:pic>""")
            sb.append("""<xdr:nvPicPr><xdr:cNvPr id="${i + 2}" name="StrukFoto${i + 1}"/><xdr:cNvPicPr/></xdr:nvPicPr>""")
            sb.append("""<xdr:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            sb.append("""<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>""")
            sb.append("""</xdr:pic>""")
            sb.append("""<xdr:clientData/>""")
            sb.append("""</xdr:oneCellAnchor>""")
        }

        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    private fun escape(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun inlineStrCell(ref: String, value: String, styleIdx: Int = 0): String {
        return """<c r="$ref" t="inlineStr" s="$styleIdx"><is><t xml:space="preserve">${escape(value)}</t></is></c>"""
    }

    private fun numberCell(ref: String, value: Long): String {
        return """<c r="$ref" t="n"><v>$value</v></c>"""
    }

    private fun sheetXml(transactions: List<TransactionHistory>, images: List<EmbeddedImage>): String {
        val imagesByRow = images.associateBy { it.rowIndex1Based }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<cols><col min="1" max="1" width="28" customWidth="1"/><col min="2" max="2" width="20" customWidth="1"/><col min="3" max="3" width="15" customWidth="1"/><col min="4" max="4" width="24" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        // Header row (row 1) - bold style index 1
        sb.append("<row r=\"1\">")
        sb.append(inlineStrCell("A1", "Gerbang Tol", 1))
        sb.append(inlineStrCell("B1", "Jam", 1))
        sb.append(inlineStrCell("C1", "Nominal", 1))
        sb.append(inlineStrCell("D1", "Foto Struk", 1))
        sb.append("</row>")

        var rowNum = 2
        var total = 0L
        for (t in transactions) {
            val img = imagesByRow[rowNum]
            // Tinggi baris disesuaikan supaya thumbnail terlihat penuh (dalam poin, 1px approx = 0.75pt)
            val rowHeightAttr = if (img != null) {
                val heightPt = (img.heightPx * 0.75f).coerceAtLeast(60f)
                """ ht="$heightPt" customHeight="1""""
            } else ""

            sb.append("<row r=\"$rowNum\"$rowHeightAttr>")
            sb.append(inlineStrCell("A$rowNum", t.tollGateName))
            sb.append(inlineStrCell("B$rowNum", jamFormat.format(Date(t.timestamp))))
            sb.append(numberCell("C$rowNum", t.nominal))
            if (img == null) sb.append(inlineStrCell("D$rowNum", "-"))
            // Kalau ada gambar, sel D dibiarkan kosong; gambar ditumpuk di atasnya lewat drawing anchor
            sb.append("</row>")
            total += t.nominal
            rowNum++
        }

        // Total row
        sb.append("<row r=\"$rowNum\">")
        sb.append(inlineStrCell("A$rowNum", "TOTAL", 1))
        sb.append(inlineStrCell("B$rowNum", "", 1))
        sb.append("""<c r="C$rowNum" t="n" s="1"><v>$total</v></c>""")
        sb.append("</row>")

        sb.append("</sheetData>")
        if (images.isNotEmpty()) {
            sb.append("""<drawing r:id="rId1"/>""")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }
}
