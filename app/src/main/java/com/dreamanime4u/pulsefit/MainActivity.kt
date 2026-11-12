
package com.dreamanime4u.pulsefit

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var stepsText: TextView
    private lateinit var distanceText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var remainingText: TextView

    private var receiverRegistered = false
    private var resetAfterPermission = false

    companion object {
        private const val PERMISSION_REQUEST = 101
        private const val DAILY_GOAL = 10000L
        private const val KM_PER_STEP = 0.0007
        private const val CALORIES_PER_STEP = 0.04
    }

    private val stepsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent?.action == StepCounterService.ACTION_UPDATE) {

                val steps = intent.getLongExtra(
                    StepCounterService.EXTRA_STEPS,
                    0L
                )

                updateDisplay(steps)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 13, 30)
        window.navigationBarColor = Color.rgb(13, 13, 30)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 35, 28, 35)
            setBackgroundColor(Color.rgb(13, 13, 30))
        }

        val title = TextView(this).apply {
            text = "PulseFit"
            textSize = 34f
            setTextColor(Color.rgb(174, 85, 255))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        stepsText = TextView(this).apply {
            text = "0"
            textSize = 64f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val label = TextView(this).apply {
            text = "STEPS"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val goal = TextView(this).apply {
            text = "Daily Goal: 10,000 steps"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        distanceText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        caloriesText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        remainingText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(174, 85, 255))
            gravity = Gravity.CENTER
        }

        fun makeButton(text: String, color: Int): Button {
            return Button(this).apply {
                this.text = text
                backgroundTintList = ColorStateList.valueOf(color)
                setTextColor(Color.WHITE)
            }
        }

        val startButton = makeButton(
            "START",
            Color.rgb(45, 100, 220)
        )

        val stopButton = makeButton(
            "STOP",
            Color.rgb(90, 90, 110)
        )

        val resetButton = makeButton(
            "RESET",
            Color.rgb(150, 55, 75)
        )

        startButton.setOnClickListener {
            requestPermissionsAndStart()
        }

        stopButton.setOnClickListener {
            val intent = Intent(
                this,
                StepCounterService::class.java
            ).apply {
                action = StepCounterService.ACTION_STOP
            }

            startService(intent)
        }

        resetButton.setOnClickListener {

            if (
                Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                resetAfterPermission = true

                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ),
                    PERMISSION_REQUEST
                )

            } else {
                performReset()
            }
        }

        root.addView(title)
        root.addView(stepsText)
        root.addView(label)
        root.addView(goal)
        root.addView(distanceText)
        root.addView(caloriesText)
        root.addView(remainingText)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(resetButton)

        setContentView(root)

        val filter = IntentFilter(
            StepCounterService.ACTION_UPDATE
        )

        ContextCompat.registerReceiver(
            this,
            stepsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        receiverRegistered = true

        val prefs = getSharedPreferences(
            "pulsefit_steps_data",
            Context.MODE_PRIVATE
        )

        updateDisplay(
            prefs.getLong("today_steps", 0L)
        )
    }

    private fun updateDisplay(steps: Long) {

        val distance = steps * KM_PER_STEP
        val calories = steps * CALORIES_PER_STEP

        val remaining = (
            DAILY_GOAL - steps
        ).coerceAtLeast(0L)

        stepsText.text = steps.toString()

        distanceText.text = String.format(
            Locale.US,
            "Distance: %.2f km",
            distance
        )

        caloriesText.text = String.format(
            Locale.US,
            "Calories: %.0f kcal",
            calories
        )

        remainingText.text =
            "Remaining: $remaining steps"
    }

    private fun requestPermissionsAndStart() {

        val needed = mutableListOf<String>()

        if (
            Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        if (needed.isNotEmpty()) {

            requestPermissions(
                needed.toTypedArray(),
                PERMISSION_REQUEST
            )

        } else {
            startTracking()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == PERMISSION_REQUEST) {

            val activityGranted =
                Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission(
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

            if (activityGranted) {

                if (resetAfterPermission) {
                    resetAfterPermission = false
                    performReset()
                } else {
                    startTracking()
                }

            } else {
                resetAfterPermission = false
            }
        }
    }

    private fun startTracking() {

        val intent = Intent(
            this,
            StepCounterService::class.java
        ).apply {
            action = StepCounterService.ACTION_START
        }

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun performReset() {

        val intent = Intent(
            this,
            StepCounterService::class.java
        ).apply {
            action = StepCounterService.ACTION_RESET
        }

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    override fun onDestroy() {

        if (receiverRegistered) {
            unregisterReceiver(stepsReceiver)
            receiverRegistered = false
        }

        super.onDestroy()
    }
}
