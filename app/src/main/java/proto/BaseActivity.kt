package proto

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import tools.ReportBug

//BaseActivity implements basic edge-to-edge actions and activates bug reporting

abstract class BaseActivity : AppCompatActivity() {

    abstract fun getAppBarId() : Int
    abstract fun isLightTheme() : Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val appbar = findViewById<View>(getAppBarId())
            appbar?.updatePadding(
                top = systemBars.top
            )
            window.decorView.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            if (isLightTheme()) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
            windowInsets
        }
        ReportBug.enableMonitor(this)
    }
}