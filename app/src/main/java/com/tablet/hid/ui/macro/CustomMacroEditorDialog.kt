package com.tablet.hid.ui.macro

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tablet.hid.R
import com.tablet.hid.model.KeyUsageConstants
import com.tablet.hid.model.KeyboardMacroButtonConfig

class CustomMacroEditorDialog : DialogFragment() {

    fun interface OnMacroCreated {
        fun onMacroCreated(macro: KeyboardMacroButtonConfig)
    }

    var onMacroCreated: OnMacroCreated? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        fun dp(value: Int) = (value * dp).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(4))
        }

        val labelHint = TextView(ctx).apply {
            text = getString(R.string.macro_editor_label_hint)
            textSize = 12f
        }
        root.addView(labelHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        val labelEdit = EditText(ctx).apply {
            hint = getString(R.string.macro_editor_label_hint)
            setSingleLine()
        }
        root.addView(labelEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        val modifierHeader = TextView(ctx).apply {
            text = getString(R.string.macro_editor_modifiers)
            textSize = 12f
        }
        root.addView(modifierHeader, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        val modRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnCtrl  = modToggle(ctx, "Ctrl")
        val btnShift = modToggle(ctx, "Shift")
        val btnAlt   = modToggle(ctx, "Alt")
        val btnGui   = modToggle(ctx, "Win/Cmd")
        val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        }
        modRow.addView(btnCtrl,  btnLp)
        modRow.addView(btnShift, btnLp)
        modRow.addView(btnAlt,   btnLp)
        modRow.addView(btnGui,   LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(modRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        val keyHeader = TextView(ctx).apply {
            text = getString(R.string.macro_editor_key)
            textSize = 12f
        }
        root.addView(keyHeader, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        val keyNames = KeyUsageConstants.COMMON_KEYS.map { it.first }
        val keySpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, keyNames)
        }
        root.addView(keySpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.macro_editor_title)
            .setView(root)
            .setPositiveButton(R.string.macro_editor_add, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val label = labelEdit.text.toString().trim()
                        if (label.isEmpty()) {
                            labelEdit.error = getString(R.string.macro_editor_label_hint)
                            Toast.makeText(ctx, "Enter a label", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        var mods = 0
                        if (btnCtrl.isChecked)  mods = mods or KeyUsageConstants.MOD_LEFT_CTRL
                        if (btnShift.isChecked) mods = mods or KeyUsageConstants.MOD_LEFT_SHIFT
                        if (btnAlt.isChecked)   mods = mods or KeyUsageConstants.MOD_LEFT_ALT
                        if (btnGui.isChecked)   mods = mods or KeyUsageConstants.MOD_LEFT_GUI
                        val selectedPos = keySpinner.selectedItemPosition.coerceAtLeast(0)
                        val keyUsage = KeyUsageConstants.COMMON_KEYS[selectedPos].second
                        onMacroCreated?.onMacroCreated(
                            KeyboardMacroButtonConfig(label, mods, listOf(keyUsage))
                        )
                        dismiss()
                    }
                }
            }
    }

    private fun modToggle(ctx: Context, text: String) = ToggleButton(ctx).apply {
        textOn  = text
        textOff = text
        this.text = text
        isChecked = false
        textSize = 11f
        gravity = Gravity.CENTER
    }
}
