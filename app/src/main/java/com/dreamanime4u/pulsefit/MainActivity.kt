
package com.dreamanime4u.pulsefit

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button

class MainActivity : Activity() {

    private lateinit var stepsText: TextView

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
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        stepsText = TextView(this).apply {
            text = "0"
            textSize = 64f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
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

        val distance = TextView(this).apply {
            text = "Distance: 0.00 km"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val calories = TextView(this).apply {
            text = "Calories: 0 kcal"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "START"
        }

        val stopButton = Button(this).apply {
            text = "STOP"
        }

        val resetButton = Button(this).apply {
            text = "RESET"
        }

        root.addView(title)
        root.addView(stepsText)
        root.addView(label)
        root.addView(goal)
        root.addView(distance)
        root.addView(calories)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(resetButton)

        setContentView(root)
    }
}
