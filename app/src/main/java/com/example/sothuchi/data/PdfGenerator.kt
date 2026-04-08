package com.example.sothuchi.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.sothuchi.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

/**
 * Utility class for generating a PDF report from transaction data.
 * Draws a table with 6 columns as per user requirements.
 */
object PdfGenerator {

    /**
     * Helper for Java calls. Launches the PDF generation in the provided scope.
     */
    @JvmStatic
    fun savePdfAsync(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        uri: Uri,
        transactions: List<Transaction>,
        onResult: (Boolean) -> Unit
    ) {
        lifecycleOwner.lifecycleScope.launch {
            val success = generateResultPdf(context, uri, transactions)
            onResult(success)
        }
    }

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f
    private const val TABLE_START_Y = 120f
    private const val ROW_HEIGHT = 25f
    private const val HEADER_HEIGHT = 30f

    // Columns: Ngày, Danh mục, Số tiền, Ghi chú, Thu chi, Chi phí cố định
    private val columnWidths = floatArrayOf(75f, 90f, 85f, 135f, 50f, 80f)
    private val headers = arrayOf("Ngày", "Danh mục", "Số tiền", "Ghi chú", "Loại", "Cố định")

    /**
     * Generates a PDF and saves it to the provided Uri.
     */
    suspend fun generateResultPdf(
        context: Context,
        uri: Uri,
        transactions: List<Transaction>
    ): Boolean = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val cellPaint = Paint().apply {
            textSize = 9f
            typeface = Typeface.DEFAULT
            color = Color.BLACK
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#1DB954") // primary_green
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var currentY = TABLE_START_Y
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Draw Title
        drawHeader(canvas, titlePaint, "BÁO CÁO KẾT QUẢ TÌM KIẾM")

        // Draw Table Headers
        drawTableHeader(canvas, headerBgPaint, headerPaint, linePaint)
        currentY += HEADER_HEIGHT

        val fmt = NumberFormat.getInstance(Locale("vi", "VN"))

        for (transaction in transactions) {
            // Check for page break
            if (currentY + ROW_HEIGHT > PAGE_HEIGHT - MARGIN) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN + 20f
                drawTableHeader(canvas, headerBgPaint, headerPaint, linePaint, currentY)
                currentY += HEADER_HEIGHT
            }

            // Draw Row data
            drawRow(canvas, cellPaint, linePaint, transaction, currentY, fmt)
            currentY += ROW_HEIGHT
        }

        pdfDocument.finishPage(page)

        return@withContext try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, text: String) {
        val x = (PAGE_WIDTH - paint.measureText(text)) / 2
        canvas.drawText(text, x, 60f, paint)
        
        val subPaint = Paint().apply {
            textSize = 10f
            color = Color.GRAY
        }
        val subText = "Xuất từ ứng dụng Sổ Thu Chi - ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date())}"
        canvas.drawText(subText, (PAGE_WIDTH - subPaint.measureText(subText)) / 2, 85f, subPaint)
    }

    private fun drawTableHeader(canvas: Canvas, bgPaint: Paint, textPaint: Paint, linePaint: Paint, y: Float = TABLE_START_Y) {
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_HEIGHT, bgPaint)
        
        var currentX = MARGIN
        for (i in headers.indices) {
            val headerText = headers[i]
            canvas.drawText(headerText, currentX + 5, y + 20, textPaint)
            // Draw Vertical Line
            canvas.drawLine(currentX, y, currentX, y + HEADER_HEIGHT, linePaint)
            currentX += columnWidths[i]
        }
        canvas.drawLine(PAGE_WIDTH - MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_HEIGHT, linePaint)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        canvas.drawLine(MARGIN, y + HEADER_HEIGHT, PAGE_WIDTH - MARGIN, y + HEADER_HEIGHT, linePaint)
    }

    private fun drawRow(canvas: Canvas, textPaint: Paint, linePaint: Paint, t: Transaction, y: Float, fmt: NumberFormat) {
        val values = arrayOf(
            t.date ?: "",
            t.category ?: "",
            fmt.format(t.amount) + "đ",
            truncateText(t.note ?: "", textPaint, columnWidths[3] - 10),
            if (t.type == 1) "Thu" else "Chi",
            "Không" // Placeholder for Fixed expense
        )

        var currentX = MARGIN
        for (i in values.indices) {
            canvas.drawText(values[i], currentX + 5, y + 17, textPaint)
            canvas.drawLine(currentX, y, currentX, y + ROW_HEIGHT, linePaint)
            currentX += columnWidths[i]
        }
        canvas.drawLine(PAGE_WIDTH - MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, linePaint)
        canvas.drawLine(MARGIN, y + ROW_HEIGHT, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, linePaint)
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "..." else "..."
    }
}
