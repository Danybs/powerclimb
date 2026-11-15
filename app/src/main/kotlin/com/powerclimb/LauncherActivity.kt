package com.powerclimb

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageButton

/** Shows app information and shares Back events with the physical button. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher_summary)

        val buttonBack = findViewById<ImageButton>(R.id.button_back)
        buttonBack.setOnClickListener {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
    }
}
