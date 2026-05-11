package com.tablet.hid.ui.touchmouse

import android.graphics.PointF
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentTouchMouseBinding
import com.tablet.hid.model.KeyboardMacroPresets
import com.tablet.hid.model.MouseButton
import com.tablet.hid.model.TouchMouseSubRegionConfig
import com.tablet.hid.model.ZoneType

class ZoneEditController(
    private val binding: FragmentTouchMouseBinding,
    private val viewModel: HidViewModel,
) {

    private var editMode = EditModeZone.NONE
    private var pendingSubRegionKeyboardModifiers = 0

    private enum class EditModeZone {
        NONE, LEFT_ZONE, RIGHT_ZONE, LEFT_SUB_REGION, RIGHT_SUB_REGION, SNIPER
    }

    fun isActive(): Boolean = editMode != EditModeZone.NONE

    fun startZoneEdit(isLeft: Boolean) {
        editMode = if (isLeft) EditModeZone.LEFT_ZONE else EditModeZone.RIGHT_ZONE
        binding.zoneEditOverlay.isVisible = true
        binding.labelZoneEditHint.setText(
            if (isLeft) R.string.zone_edit_hint_left else R.string.zone_edit_hint_right
        )
        binding.touchZoneOverlay.editingLeft = isLeft
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    fun startSubRegionEdit(isLeft: Boolean, keyboardModifiers: Int) {
        editMode = if (isLeft) EditModeZone.LEFT_SUB_REGION else EditModeZone.RIGHT_SUB_REGION
        pendingSubRegionKeyboardModifiers = keyboardModifiers
        binding.zoneEditOverlay.isVisible = true
        val action = if (keyboardModifiers == KeyboardMacroPresets.MOD_LEFT_CONTROL) {
            "hold Ctrl"
        } else {
            "send middle-click"
        }
        binding.labelZoneEditHint.text = if (isLeft) {
            "Drag a Left-button sub-region. It will $action."
        } else {
            "Drag a Right-button sub-region. It will $action."
        }
        binding.touchZoneOverlay.editingLeft = isLeft
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    fun startSniperEdit() {
        editMode = EditModeZone.SNIPER
        binding.zoneEditOverlay.isVisible = true
        binding.labelZoneEditHint.text = "Drag to set the Sniper Zone — hold it while moving to slow the cursor."
        binding.touchZoneOverlay.editingLeft = true
        binding.touchZoneOverlay.editingSniper = true
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    fun cancelZoneEdit() {
        editMode = EditModeZone.NONE
        pendingSubRegionKeyboardModifiers = 0
        binding.zoneEditOverlay.isVisible = false
        binding.touchZoneOverlay.editingLeft = null
        binding.touchZoneOverlay.editingSniper = false
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    fun handleTouch(event: MotionEvent): Boolean {
        val overlay = binding.touchZoneOverlay
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                overlay.editDragStart = PointF(event.x, event.y)
                overlay.editDragEnd   = PointF(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                overlay.editDragEnd = PointF(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                confirmZoneEdit(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> cancelZoneEdit()
        }
        return true
    }

    private fun confirmZoneEdit(endX: Float, endY: Float) {
        val start = binding.touchZoneOverlay.editDragStart ?: return cancelZoneEdit()
        val w = binding.touchZoneOverlay.width.toFloat().takeIf { it > 0 } ?: return cancelZoneEdit()
        val h = binding.touchZoneOverlay.height.toFloat().takeIf { it > 0 } ?: return cancelZoneEdit()

        val left   = minOf(start.x, endX) / w
        val top    = minOf(start.y, endY) / h
        val right  = maxOf(start.x, endX) / w
        val bottom = maxOf(start.y, endY) / h

        if (right - left < 0.05f || bottom - top < 0.05f) return cancelZoneEdit()

        val prev = viewModel.touchMouseConfig.value
        val newConfig = when (editMode) {
            EditModeZone.LEFT_ZONE -> prev.copy(leftButton = prev.leftButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
            EditModeZone.RIGHT_ZONE -> prev.copy(rightButton = prev.rightButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
            EditModeZone.LEFT_SUB_REGION -> {
                val subRegion = newSubRegion(left, top, right, bottom)
                prev.copy(leftButton = prev.leftButton.copy(
                    enabled = true,
                    subRegions = prev.leftButton.subRegions + subRegion,
                ))
            }
            EditModeZone.RIGHT_SUB_REGION -> {
                val subRegion = newSubRegion(left, top, right, bottom)
                prev.copy(rightButton = prev.rightButton.copy(
                    enabled = true,
                    subRegions = prev.rightButton.subRegions + subRegion,
                ))
            }
            EditModeZone.SNIPER -> prev.copy(
                sniperLeft = left, sniperTop = top, sniperRight = right, sniperBottom = bottom
            )
            EditModeZone.NONE -> prev
        }
        viewModel.updateTouchMouseConfig(newConfig)
        cancelZoneEdit()
    }

    private fun newSubRegion(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = TouchMouseSubRegionConfig(
        enabled = true,
        zoneType = ZoneType.STATIC,
        staticLeft = left,
        staticTop = top,
        staticRight = right,
        staticBottom = bottom,
        keyboardModifiers = pendingSubRegionKeyboardModifiers,
        alternateMouseButton = if (pendingSubRegionKeyboardModifiers == 0) MouseButton.MIDDLE else null,
    )
}
