package app.basil.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class PhotoRecognitionTest {
    @Test fun bundledRecognizerReadsAReceiptImage() {
        val bitmap = Bitmap.createBitmap(1200, 750, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 58f }
        listOf("ICA", "2026-10-02", "TOMATO TIN 20,00", "TOTALT 20,00").forEachIndexed { i, text -> canvas.drawText(text, 60f, 100f + i * 120f, paint) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).text
            assertTrue(text, text.contains("ICA")); assertTrue(text, text.contains("TOMATO")); assertTrue(text, text.contains("20")); assertTrue(text, text.contains("TOTALT"))
        } finally { recognizer.close(); bitmap.recycle() }
    }
}
