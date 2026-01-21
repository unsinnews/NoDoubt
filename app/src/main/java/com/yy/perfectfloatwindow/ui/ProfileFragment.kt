package com.yy.perfectfloatwindow.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.ThemeManager

class ProfileFragment : Fragment() {

    private var onThemeChangedListener: (() -> Unit)? = null
    private var onSizeChangedListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenuItems(view)
        setupFloatSize(view)
        applyTheme(view)
        updateApiStatus(view)
        updateThemeDisplay(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            applyTheme(it)
            updateApiStatus(it)
            updateThemeDisplay(it)
        }
    }

    fun setOnThemeChangedListener(listener: () -> Unit) {
        onThemeChangedListener = listener
    }

    fun setOnSizeChangedListener(listener: () -> Unit) {
        onSizeChangedListener = listener
    }

    private fun applyTheme(view: View) {
        val isChatGPT = ThemeManager.isChatGPTTheme(requireContext())

        val scrollView = view.findViewById<ScrollView>(R.id.scrollView)
        val rootLayout = view.findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = view.findViewById<LinearLayout>(R.id.headerLayout)
        val avatarContainer = view.findViewById<FrameLayout>(R.id.avatarContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)

        // Section labels
        val tvSectionAppearance = view.findViewById<TextView>(R.id.tvSectionAppearance)
        val tvSectionSettings = view.findViewById<TextView>(R.id.tvSectionSettings)
        val tvSectionAbout = view.findViewById<TextView>(R.id.tvSectionAbout)

        // Menu items
        val tvThemeTitle = view.findViewById<TextView>(R.id.tvThemeTitle)
        val tvThemeValue = view.findViewById<TextView>(R.id.tvThemeValue)
        val tvSizeTitle = view.findViewById<TextView>(R.id.tvSizeTitle)
        val tvSizeValue = view.findViewById<TextView>(R.id.tvSizeValue)
        val tvApiTitle = view.findViewById<TextView>(R.id.tvApiTitle)
        val tvApiStatus = view.findViewById<TextView>(R.id.tvApiStatus)
        val tvAboutTitle = view.findViewById<TextView>(R.id.tvAboutTitle)
        val tvVersion = view.findViewById<TextView>(R.id.tvVersion)
        val tvFooter = view.findViewById<TextView>(R.id.tvFooter)

        // Icons
        val ivThemeIcon = view.findViewById<ImageView>(R.id.ivThemeIcon)
        val ivSizeIcon = view.findViewById<ImageView>(R.id.ivSizeIcon)
        val ivApiIcon = view.findViewById<ImageView>(R.id.ivApiIcon)
        val ivAboutIcon = view.findViewById<ImageView>(R.id.ivAboutIcon)

        // Sections
        val sectionAppearance = view.findViewById<LinearLayout>(R.id.sectionAppearance)
        val sectionSettings = view.findViewById<LinearLayout>(R.id.sectionSettings)
        val sectionAbout = view.findViewById<LinearLayout>(R.id.sectionAbout)

        if (isChatGPT) {
            // ChatGPT Theme - Light
            scrollView.setBackgroundColor(0xFFF7F7F8.toInt())
            rootLayout.setBackgroundColor(0xFFF7F7F8.toInt())
            headerLayout.setBackgroundColor(0xFFFFFFFF.toInt())
            avatarContainer.setBackgroundResource(R.drawable.float_bg_chatgpt)
            tvTitle.setTextColor(0xFF202123.toInt())

            val primaryColor = 0xFF10A37F.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()
            val sectionBg = 0xFFFFFFFF.toInt()

            tvSectionAppearance.setTextColor(primaryColor)
            tvSectionSettings.setTextColor(primaryColor)
            tvSectionAbout.setTextColor(primaryColor)

            sectionAppearance.setBackgroundColor(sectionBg)
            sectionSettings.setBackgroundColor(sectionBg)
            sectionAbout.setBackgroundColor(sectionBg)

            tvThemeTitle.setTextColor(textPrimary)
            tvThemeValue.setTextColor(textSecondary)
            tvSizeTitle.setTextColor(textPrimary)
            tvSizeValue.setTextColor(primaryColor)
            tvApiTitle.setTextColor(textPrimary)
            tvApiStatus.setTextColor(textSecondary)
            tvAboutTitle.setTextColor(textPrimary)
            tvVersion.setTextColor(textSecondary)
            tvFooter.setTextColor(0xFFACACAC.toInt())

            ivThemeIcon.setColorFilter(textPrimary)
            ivSizeIcon.setColorFilter(textPrimary)
            ivApiIcon.setColorFilter(textPrimary)
            ivAboutIcon.setColorFilter(textPrimary)

        } else {
            // Claude Theme (浅棕黑)
            scrollView.setBackgroundColor(0xFFFAF9F5.toInt())
            rootLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            headerLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            avatarContainer.setBackgroundResource(R.drawable.float_bg_netflix)
            tvTitle.setTextColor(0xFF141413.toInt())

            val primaryColor = 0xFF141413.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()
            val sectionBg = 0xFFFFFFFF.toInt()

            tvSectionAppearance.setTextColor(0xFFD97757.toInt())  // Warm accent
            tvSectionSettings.setTextColor(0xFFD97757.toInt())
            tvSectionAbout.setTextColor(0xFFD97757.toInt())

            sectionAppearance.setBackgroundColor(sectionBg)
            sectionSettings.setBackgroundColor(sectionBg)
            sectionAbout.setBackgroundColor(sectionBg)

            tvThemeTitle.setTextColor(textPrimary)
            tvThemeValue.setTextColor(textSecondary)
            tvSizeTitle.setTextColor(textPrimary)
            tvSizeValue.setTextColor(0xFFD97757.toInt())
            tvApiTitle.setTextColor(textPrimary)
            tvApiStatus.setTextColor(textSecondary)
            tvAboutTitle.setTextColor(textPrimary)
            tvVersion.setTextColor(textSecondary)
            tvFooter.setTextColor(0xFFACACAC.toInt())

            ivThemeIcon.setColorFilter(textPrimary)
            ivSizeIcon.setColorFilter(textPrimary)
            ivApiIcon.setColorFilter(textPrimary)
            ivAboutIcon.setColorFilter(textPrimary)
        }
    }

    private fun setupMenuItems(view: View) {
        // Theme selector
        view.findViewById<View>(R.id.menuTheme).setOnClickListener {
            showThemeDialog()
        }

        // API Settings
        view.findViewById<View>(R.id.menuApiSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // About
        view.findViewById<View>(R.id.menuAbout).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun setupFloatSize(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekBarSize)
        val tvSizeValue = view.findViewById<TextView>(R.id.tvSizeValue)

        val currentSize = ThemeManager.getFloatSize(requireContext())
        seekBar.progress = currentSize
        tvSizeValue.text = "${currentSize}dp"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSizeValue.text = "${progress}dp"
                ThemeManager.setFloatSize(requireContext(), progress)
                onSizeChangedListener?.invoke()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showThemeDialog() {
        val themes = arrayOf("ChatGPT (默认)", "Netflix")
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())
        val selectedIndex = if (currentTheme == ThemeManager.THEME_CHATGPT) 0 else 1

        AlertDialog.Builder(requireContext())
            .setTitle("选择主题")
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val newTheme = if (which == 0) ThemeManager.THEME_CHATGPT else ThemeManager.THEME_NETFLIX
                ThemeManager.setTheme(requireContext(), newTheme)

                view?.let {
                    applyTheme(it)
                    updateThemeDisplay(it)
                }

                onThemeChangedListener?.invoke()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateThemeDisplay(view: View) {
        val tvThemeValue = view.findViewById<TextView>(R.id.tvThemeValue)
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())
        tvThemeValue.text = if (currentTheme == ThemeManager.THEME_CHATGPT) "ChatGPT" else "Netflix"
    }

    private fun updateApiStatus(view: View) {
        val tvApiStatus = view.findViewById<TextView>(R.id.tvApiStatus)
        val apiKey = AISettings.getApiKey(requireContext())

        if (apiKey.isNotBlank()) {
            tvApiStatus.text = "已配置"
            tvApiStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            tvApiStatus.text = "未配置"
            tvApiStatus.setTextColor(0xFFFF5252.toInt())
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("关于")
            .setMessage(
                "AI 解题助手\n\n" +
                "版本: 1.2.0\n\n" +
                "功能:\n" +
                "• 截图识别题目\n" +
                "• AI 智能解答\n" +
                "• 快速/深度两种模式\n" +
                "• 支持多种 AI 模型"
            )
            .setPositiveButton("确定", null)
            .show()
    }
}
