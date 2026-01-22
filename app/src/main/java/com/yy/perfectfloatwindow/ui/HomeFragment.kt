package com.yy.perfectfloatwindow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.yy.floatserver.utils.SettingsCompat
import com.yy.perfectfloatwindow.MainActivity
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.ThemeManager

class HomeFragment : Fragment() {

    private lateinit var switchFloat: SwitchCompat
    private lateinit var tvFloatStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchFloat = view.findViewById(R.id.switchFloat)
        tvFloatStatus = view.findViewById(R.id.tvFloatStatus)

        setupSwitch()
        applyTheme(view)

        // Sync switch state with MainActivity
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            switchFloat.isChecked = it.isFloatShowing
            updateStatusText(it.isFloatShowing)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { applyTheme(it) }

        // Check permission state
        if (switchFloat.isChecked && !SettingsCompat.canDrawOverlays(requireContext())) {
            switchFloat.isChecked = false
            tvFloatStatus.text = "点击开启后，悬浮球将显示在屏幕上"
            (activity as? MainActivity)?.let { it.isFloatShowing }
        }

        // Sync with MainActivity state
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            if (switchFloat.isChecked != it.isFloatShowing) {
                switchFloat.isChecked = it.isFloatShowing
                updateStatusText(it.isFloatShowing)
            }
        }
    }

    private fun applyTheme(view: View) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())

        val rootLayout = view.findViewById<LinearLayout>(R.id.rootLayout)
        val iconContainer = view.findViewById<FrameLayout>(R.id.iconContainer)
        val ivAppIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
        val tvAppName = view.findViewById<TextView>(R.id.tvAppName)
        val tvAppDesc = view.findViewById<TextView>(R.id.tvAppDesc)
        val cardFloatToggle = view.findViewById<MaterialCardView>(R.id.cardFloatToggle)
        val ivFloatIcon = view.findViewById<ImageView>(R.id.ivFloatIcon)
        val tvFloatTitle = view.findViewById<TextView>(R.id.tvFloatTitle)
        val tipLayout = view.findViewById<LinearLayout>(R.id.tipLayout)
        val tvTipTitle = view.findViewById<TextView>(R.id.tvTipTitle)
        val tvTipContent = view.findViewById<TextView>(R.id.tvTipContent)

        if (isLightGreenGray) {
            // 浅绿灰主题
            rootLayout.setBackgroundColor(0xFFFFFFFF.toInt())
            iconContainer.setBackgroundResource(R.drawable.float_bg_light_green_gray)
            ivAppIcon.setColorFilter(0xFFFFFFFF.toInt())  // White icon
            tvAppName.setTextColor(0xFF202123.toInt())
            tvAppDesc.setTextColor(0xFF6E6E80.toInt())
            cardFloatToggle.setCardBackgroundColor(0xFFF7F7F8.toInt())
            ivFloatIcon.setBackgroundResource(R.drawable.bg_icon_circle)
            ivFloatIcon.setColorFilter(0xFF10A37F.toInt())
            tvFloatTitle.setTextColor(0xFF202123.toInt())
            tvFloatStatus.setTextColor(0xFF6E6E80.toInt())
            tipLayout.setBackgroundResource(R.drawable.bg_tip_light_green_gray)
            tvTipTitle.setTextColor(0xFF10A37F.toInt())
            tvTipContent.setTextColor(0xFF6E6E80.toInt())
        } else {
            // 浅棕黑主题
            rootLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            iconContainer.setBackgroundResource(R.drawable.float_bg_light_brown_black)
            ivAppIcon.setColorFilter(0xFFFAF9F5.toInt())  // Light beige icon
            tvAppName.setTextColor(0xFF141413.toInt())
            tvAppDesc.setTextColor(0xFF666666.toInt())
            cardFloatToggle.setCardBackgroundColor(0xFFF5F4F0.toInt())
            ivFloatIcon.setBackgroundResource(R.drawable.bg_icon_circle_light_brown_black)
            ivFloatIcon.setColorFilter(0xFFDA7A5A.toInt())  // Warm orange accent
            tvFloatTitle.setTextColor(0xFF141413.toInt())
            tvFloatStatus.setTextColor(0xFF666666.toInt())
            tipLayout.setBackgroundResource(R.drawable.bg_tip_light_brown_black)
            tvTipTitle.setTextColor(0xFFDA7A5A.toInt())  // Warm orange accent
            tvTipContent.setTextColor(0xFF666666.toInt())
        }
    }

    private fun setupSwitch() {
        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            val mainActivity = activity as? MainActivity ?: return@setOnCheckedChangeListener

            if (isChecked) {
                mainActivity.showFloatWindow()
            } else {
                mainActivity.hideFloatWindow()
            }
            updateStatusText(isChecked)
        }
    }

    private fun updateStatusText(isShowing: Boolean) {
        tvFloatStatus.text = if (isShowing) {
            "悬浮球已开启，点击可截图解题"
        } else {
            "点击开启后，悬浮球将显示在屏幕上"
        }
    }

    fun updateSwitchState(isShowing: Boolean) {
        if (::switchFloat.isInitialized) {
            switchFloat.isChecked = isShowing
            updateStatusText(isShowing)
        }
    }
}
