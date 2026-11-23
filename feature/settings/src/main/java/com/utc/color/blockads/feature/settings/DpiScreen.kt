package com.utc.color.blockads.feature.settings

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import com.utc.color.blockads.core.common.DpiMode
import com.utc.color.blockads.core.common.DpiSettings
import com.utc.color.blockads.core.common.label
import com.utc.color.blockads.core.common.userMessage
import com.utc.color.blockads.core.data.SettingsRepository
import com.utc.color.blockads.core.designsystem.PreferenceIcon
import com.utc.color.blockads.core.designsystem.Screen
import com.utc.color.blockads.core.designsystem.choiceSheet
import com.utc.color.blockads.core.designsystem.inflater
import com.utc.color.blockads.core.designsystem.showMessage
import com.utc.color.blockads.feature.settings.databinding.ScreenDpiBinding
import com.utc.color.blockads.core.common.R as Strings

/**
 * Goodbye DPI options, kept short on purpose: on/off, Standard or Strong, the decoy TTL for
 * Strong, and an optional site list. The engine supports every GoodbyeDPI switch, but the
 * presets are all most people need.
 */
class DpiScreen private constructor(
    activity: ComponentActivity,
    private val views: ScreenDpiBinding,
    state: Bundle,
    private val settings: SettingsRepository,
    private val onEnable: () -> Unit,
) : Screen(activity, views.root) {
    constructor(
        activity: ComponentActivity, parent: ViewGroup, state: Bundle, settings: SettingsRepository, onEnable: () -> Unit,
    ) : this(activity, ScreenDpiBinding.inflate(parent.inflater, parent, false), state, settings, onEnable)

    private var dpi = DpiSettings()
    private var loaded = false
    private var dragging = false
    private val draftSites = state.getString("sites")

    init {
        views.sites.root.hint = context.getString(Strings.string.dpi_sites)
        views.sites.input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
        views.sites.input.minLines = 3
        views.saveSites.setOnClickListener {
            val text = views.sites.input.text.toString()
            perform(Strings.string.dpi_sites_saved) {
                val sites = DpiSettings.parseSites(text)
                settings.updateDpi { it.copy(blacklist = sites) }
            }
        }
        views.fakeTtlSlider.setLabelFormatter { it.toInt().toString() }
        views.fakeTtlSlider.addOnChangeListener { _, value, _ -> renderTtl(value.toInt()) }
        views.fakeTtlSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                dragging = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                dragging = false
                val ttl = slider.value.toInt()
                perform { settings.updateDpi { it.copy(fakeTtl = ttl) } }
            }
        })
        observe(settings.dpi) { render(it) }
    }

    private fun render(value: DpiSettings) {
        if (!loaded) views.sites.input.setText(draftSites ?: value.blacklist.joinToString("\n"))
        else if (value.blacklist != dpi.blacklist) views.sites.input.setText(value.blacklist.joinToString("\n"))
        loaded = true
        dpi = value

        views.dpiEnabled.root.bind(context.getString(Strings.string.home_dpi_bypass),
            context.getString(Strings.string.home_dpi_description), checked = value.enabled) {
            val enable = !value.enabled
            perform {
                settings.updateDpi { it.copy(enabled = enable) }
                // Only after saving: turning protection on re-inflates the shell and closes this screen.
                if (enable) onEnable()
            }
        }
        val mode = value.mode
        val modes = listOf(DpiMode.Basic, DpiMode.Strong)
        val title = context.getString(Strings.string.dpi_mode)
        views.dpiMode.root.bind(title, context.getString(mode.label), trailingIcon = PreferenceIcon.Dropdown) {
            show(choiceSheet(context, title, modes.map { context.getString(it.label) }, modes.indexOf(mode),
                { context.getString(modes[it].description) }) { index ->
                perform { settings.updateDpi(modes[index]::apply) }
            })
        }
        views.fakeTtl.isVisible = value.fake
        if (!dragging) {
            views.fakeTtlSlider.value = value.fakeTtl.toFloat()
            renderTtl(value.fakeTtl)
        }
    }

    private fun renderTtl(ttl: Int) {
        views.fakeTtlLabel.text = context.getString(Strings.string.dpi_fake_ttl_value, ttl)
    }

    private fun perform(message: Int? = null, block: suspend () -> Unit) {
        action({ views.feedback.showMessage(message?.let(context::getString)) }, {
            views.feedback.showMessage(it.userMessage(resources) ?: context.getString(Strings.string.settings_action_failed))
        }, block)
    }

    override fun saveState() = super.saveState().apply {
        val text = views.sites.input.text.toString()
        putString("sites", text.takeIf { loaded && it != dpi.blacklist.joinToString("\n") })
    }
}

private val DpiMode.description: Int
    get() = when (this) {
        DpiMode.Strong -> Strings.string.dpi_mode_strong_hint
        else -> Strings.string.dpi_mode_basic_hint
    }
