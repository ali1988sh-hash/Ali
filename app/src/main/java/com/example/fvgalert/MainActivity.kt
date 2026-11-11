package com.example.fvgalert

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var key: EditText
    private lateinit var tf: EditText
    private lateinit var min: EditText
    private lateinit var poll: EditText
    private lateinit var touch: CheckBox
    private lateinit var nw: CheckBox
    private lateinit var btn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 28, 0, 4) }.also { root.addView(it) }
        fun edit(v: String, type: Int) = EditText(this).apply { setText(v); inputType = type }.also { root.addView(it) }

        root.addView(TextView(this).apply { text = "FVG MTF Alert — XAU/USD"; textSize = 20f })
        label("Twelve Data API key")
        key = edit(p.getString("key", "") ?: "", InputType.TYPE_CLASS_TEXT)
        label("تایم‌فریم FVG به دقیقه (5 / 15 / 30 / 60)")
        tf = edit(p.getString("tf", "15") ?: "15", InputType.TYPE_CLASS_NUMBER)
        label("حداقل اندازه گپ (دلار)")
        min = edit(p.getString("min", "0") ?: "0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        label("فاصله چک به ثانیه (حداقل ۶۰)")
        poll = edit(p.getString("poll", "150") ?: "150", InputType.TYPE_CLASS_NUMBER)
        touch = CheckBox(this).apply { text = "هشدار برخورد"; isChecked = p.getBoolean("touch", true) }.also { root.addView(it) }
        nw = CheckBox(this).apply { text = "هشدار FVG جدید"; isChecked = p.getBoolean("new", false) }.also { root.addView(it) }

        btn = Button(this).also { root.addView(it) }
        btn.setOnClickListener { if (FvgService.running) stopSvc() else startSvc() }

        root.addView(Button(this).apply {
            text = "غیرفعال کردن بهینه‌سازی باتری (پیشنهادی)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "بعد از شروع، سرویس تو پس‌زمینه با یه اعلان دائمی کار می‌کنه."
            setPadding(0, 24, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        btn.text = if (FvgService.running) "توقف" else "شروع"
    }

    private fun startSvc() {
        if (key.text.isBlank()) { Toast.makeText(this, "کلید API رو وارد کن", Toast.LENGTH_SHORT).show(); return }
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("key", key.text.toString().trim())
            .putString("tf", tf.text.toString())
            .putString("min", min.text.toString())
            .putString("poll", poll.text.toString())
            .putBoolean("touch", touch.isChecked)
            .putBoolean("new", nw.isChecked)
            .apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, FvgService::class.java))
        btn.text = "توقف"
    }

    private fun stopSvc() {
        stopService(Intent(this, FvgService::class.java))
        btn.text = "شروع"
    }
}
