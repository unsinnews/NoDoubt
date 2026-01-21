package com.yy.perfectfloatwindow.ui

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())

        val scrollView = view.findViewById<ScrollView>(R.id.scrollView)
        val rootLayout = view.findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = view.findViewById<LinearLayout>(R.id.headerLayout)
        val avatarContainer = view.findViewById<FrameLayout>(R.id.avatarContainer)
        val ivAvatar = view.findViewById<ImageView>(R.id.ivAvatar)
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

        // SeekBar
        val seekBarSize = view.findViewById<SeekBar>(R.id.seekBarSize)

        if (isLightGreenGray) {
            // 浅绿灰主题
            scrollView.setBackgroundColor(0xFFF7F7F8.toInt())
            rootLayout.setBackgroundColor(0xFFF7F7F8.toInt())
            headerLayout.setBackgroundColor(0xFFFFFFFF.toInt())
            avatarContainer.setBackgroundResource(R.drawable.float_bg_light_green_gray)
            ivAvatar.setColorFilter(0xFFFFFFFF.toInt())  // White shield icon
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

            // SeekBar theming
            seekBarSize.progressTintList = ColorStateList.valueOf(primaryColor)
            seekBarSize.thumbTintList = ColorStateList.valueOf(primaryColor)

        } else {
            // 浅棕黑主题
            scrollView.setBackgroundColor(0xFFFAF9F5.toInt())
            rootLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            headerLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            avatarContainer.setBackgroundResource(R.drawable.float_bg_light_brown_black)
            ivAvatar.setColorFilter(0xFFFAF9F5.toInt())  // Light beige shield icon
            tvTitle.setTextColor(0xFF141413.toInt())

            val accentColor = 0xFFDA7A5A.toInt()  // Warm orange accent
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()
            val sectionBg = 0xFFFFFFFF.toInt()

            tvSectionAppearance.setTextColor(accentColor)
            tvSectionSettings.setTextColor(accentColor)
            tvSectionAbout.setTextColor(accentColor)

            sectionAppearance.setBackgroundColor(sectionBg)
            sectionSettings.setBackgroundColor(sectionBg)
            sectionAbout.setBackgroundColor(sectionBg)

            tvThemeTitle.setTextColor(textPrimary)
            tvThemeValue.setTextColor(textSecondary)
            tvSizeTitle.setTextColor(textPrimary)
            tvSizeValue.setTextColor(accentColor)
            tvApiTitle.setTextColor(textPrimary)
            tvApiStatus.setTextColor(textSecondary)
            tvAboutTitle.setTextColor(textPrimary)
            tvVersion.setTextColor(textSecondary)
            tvFooter.setTextColor(0xFFACACAC.toInt())

            ivThemeIcon.setColorFilter(textPrimary)
            ivSizeIcon.setColorFilter(textPrimary)
            ivApiIcon.setColorFilter(textPrimary)
            ivAboutIcon.setColorFilter(textPrimary)

            // SeekBar theming
            seekBarSize.progressTintList = ColorStateList.valueOf(accentColor)
            seekBarSize.thumbTintList = ColorStateList.valueOf(accentColor)
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
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_theme_selector)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Get views
        val optionLightGreenGray = dialog.findViewById<LinearLayout>(R.id.optionLightGreenGray)
        val optionLightBrownBlack = dialog.findViewById<LinearLayout>(R.id.optionLightBrownBlack)
        val tvOption1Title = dialog.findViewById<TextView>(R.id.tvOption1Title)
        val tvOption2Title = dialog.findViewById<TextView>(R.id.tvOption2Title)
        val ivCheck1 = dialog.findViewById<ImageView>(R.id.ivCheck1)
        val ivCheck2 = dialog.findViewById<ImageView>(R.id.ivCheck2)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)

        // Apply theme colors
        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF202123.toInt() else 0xFF141413.toInt()

        tvOption1Title.setTextColor(textPrimary)
        tvOption2Title.setTextColor(textPrimary)
        ivCheck1.setColorFilter(primaryColor)
        ivCheck2.setColorFilter(primaryColor)

        // Set selected state
        if (currentTheme == ThemeManager.THEME_LIGHT_GREEN_GRAY) {
            optionLightGreenGray.setBackgroundResource(
                if (isLightGreenGray) R.drawable.bg_dialog_item_selected_light_green_gray
                else R.drawable.bg_dialog_item_selected_light_brown_black
            )
            ivCheck1.visibility = View.VISIBLE
            optionLightBrownBlack.setBackgroundResource(R.drawable.bg_dialog_item_unselected)
            ivCheck2.visibility = View.GONE
        } else {
            optionLightBrownBlack.setBackgroundResource(
                if (isLightGreenGray) R.drawable.bg_dialog_item_selected_light_green_gray
                else R.drawable.bg_dialog_item_selected_light_brown_black
            )
            ivCheck2.visibility = View.VISIBLE
            optionLightGreenGray.setBackgroundResource(R.drawable.bg_dialog_item_unselected)
            ivCheck1.visibility = View.GONE
        }

        // Click listeners
        optionLightGreenGray.setOnClickListener {
            ThemeManager.setTheme(requireContext(), ThemeManager.THEME_LIGHT_GREEN_GRAY)
            view?.let {
                applyTheme(it)
                updateThemeDisplay(it)
            }
            onThemeChangedListener?.invoke()
            dialog.dismiss()
        }

        optionLightBrownBlack.setOnClickListener {
            ThemeManager.setTheme(requireContext(), ThemeManager.THEME_LIGHT_BROWN_BLACK)
            view?.let {
                applyTheme(it)
                updateThemeDisplay(it)
            }
            onThemeChangedListener?.invoke()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateThemeDisplay(view: View) {
        val tvThemeValue = view.findViewById<TextView>(R.id.tvThemeValue)
        val currentTheme = ThemeManager.getCurrentTheme(requireContext())
        tvThemeValue.text = if (currentTheme == ThemeManager.THEME_LIGHT_GREEN_GRAY) "浅绿灰" else "浅棕黑"
    }

    private fun updateApiStatus(view: View) {
        val tvApiStatus = view.findViewById<TextView>(R.id.tvApiStatus)
        val apiKey = AISettings.getApiKey(requireContext())
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())

        if (apiKey.isNotBlank()) {
            tvApiStatus.text = "已配置"
            tvApiStatus.setTextColor(if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt())
        } else {
            tvApiStatus.text = "未配置"
            tvApiStatus.setTextColor(0xFFFF5252.toInt())
        }
    }

    private fun showAboutDialog() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_about)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Get views
        val iconContainer = dialog.findViewById<FrameLayout>(R.id.iconContainer)
        val ivIcon = dialog.findViewById<ImageView>(R.id.ivIcon)
        val tvDialogTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvVersion = dialog.findViewById<TextView>(R.id.tvVersion)
        val tvFeaturesTitle = dialog.findViewById<TextView>(R.id.tvFeaturesTitle)
        val ivFeature1 = dialog.findViewById<ImageView>(R.id.ivFeature1)
        val ivFeature2 = dialog.findViewById<ImageView>(R.id.ivFeature2)
        val ivFeature3 = dialog.findViewById<ImageView>(R.id.ivFeature3)
        val ivFeature4 = dialog.findViewById<ImageView>(R.id.ivFeature4)
        val btnOk = dialog.findViewById<TextView>(R.id.btnOk)

        // Apply theme colors
        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF202123.toInt() else 0xFF141413.toInt()

        if (isLightGreenGray) {
            iconContainer.setBackgroundResource(R.drawable.float_bg_light_green_gray)
            ivIcon.setColorFilter(0xFFFFFFFF.toInt())  // White shield icon
            btnOk.setBackgroundResource(R.drawable.bg_button_filled)
        } else {
            iconContainer.setBackgroundResource(R.drawable.float_bg_light_brown_black)
            ivIcon.setColorFilter(0xFFFAF9F5.toInt())  // Light beige shield icon
            btnOk.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)
        }
        tvDialogTitle.setTextColor(textPrimary)
        tvFeaturesTitle.setTextColor(textPrimary)
        ivFeature1.setColorFilter(primaryColor)
        ivFeature2.setColorFilter(primaryColor)
        ivFeature3.setColorFilter(primaryColor)
        ivFeature4.setColorFilter(primaryColor)

        btnOk.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
