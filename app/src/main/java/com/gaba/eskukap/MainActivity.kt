package com.gaba.eskukap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val file = File(filesDir, "fake.jpg")
        contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
        // сохраняем путь в SharedPreferences
        getSharedPreferences("fakecam", Context.MODE_PRIVATE)
            .edit()
            .putString("photo_path", file.absolutePath)
            .apply()
        Toast.makeText(this, "Фото сохранено: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40,40,40,40)
        }

        val btnPick = Button(this).apply { text = "Выбрать фото из галереи" }
        val btnMakeSd = Button(this).apply { text = "Создать папку на SD (если нужно)" }

        btnPick.setOnClickListener {
            pickLauncher.launch("image/*")
        }

        btnMakeSd.setOnClickListener {
            // создаём папку на sdcard для совместимости с хуком
            val dir = File(getExternalFilesDir(null), "fake_camera_files")
            if (!dir.exists()) dir.mkdirs()
            val sample = File(dir, "readme.txt")
            sample.writeText("FakeCamera folder")
            Toast.makeText(this, "Папка создана: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        }

        layout.addView(btnPick)
        layout.addView(btnMakeSd)
        setContentView(layout)
    }
}
