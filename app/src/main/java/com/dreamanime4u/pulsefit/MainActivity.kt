
package com.dreamanime4u.pulsefit

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

data class MetricCard(
    val container: LinearLayout,
    val label: TextView,
    val value: TextView
)

class ProgressRingView(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 48, 72)
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(174, 105, 255)
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 17f
        textAlign = Paint.Align.CENTER
    }

    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var steps = 0L
        set(value) {
            field = value
            invalidate()
        }

    var stepsLabel = "STEPS"
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - 20f

        val rect = RectF(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        canvas.drawArc(
            rect,
            -90f,
            360f * progress,
            false,
            progressPaint
        )

        canvas.drawText(
            String.format(Locale.US, "%,d", steps),
            cx,
            cy + 8f,
            numberPaint
        )

        canvas.drawText(
            stepsLabel,
            cx,
            cy + 42f,
            labelPaint
        )
    }
}

class MainActivity : Activity() {

    companion object {
        private const val PERMISSION_REQUEST = 101
        private const val DAILY_GOAL = 10000L

        private const val KM_PER_STEP = 0.0007
        private const val CALORIES_PER_STEP = 0.04

        private val BG = 0xFF0D1020.toInt()
        private val CARD = 0xFF191E35.toInt()
        private val PURPLE = 0xFFAE69FF.toInt()
        private val CYAN = 0xFF48D9E8.toInt()
        private val WHITE = Color.WHITE
    }

    private lateinit var progressRing: ProgressRingView

    private lateinit var distanceText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var remainingText: TextView

    private lateinit var distanceLabel: TextView
    private lateinit var caloriesLabel: TextView
    private lateinit var remainingLabel: TextView

    private lateinit var subtitleText: TextView
    private lateinit var goalText: TextView
    private lateinit var statisticsTitle: TextView
    private lateinit var controlsTitle: TextView

    private lateinit var languageButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var resetButton: Button

    private var receiverRegistered = false
    private var resetAfterPermission = false
    private var selectedLanguage = "en"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        color: Int,
        radius: Int = 20
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeText(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun makeCard(label: String, color: Int): MetricCard {

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = roundedBackground(CARD)
        }

        val labelView = makeText(label, 13f, Color.LTGRAY, true)
        val valueView = makeText("--", 22f, color, true)

        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            valueView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        return MetricCard(container, labelView, valueView)
    }

    private fun addSectionTitle(
        parent: LinearLayout,
        text: String
    ): TextView {

        val view = makeText(text, 18f, WHITE, true)

        view.gravity = Gravity.START or Gravity.CENTER_VERTICAL

        parent.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        return view
    }

    private fun addButton(
        parent: LinearLayout,
        text: String,
        color: Int
    ): Button {

        val button = Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(WHITE)
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false

            backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        return button
    }

    private val stepsReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
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

        window.statusBarColor = BG
        window.navigationBarColor = BG

        selectedLanguage = getSharedPreferences(
            "pulsefit_ui_settings",
            MODE_PRIVATE
        ).getString("selected_language", "en") ?: "en"

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(24))

            layoutDirection = if (selectedLanguage == "ar") {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
        }

        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val appTitle = makeText("PulseFit", 28f, PURPLE, true)
        appTitle.gravity = Gravity.START or Gravity.CENTER_VERTICAL

        header.addView(
            appTitle,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        languageButton = Button(this).apply {
            textSize = 12f
            setTextColor(WHITE)
            isAllCaps = false

            backgroundTintList =
                android.content.res.ColorStateList.valueOf(CARD)
        }

        header.addView(languageButton)
        root.addView(header)

        subtitleText = makeText("", 14f, Color.LTGRAY)
        subtitleText.gravity = Gravity.START

        root.addView(subtitleText)

        val ringContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(10))
        }

        progressRing = ProgressRingView(this)

        ringContainer.addView(
            progressRing,
            LinearLayout.LayoutParams(dp(230), dp(230))
        )

        goalText = makeText("", 15f, WHITE, true)

        ringContainer.addView(
            goalText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
            )
        )

        root.addView(ringContainer)

        statisticsTitle = addSectionTitle(root, "")

        val distanceCard = makeCard("", CYAN)
        val caloriesCard = makeCard("", PURPLE)
        val remainingCard = makeCard("", WHITE)

        distanceLabel = distanceCard.label
        caloriesLabel = caloriesCard.label
        remainingLabel = remainingCard.label

        distanceText = distanceCard.value
        caloriesText = caloriesCard.value
        remainingText = remainingCard.value

        fun addCardRow(first: MetricCard, second: MetricCard) {

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            row.addView(
                first.container,
                LinearLayout.LayoutParams(0, dp(100), 1f).apply {
                    marginEnd = dp(6)
                    bottomMargin = dp(10)
                }
            )

            row.addView(
                second.container,
                LinearLayout.LayoutParams(0, dp(100), 1f).apply {
                    marginStart = dp(6)
                    bottomMargin = dp(10)
                }
            )

            root.addView(row)
        }

        addCardRow(distanceCard, caloriesCard)

        val remainingRow = LinearLayout(this)

        remainingRow.addView(
            remainingCard.container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            )
        )

        root.addView(remainingRow)

        controlsTitle = addSectionTitle(root, "")

        startButton = addButton(root, "", 0xFF4169E1.toInt())
        stopButton = addButton(root, "", 0xFF555B73.toInt())
        resetButton = addButton(root, "", 0xFF9D4568.toInt())

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

        languageButton.setOnClickListener {

            selectedLanguage = when (selectedLanguage) {
                "en" -> "ar"
                "ar" -> "fr"
                else -> "en"
            }

            getSharedPreferences(
                "pulsefit_ui_settings",
                MODE_PRIVATE
            ).edit()
                .putString("selected_language", selectedLanguage)
                .apply()

            recreate()
        }

        setContentView(scroll)

        applyTranslations()

        ContextCompat.registerReceiver(
            this,
            stepsReceiver,
            IntentFilter(StepCounterService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        receiverRegistered = true

        val prefs = getSharedPreferences(
            "pulsefit_steps_data",
            MODE_PRIVATE
        )

        updateDisplay(
            prefs.getLong("today_steps", 0L)
        )
    }

    private fun applyTranslations() {

        val ar = selectedLanguage == "ar"
        val fr = selectedLanguage == "fr"

        subtitleText.text = when {
            ar -> "تابع نشاطك وحقق أهدافك اليومية"
            fr -> "Suivez votre activité et atteignez vos objectifs"
            else -> "Track your activity and reach your daily goals"
        }

        statisticsTitle.text = when {
            ar -> "الإحصائيات"
            fr -> "Statistiques"
            else -> "Statistics"
        }

        controlsTitle.text = when {
            ar -> "التحكم"
            fr -> "Commandes"
            else -> "Controls"
        }

        goalText.text = when {
            ar -> "الهدف اليومي: 10,000 خطوة"
            fr -> "Objectif quotidien : 10 000 pas"
            else -> "Daily Goal: 10,000 steps"
        }

        distanceLabel.text = when {
            ar -> "المسافة"
            fr -> "DISTANCE"
            else -> "DISTANCE"
        }

        caloriesLabel.text = when {
            ar -> "السعرات"
            fr -> "CALORIES"
            else -> "CALORIES"
        }

        remainingLabel.text = when {
            ar -> "المتبقي"
            fr -> "RESTANT"
            else -> "REMAINING"
        }

        startButton.text = when {
            ar -> "ابدأ"
            fr -> "DÉMARRER"
            else -> "START"
        }

        stopButton.text = when {
            ar -> "إيقاف"
            fr -> "ARRÊTER"
            else -> "STOP"
        }

        resetButton.text = when {
            ar -> "إعادة ضبط"
            fr -> "RÉINITIALISER"
            else -> "RESET"
        }

        languageButton.text = when (selectedLanguage) {
            "ar" -> "العربية"
            "fr" -> "Français"
            else -> "English"
        }

        progressRing.stepsLabel = when {
            ar -> "خطوة"
            fr -> "PAS"
            else -> "STEPS"
        }

        val direction = if (ar) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }

        rootDirection(direction)
    }

    private fun rootDirection(direction: Int) {
        findViewById<View>(android.R.id.content)
            ?.layoutDirection = direction
    }

    private fun updateDisplay(steps: Long) {

        val safeSteps = steps.coerceAtLeast(0L)

        val distance = safeSteps * KM_PER_STEP
        val calories = safeSteps * CALORIES_PER_STEP

        val remaining = (
            DAILY_GOAL - safeSteps
        ).coerceAtLeast(0L)

        progressRing.steps = safeSteps

        progressRing.progress =
            safeSteps.toFloat() / DAILY_GOAL.toFloat()

        distanceText.text = String.format(
            Locale.US,
            "%.2f km",
            distance
        )

        caloriesText.text = String.format(
            Locale.US,
            "%.0f kcal",
            calories
        )

        remainingText.text = String.format(
            Locale.US,
            "%,d",
            remaining
        )
    }

    private fun requestPermissionsAndStart() {

        val needed = mutableListOf<String>()

        if (
            Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
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

        if (requestCode != PERMISSION_REQUEST) return

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

    private fun startTracking() {

        val intent = Intent(
            this,
            StepCounterService::class.java
        ).apply {
            action = StepCounterService.ACTION_START
        }

        ContextCompat.startForegroundService(this, intent)
    }

    private fun performReset() {

        val intent = Intent(
            this,
            StepCounterService::class.java
        ).apply {
            action = StepCounterService.ACTION_RESET
        }

        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {

        if (receiverRegistered) {
            unregisterReceiver(stepsReceiver)
            receiverRegistered = false
        }

        super.onDestroy()
    }
}
