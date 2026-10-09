package com.zhafrisbs.liftingplancalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhafrisbs.liftingplancalculator.ui.LiftingPlanApp
import com.zhafrisbs.liftingplancalculator.ui.theme.LiftingPlanCalculatorTheme

/**
 * The app's single Activity.
 *
 * One Activity rather than one per tab: the whole app is five forms over one
 * shared lift, so a second Activity would only be a way for two screens to
 * disagree about what is loaded.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, so the first frame is already edge-to-edge and the window
        // does not visibly jump once the content is drawn.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LiftingPlanCalculatorTheme {
                LiftingPlanApp()
            }
        }
    }
}
