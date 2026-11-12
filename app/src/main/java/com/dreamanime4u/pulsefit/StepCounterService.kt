
package com.dreamanime4u.pulsefit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START =
            "com.dreamanime4u.pulsefit.START"

        const val ACTION_STOP =
            "com.dreamanime4u.pulsefit.STOP"

        const val ACTION_RESET =
            "com.dreamanime4u.pulsefit.RESET"

        const val ACTION_UPDATE =
            "com.dreamanime4u.pulsefit.UPDATE"

        const val EXTRA_STEPS = "steps"

        private const val CHANNEL_ID = "pulsefit_steps"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "pulsefit_steps_data"
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var registered = false

    private val prefs by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private var todaySteps = 0L
    private var lastSensorValue = -1f
    private var savedDate = ""

    override fun onCreate() {
        super.onCreate()

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        stepSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        loadData()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }

            ACTION_RESET -> {
                resetSteps()
            }
        }

        if (intent?.action == ACTION_START ||
            intent?.action == ACTION_RESET ||
            intent == null
        ) {
            startTracking()
        }

        return START_STICKY
    }

    private fun startTracking() {

        if (Build.VERSION.SDK_INT >= 29 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
                )
            }
        } else {
            startForeground(
                NOTIFICATION_ID,
                buildNotification()
            )
        }

        if (stepSensor == null) {
            updateNotification("مستشعر الخطوات غير متوفر")
            broadcastSteps()
            return
        }

        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("يرجى السماح بإذن النشاط البدني")
            return
        }

        if (!registered) {
            registered = sensorManager.registerListener(
                this,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        broadcastSteps()
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) {
            return
        }

        val currentValue = event.values[0]

        val currentDate = getTodayDate()

        if (savedDate != currentDate) {
            todaySteps = 0L
            lastSensorValue = currentValue
            savedDate = currentDate
        } else if (lastSensorValue < 0f) {
            lastSensorValue = currentValue
        } else if (currentValue >= lastSensorValue) {

            val difference =
                (currentValue - lastSensorValue).toLong()

            todaySteps += difference
            lastSensorValue = currentValue

        } else {
            // The sensor counter may reset after a device reboot.
            todaySteps += currentValue.toLong()
            lastSensorValue = currentValue
        }

        saveData()
        broadcastSteps()

        updateNotification(
            "الخطوات اليوم: $todaySteps"
        )
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // No action required.
    }

    private fun resetSteps() {
        todaySteps = 0L
        savedDate = getTodayDate()

        saveData()
        broadcastSteps()
    }

    private fun stopTracking() {

        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())
    }

    private fun loadData() {

        todaySteps = prefs.getLong("today_steps", 0L)
        lastSensorValue = prefs.getFloat("last_sensor", -1f)
        savedDate = prefs.getString("saved_date", "") ?: ""

        if (savedDate != getTodayDate()) {
            todaySteps = 0L
            savedDate = getTodayDate()
            lastSensorValue = -1f
            saveData()
        }
    }

    private fun saveData() {

        prefs.edit()
            .putLong("today_steps", todaySteps)
            .putFloat("last_sensor", lastSensorValue)
            .putString("saved_date", savedDate)
            .apply()
    }

    private fun broadcastSteps() {

        val updateIntent = Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STEPS, todaySteps)
        }

        sendBroadcast(updateIntent)
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "PulseFit Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "متابعة الخطوات اليومية"
            }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PulseFit")
            .setContentText("جاري متابعة خطواتك")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(message: String) {

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("PulseFit")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {

        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }

        super.onDestroy()
    }
}
