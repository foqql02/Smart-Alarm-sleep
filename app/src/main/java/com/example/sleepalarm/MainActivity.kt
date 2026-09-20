package com.example.sleepalarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentRequest

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etHours = findViewById(R.id.etHours)
        etMinutes = findViewById(R.id.etMinutes)
        btnStart = findViewById(R.id.btnStart)

        requestNeededPermissions()

        btnStart.setOnClickListener {
            val hours = etHours.text.toString().toIntOrNull() ?: 0
            val minutes = etMinutes.text.toString().toIntOrNull() ?: 0
            val totalMinutes = (hours * 60) + minutes

            if (totalMinutes <= 0) {
                Toast.makeText(this, "يرجى إدخال مدة صحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putInt("target_minutes", totalMinutes).apply()

            startSleepTracking()
            tvStatus.text = "الحالة: بانتظار استشعار النوم من الساعة والحساسات..."
            Toast.makeText(this, "تم بدء المراقبة: $hours س و $minutes د", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    1001
                )
            }
        }
    }

    private fun startSleepTracking() {
        val intent = Intent(this, SleepReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            ActivityRecognition.getClient(this)
                .requestSleepSegmentUpdates(
                    pendingIntent,
                    SleepSegmentRequest.getDefaultSleepSegmentRequest()
                )
        } catch (e: SecurityException) {
            tvStatus.text = "يرجى منح صلاحية تتبع النشاط"
        }
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SleepClassifyEvent.hasEvents(intent)) {
            val events = SleepClassifyEvent.extractEvents(intent)
            for (event in events) {
                // التأكد من النوم بنسبة ثقة أعلى من 70%
                if (event.confidence >= 70) {
                    val sharedPref = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
                    val totalMinutes = sharedPref.getInt("target_minutes", 180)
                    
                    scheduleAlarm(context, totalMinutes)
                    
                    val pIntent = PendingIntent.getBroadcast(
                        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pIntent)
                    break
                }
            }
        }
    }

    private fun scheduleAlarm(context: Context, totalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + (totalMinutes * 60 * 1000L)

        val alarmIntent = Intent(context, AlarmTriggerReceiver::class.java).let {
            PendingIntent.getBroadcast(context, 102, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent)
        }
    }
}

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
        ringtone?.play()
        Toast.makeText(context, "حان وقت الاستيقاظ!", Toast.LENGTH_LONG).show()
    }
}
