package com.justinlin099.xperiarecentsrootless

import android.content.SharedPreferences
import android.view.Gravity

internal enum class ButtonMode { TEXT, ICON, TEXT_ICON }
internal enum class IconArrangement { START, TOP }
internal enum class PlainTextColor { THEME, WHITE }
internal enum class ButtonPosition { BOTTOM_CENTER, TOP_RIGHT, BOTTOM_RIGHT }
internal data class ButtonAppearance(
    val mode: ButtonMode = ButtonMode.TEXT_ICON,
    val arrangement: IconArrangement = IconArrangement.TOP,
    val position: ButtonPosition = ButtonPosition.BOTTOM_CENTER,
    val verticalMargin: Int = ManualPlacement.defaultBottom(mode, arrangement),
    val textBackground: Boolean = false,
    val materialShape: String = "CIRCLE",
    val manualX: Int = ManualPlacement.defaultX(mode, arrangement),
    val manualY: Int = 790,
    val manualLandscapeX: Int = ManualPlacement.defaultX(mode, arrangement),
    val manualLandscapeY: Int = 790,
    val topMargin: Int = 0,
    val topSideMargin: Int = 23,
    val filledTextPadding: Int = 16,
    val plainTextPadding: Int = 0,
    val plainTextColor: PlainTextColor = PlainTextColor.WHITE
) {
    val effectiveTextPadding get() = if (textBackground) filledTextPadding else plainTextPadding
    val effectiveVerticalMargin get() = if (position == ButtonPosition.TOP_RIGHT) topMargin else verticalMargin
    val effectiveHorizontalMargin get() = if (position == ButtonPosition.TOP_RIGHT) topSideMargin else 12
    val gravity: Int get() = when (position) {
        ButtonPosition.BOTTOM_CENTER -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ButtonPosition.TOP_RIGHT -> Gravity.TOP or Gravity.RIGHT
        ButtonPosition.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.RIGHT
    }
    companion object {
        val keys = setOf("buttonMode", "buttonPosition", "textBackground", "materialShape", "iconArrangement", "filledTextPadding", "plainTextPadding", "plainTextColor") + ManualPlacement.keys
        fun read(p: SharedPreferences): ButtonAppearance {
            val defaults=ButtonAppearance()
            val mode = runCatching { ButtonMode.valueOf(p.getString("buttonMode", defaults.mode.name)!!) }.getOrDefault(defaults.mode)
            val arrangement = runCatching { IconArrangement.valueOf(p.getString("iconArrangement",defaults.arrangement.name)!!) }.getOrDefault(defaults.arrangement)
            val base = ButtonAppearance(mode = mode, arrangement = arrangement)
            val profile = ManualPlacement.profile(base)
            return base.copy(
                position = runCatching { ButtonPosition.valueOf(p.getString("buttonPosition", "BOTTOM_CENTER")!!) }.getOrDefault(ButtonPosition.BOTTOM_CENTER),
                verticalMargin = p.getInt(ManualPlacement.bottomKey(profile),base.verticalMargin).coerceIn(0,180),
                textBackground = p.getBoolean("textBackground",defaults.textBackground),
                materialShape = p.getString("materialShape","CIRCLE").orEmpty().takeIf { it in MaterialShapeCatalog.names } ?: "CIRCLE",
                manualX = p.getInt(ManualPlacement.key(profile,false,true),base.manualX).coerceIn(0,1000),
                manualY = p.getInt(ManualPlacement.key(profile,false,false),790).coerceIn(0,1000),
                manualLandscapeX = p.getInt(ManualPlacement.key(profile,true,true),base.manualLandscapeX).coerceIn(0,1000),
                manualLandscapeY = p.getInt(ManualPlacement.key(profile,true,false),790).coerceIn(0,1000),
                topMargin = p.getInt(ManualPlacement.topKey(profile,false),0).coerceIn(0,180), topSideMargin = p.getInt(ManualPlacement.topKey(profile,true),23).coerceIn(0,72),
                filledTextPadding = p.getInt("filledTextPadding",16).coerceIn(0,48), plainTextPadding = p.getInt("plainTextPadding",0).coerceIn(0,48),
                plainTextColor = runCatching { PlainTextColor.valueOf(p.getString("plainTextColor",defaults.plainTextColor.name)!!) }.getOrDefault(defaults.plainTextColor)
            )
        }
    }
}
