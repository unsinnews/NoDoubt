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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yy.perfectfloatwindow.BuildConfig
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
        setupAutoDeleteSwitch(view)
        applyTheme(view)
        updateApiStatus(view)
        updateThemeDisplay(view)
        updateVersionDisplay(view)
        updateProfileDisplay(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            applyTheme(it)
            updateApiStatus(it)
            updateThemeDisplay(it)
            updateVersionDisplay(it)
            updateProfileDisplay(it)
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
        val ivAutoDeleteIcon = view.findViewById<ImageView>(R.id.ivAutoDeleteIcon)
        val editBadgeContainer = view.findViewById<FrameLayout>(R.id.editBadgeContainer)

        // Auto delete switch items
        val tvAutoDeleteTitle = view.findViewById<TextView>(R.id.tvAutoDeleteTitle)
        val tvAutoDeleteDesc = view.findViewById<TextView>(R.id.tvAutoDeleteDesc)
        val switchAutoDelete = view.findViewById<Switch>(R.id.switchAutoDelete)

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
            editBadgeContainer.setBackgroundResource(R.drawable.bg_edit_badge)

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
            ivAutoDeleteIcon.setColorFilter(textPrimary)

            // Auto delete switch
            tvAutoDeleteTitle.setTextColor(textPrimary)
            tvAutoDeleteDesc.setTextColor(textSecondary)
            switchAutoDelete.thumbTintList = ColorStateList.valueOf(primaryColor)
            switchAutoDelete.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0x8010A37F.toInt(), 0xFFE0E0E0.toInt())
            )

            // SeekBar theming
            seekBarSize.progressTintList = ColorStateList.valueOf(primaryColor)
            seekBarSize.thumbTintList = ColorStateList.valueOf(primaryColor)

        } else {
            // 浅棕黑主题 - 暖橙色按钮，黑色文字
            scrollView.setBackgroundColor(0xFFFAF9F5.toInt())
            rootLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            headerLayout.setBackgroundColor(0xFFFAF9F5.toInt())
            avatarContainer.setBackgroundResource(R.drawable.float_bg_light_brown_black)
            ivAvatar.setColorFilter(0xFFFAF9F5.toInt())  // Light beige shield icon
            tvTitle.setTextColor(0xFF141413.toInt())
            editBadgeContainer.setBackgroundResource(R.drawable.bg_edit_badge_light_brown_black)

            val accentColor = 0xFFDA7A5A.toInt()  // Warm orange accent
            val textPrimary = 0xFF141413.toInt()  // 黑色用于文字
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
            ivAutoDeleteIcon.setColorFilter(textPrimary)

            // Auto delete switch
            tvAutoDeleteTitle.setTextColor(textPrimary)
            tvAutoDeleteDesc.setTextColor(textSecondary)
            switchAutoDelete.thumbTintList = ColorStateList.valueOf(accentColor)
            switchAutoDelete.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0x80DA7A5A.toInt(), 0xFFE0E0E0.toInt())
            )

            // SeekBar theming
            seekBarSize.progressTintList = ColorStateList.valueOf(accentColor)
            seekBarSize.thumbTintList = ColorStateList.valueOf(accentColor)
        }
    }

    private fun setupMenuItems(view: View) {
        // Profile edit (avatar and nickname)
        view.findViewById<View>(R.id.avatarClickArea).setOnClickListener {
            showEditProfileDialog()
        }
        view.findViewById<View>(R.id.tvTitle).setOnClickListener {
            showEditProfileDialog()
        }

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

    private fun setupAutoDeleteSwitch(view: View) {
        val switchAutoDelete = view.findViewById<Switch>(R.id.switchAutoDelete)

        // Load current setting (default: true - auto delete enabled)
        switchAutoDelete.isChecked = AISettings.isAutoDeleteScreenshot(requireContext())

        switchAutoDelete.setOnCheckedChangeListener { _, isChecked ->
            AISettings.setAutoDeleteScreenshot(requireContext(), isChecked)
        }
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

    private fun updateVersionDisplay(view: View) {
        val versionName = BuildConfig.VERSION_NAME
        view.findViewById<TextView>(R.id.tvVersion)?.text = "v$versionName"
    }

    private fun showAboutDialog() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_about)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Get views
        val headerContainer = dialog.findViewById<LinearLayout>(R.id.headerContainer)
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
            // Header with green background, white text
            headerContainer.setBackgroundResource(R.drawable.bg_about_header_light_green_gray)
            iconContainer.setBackgroundResource(R.drawable.float_bg_about_icon_light_green_gray)
            ivIcon.setColorFilter(0xFF10A37F.toInt())  // Green icon on white background
            btnOk.setBackgroundResource(R.drawable.bg_button_filled)
        } else {
            // Header with warm orange background (#DA7A5A), white text
            headerContainer.setBackgroundResource(R.drawable.bg_about_header_light_brown_black)
            iconContainer.setBackgroundResource(R.drawable.float_bg_about_icon_light_brown_black)
            ivIcon.setColorFilter(0xFF141413.toInt())  // Black icon on light background
            btnOk.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)
        }
        // Title and version are always white on the header background
        tvDialogTitle.setTextColor(0xFFFFFFFF.toInt())
        tvVersion.text = "版本 ${BuildConfig.VERSION_NAME}"
        tvVersion.setTextColor(0xFFFFFFFF.toInt())
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

    private fun showEditProfileDialog() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(requireContext())
        var selectedAvatarIndex = ThemeManager.getAvatarIndex(requireContext())

        val dialog = Dialog(requireContext(), R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Get views
        val titleContainer = dialog.findViewById<LinearLayout>(R.id.titleContainer)
        val tvDialogTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val avatarCard = dialog.findViewById<LinearLayout>(R.id.avatarCard)
        val tvAvatarLabel = dialog.findViewById<TextView>(R.id.tvAvatarLabel)
        val nicknameCard = dialog.findViewById<LinearLayout>(R.id.nicknameCard)
        val tvNicknameLabel = dialog.findViewById<TextView>(R.id.tvNicknameLabel)
        val etNickname = dialog.findViewById<EditText>(R.id.etNickname)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)

        // Avatar options
        val avatarOptions = listOf(
            dialog.findViewById<FrameLayout>(R.id.avatarOption0),
            dialog.findViewById<FrameLayout>(R.id.avatarOption1),
            dialog.findViewById<FrameLayout>(R.id.avatarOption2),
            dialog.findViewById<FrameLayout>(R.id.avatarOption3)
        )
        val avatarIcons = listOf(
            dialog.findViewById<ImageView>(R.id.ivAvatar0),
            dialog.findViewById<ImageView>(R.id.ivAvatar1),
            dialog.findViewById<ImageView>(R.id.ivAvatar2),
            dialog.findViewById<ImageView>(R.id.ivAvatar3)
        )

        // Apply theme colors
        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF202123.toInt() else 0xFF141413.toInt()

        if (isLightGreenGray) {
            titleContainer.setBackgroundResource(R.drawable.bg_dialog_title)
            avatarCard.setBackgroundResource(R.drawable.bg_card_settings)
            nicknameCard.setBackgroundResource(R.drawable.bg_card_settings)
            etNickname.setBackgroundResource(R.drawable.bg_edittext_settings)
            btnSave.setBackgroundResource(R.drawable.bg_button_filled)
        } else {
            titleContainer.setBackgroundResource(R.drawable.bg_dialog_title_light_brown_black)
            avatarCard.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            nicknameCard.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            etNickname.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            btnSave.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)
        }

        // Title is always white on colored background
        tvDialogTitle.setTextColor(0xFFFFFFFF.toInt())
        // Labels are primary color on card background
        tvAvatarLabel.setTextColor(textPrimary)
        tvNicknameLabel.setTextColor(textPrimary)
        etNickname.setTextColor(textPrimary)

        // Load current values
        etNickname.setText(ThemeManager.getNickname(requireContext()))

        // Function to update avatar selection UI
        fun updateAvatarSelection(index: Int) {
            selectedAvatarIndex = index
            avatarOptions.forEachIndexed { i, option ->
                if (i == index) {
                    option.setBackgroundResource(
                        if (isLightGreenGray) R.drawable.bg_avatar_option_selected_light_green_gray
                        else R.drawable.bg_avatar_option_selected_light_brown_black
                    )
                    avatarIcons[i].setColorFilter(0xFFFFFFFF.toInt())
                } else {
                    option.setBackgroundResource(R.drawable.bg_avatar_option_unselected)
                    avatarIcons[i].setColorFilter(primaryColor)
                }
            }
        }

        // Initialize avatar selection
        updateAvatarSelection(selectedAvatarIndex)

        // Avatar click listeners
        avatarOptions.forEachIndexed { index, option ->
            option.setOnClickListener {
                updateAvatarSelection(index)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            if (nickname.isNotEmpty()) {
                ThemeManager.setNickname(requireContext(), nickname)
            }
            ThemeManager.setAvatarIndex(requireContext(), selectedAvatarIndex)
            updateProfileDisplay(view!!)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateProfileDisplay(view: View) {
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val ivAvatar = view.findViewById<ImageView>(R.id.ivAvatar)

        // Update nickname
        tvTitle.text = ThemeManager.getNickname(requireContext())

        // Update avatar icon
        val avatarDrawable = when (ThemeManager.getAvatarIndex(requireContext())) {
            ThemeManager.AVATAR_LIGHTBULB -> R.drawable.ic_float_ai
            ThemeManager.AVATAR_BRAIN -> R.drawable.ic_avatar_brain
            ThemeManager.AVATAR_STAR -> R.drawable.ic_avatar_star
            ThemeManager.AVATAR_ROCKET -> R.drawable.ic_avatar_rocket
            else -> R.drawable.ic_float_ai
        }
        ivAvatar.setImageResource(avatarDrawable)
    }
}
