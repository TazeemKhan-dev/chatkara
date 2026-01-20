package com.example.mypos

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btn = findViewById<Button>(R.id.btnPrint)

        btn.setOnClickListener {
            val printer = BluetoothPrinterManager("66:32:78:9C:A9:80")

            printer.printReceipt(
                name = "CHATKARA RESTAURANT",
                billNo = "30674",
                date = "19/01/26 09:15 AM",
                items = listOf(
                    Triple("अमूल बटर अमूल बटर अमूल बटर अमूल बटर अमूल बटर अमूल बटर ", 3, 90),
                    Triple("कुरकुरे मोमोज", 1, 100),
                    Triple("क्रीम बर्गर", 2, 50),
                    Triple("चाउमीन (1/2)", 1, 50),
                    Triple("चिली पोटैटो", 3, 80),
                    Triple("पाव भाजी", 2, 70)
                ),
                total = 900
            )
        }
    }
}
