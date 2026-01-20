package com.example.mypos

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.io.OutputStream
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color

class BluetoothPrinterManager(private val mac: String) {

    private lateinit var outputStream: OutputStream
    private val PRINTER_WIDTH = 384   // 58mm

    fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = adapter.getRemoteDevice(mac)
        val socket = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        )
        socket.connect()
        outputStream = socket.outputStream

        cmd(byteArrayOf(0x1B, 0x40)) // reset
    }

    private fun cmd(bytes: ByteArray) {
        outputStream.write(bytes)
    }

    private fun write(text: String) {
        outputStream.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    // ---------- IMAGE PRINT (HINDI SAFE) ----------

    private fun printTextBitmap(
        text: String,
        size: Float,
        bold: Boolean,
        center: Boolean
    ) {
        val paint = Paint().apply {
            textSize = size
            isFakeBoldText = bold
            color = Color.BLACK
            isAntiAlias = true
        }

        val lineHeight = (size + 6).toInt()
        val lines = wrapText(text, paint, PRINTER_WIDTH)
        val height = lines.size * lineHeight + 10

        val bmp = Bitmap.createBitmap(PRINTER_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        var y = lineHeight.toFloat()
        lines.forEach {
            val x = if (center) (PRINTER_WIDTH - paint.measureText(it)) / 2 else 0f
            canvas.drawText(it, x, y, paint)
            y += lineHeight
        }

        printBitmap(bmp)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        for (w in words) {
            val test = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(test) <= maxWidth) {
                line = test
            } else {
                lines.add(line)
                line = w
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
    }

    private fun printBitmap(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val bytes = ByteArray((w * h) / 8)
        var idx = 0

        for (y in 0 until h) {
            for (x in 0 until w step 8) {
                var b = 0
                for (bit in 0 until 8) {
                    if (bitmap.getPixel(x + bit, y) != Color.WHITE) {
                        b = b or (1 shl (7 - bit))
                    }
                }
                bytes[idx++] = b.toByte()
            }
        }

        cmd(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (w / 8).toByte(), 0x00, h.toByte(), 0x00))
        cmd(bytes)
    }

    // ---------- RECEIPT ----------

    private fun printHeaderRow() {
        val paint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.BLACK
            isAntiAlias = true
        }

        val height = 45
        val bmp = Bitmap.createBitmap(PRINTER_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val y = 30f
        canvas.drawText("Item", 0f, y, paint)
        canvas.drawText("Qty", 220f, y, paint)
        canvas.drawText("Rate", 270f, y, paint)
        canvas.drawText("Total", 330f, y, paint)

        printBitmap(bmp)
    }

    private fun printItemRow(
        name: String,
        qty: Int,
        rate: Int,
        total: Int
    ) {
        val paint = Paint().apply {
            textSize = 22f
            color = Color.BLACK
            isAntiAlias = true
        }

        val nameLines = wrapText(name, paint, 200)
        val rowHeight = nameLines.size * 28 + 12

        val bmp = Bitmap.createBitmap(PRINTER_WIDTH, rowHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val nameX = 0f
        val qtyX  = 210f
        val rateX = 260f
        val totX  = 320f
        val y = 28f

        var currentY = y
        nameLines.forEach {
            canvas.drawText(it, nameX, currentY, paint)
            currentY += 28
        }

        // Numbers stay aligned to FIRST line only
        canvas.drawText(qty.toString(), qtyX, y, paint)
        canvas.drawText(rate.toString(), rateX, y, paint)
        canvas.drawText(total.toString(), totX, y, paint)

        printBitmap(bmp)
    }
    private fun printTotalRow(total: Int) {
        val paint = Paint().apply {
            textSize = 32f
            isFakeBoldText = true
            color = Color.BLACK
            isAntiAlias = true
        }

        val bmp = Bitmap.createBitmap(PRINTER_WIDTH, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val text = "TOTAL  $total"
        val x = (PRINTER_WIDTH - paint.measureText(text)) / 2
        canvas.drawText(text, x, 40f, paint)

        printBitmap(bmp)
    }

    private fun printDivider() {
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        val bmp = Bitmap.createBitmap(PRINTER_WIDTH, 12, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.drawLine(0f, 6f, PRINTER_WIDTH.toFloat(), 6f, paint)

        printBitmap(bmp)
    }

    private fun feed(lines: Int = 3) {
        repeat(lines) {
            cmd(byteArrayOf(0x0A))
        }
    }

    fun printReceipt(
        name: String,
        billNo: String,
        date: String,
        items: List<Triple<String, Int, Int>>,
        total: Int
    ) {
        connect()

        printTextBitmap(name, 30f, true, true)

        printTextBitmap("Bill No: $billNo", 20f, false, false)
        printTextBitmap("Date: $date", 20f, false, false)
        printDivider()
        printHeaderRow()
        printDivider()


        // Items
        items.forEach {
            val qty = it.second
            val rate = it.third
            val total = qty * rate

            printItemRow(it.first, qty, rate, total)
        }



        printDivider()


        printTextBitmap("Total Items: ${items.size}", 20f, false, false)

        printTotalRow(total)


        printTextBitmap("Thank You! Visit Again!", 20f, false, true)
        feed(4)

    }
}
