package com.yy.perfectfloatwindow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yy.floatserver.FloatClient
import com.yy.floatserver.FloatHelper
import com.yy.floatserver.IFloatClickListener
import com.yy.floatserver.IFloatPermissionCallback
import com.yy.floatserver.utils.SettingsCompat
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.ThemeManager
import com.yy.perfectfloatwindow.screenshot.ScreenshotService
import com.yy.perfectfloatwindow.ui.AnswerPopupService
import com.yy.perfectfloatwindow.ui.HomeFragment
import com.yy.perfectfloatwindow.ui.ProfileFragment
import com.yy.perfectfloatwindow.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private var homeFragment: HomeFragment? = null
    private var profileFragment: ProfileFragment? = null

    // Float window management
    var floatHelper: FloatHelper? = null
        private set
    private lateinit var floatView: View
    var isFloatShowing = false
        private set

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenshotService(result.resultCode, result.data!!)
            Toast.makeText(this, "截图服务已就绪", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        setupFloatWindow()

        // Set default fragment
        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, homeFragment!!)
                .commit()
        }

        setupBottomNavigation()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        updateFloatViewTheme()

        // Check permission state
        if (isFloatShowing && !SettingsCompat.canDrawOverlays(this)) {
            isFloatShowing = false
            homeFragment?.updateSwitchState(false)
        }
    }

    private fun setupFloatWindow() {
        floatView = View.inflate(this, R.layout.float_view, null)

        floatHelper = FloatClient.Builder()
            .with(this)
            .addView(floatView)
            .enableDefaultPermissionDialog(true)
            .setClickListener(object : IFloatClickListener {
                override fun onFloatClick() {
                    takeScreenshot()
                }
            })
            .addPermissionCallback(object : IFloatPermissionCallback {
                override fun onPermissionResult(granted: Boolean) {
                    if (granted) {
                        requestScreenshotPermission()
                    } else {
                        floatHelper?.requestPermission()
                    }
                }
            })
            .build()

        updateFloatViewTheme()
    }

    fun showFloatWindow() {
        floatHelper?.show()
        isFloatShowing = true
    }

    fun hideFloatWindow() {
        floatHelper?.dismiss()
        isFloatShowing = false
    }

    fun requestScreenshotPermission() {
        if (!ScreenshotService.isServiceRunning) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun startScreenshotService(resultCode: Int, data: Intent) {
        ScreenshotService.setScreenshotCallback(object : ScreenshotService.Companion.ScreenshotCallback {
            override fun onScreenshotCaptured(bitmap: Bitmap) {
                AnswerPopupService.show(this@MainActivity, bitmap)
            }

            override fun onScreenshotFailed(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })

        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun takeScreenshot() {
        val apiKey = AISettings.getApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, "请先到「我的」页面配置 API", Toast.LENGTH_LONG).show()
            return
        }

        if (ScreenshotService.isServiceRunning) {
            ScreenshotService.requestScreenshot()
        } else {
            Toast.makeText(this, "请先开启悬浮球", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateFloatViewTheme() {
        // Keep gray background regardless of theme
        val container = floatView.findViewById<FrameLayout>(R.id.llContainer)
        container?.setBackgroundResource(R.drawable.float_bg_gray)
        updateFloatSize()
    }

    fun updateFloatSize() {
        val sizeDp = ThemeManager.getFloatSize(this)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val iconSizePx = (sizeDp * 0.7 * density).toInt()

        val container = floatView.findViewById<FrameLayout>(R.id.llContainer)
        val icon = floatView.findViewById<ImageView>(R.id.ivIcon)

        container?.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            container.layoutParams = it
        }

        icon?.layoutParams?.let {
            it.width = iconSizePx
            it.height = iconSizePx
            icon.layoutParams = it
        }
    }

    private fun applyTheme() {
        val isChatGPT = ThemeManager.isChatGPTTheme(this)

        val fragmentContainer = findViewById<View>(R.id.fragmentContainer)

        if (isChatGPT) {
            // ChatGPT Theme
            window.statusBarColor = 0xFFFFFFFF.toInt()
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            bottomNavigation.setBackgroundColor(0xFFFFFFFF.toInt())
            fragmentContainer.setBackgroundColor(0xFFFFFFFF.toInt())
        } else {
            // Claude Theme (浅棕黑)
            window.statusBarColor = 0xFFFAF9F5.toInt()
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            bottomNavigation.setBackgroundColor(0xFFFAF9F5.toInt())
            fragmentContainer.setBackgroundColor(0xFFFAF9F5.toInt())
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(getHomeFragment())
                    true
                }
                R.id.nav_profile -> {
                    switchFragment(getProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun getHomeFragment(): Fragment {
        if (homeFragment == null) {
            homeFragment = HomeFragment()
        }
        return homeFragment!!
    }

    private fun getProfileFragment(): Fragment {
        if (profileFragment == null) {
            profileFragment = ProfileFragment()
            profileFragment?.setOnThemeChangedListener {
                applyTheme()
                updateFloatViewTheme()
                // Recreate home fragment to apply new theme
                homeFragment = null
            }
            profileFragment?.setOnSizeChangedListener {
                updateFloatSize()
            }
        }
        return profileFragment!!
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatHelper?.release()
    }
}
