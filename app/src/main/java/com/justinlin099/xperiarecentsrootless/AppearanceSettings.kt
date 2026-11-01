package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

internal class AppearanceSettings(context: Context, showPreview: Boolean = true) : LinearLayout(context), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = context.getSharedPreferences("rootless", 0)
    private val updates = mutableListOf<(ButtonAppearance) -> Unit>()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private val preview = if (showPreview) RecentsPreviewView(context) else null
    init {
        orientation = VERTICAL
        if (preview != null) {
            heading(R.string.preview)
            addView(preview, LayoutParams(-1, dp(340)))
        }
        heading(R.string.display_mode)
        val modes = row()
        for (mode in ButtonMode.entries) {
            val sample = ClearButtonView(context).apply { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
            val holder = FrameLayout(context).apply { addView(sample, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
            option(modes, when (mode) { ButtonMode.TEXT -> R.string.mode_text; ButtonMode.ICON -> R.string.mode_icon; ButtonMode.TEXT_ICON -> R.string.mode_text_icon }, holder, 58,
                { it.mode == mode }) { prefs.edit().putString("buttonMode", mode.name).apply() }
            updates.add { sample.configure(it.copy(mode = mode), .44f) }
        }
        val backgroundHeading = heading(R.string.text_background)
        val backgrounds = row()
        for (enabled in listOf(true, false)) {
            val sample = ClearButtonView(context).apply { isClickable = false; isFocusable = false }
            val holder = FrameLayout(context).apply { addView(sample, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
            option(backgrounds, if (enabled) R.string.background_on else R.string.background_off, holder, 58,
                { it.mode != ButtonMode.ICON && it.textBackground == enabled }) {
                prefs.edit().putString("buttonMode", if (ButtonAppearance.read(prefs).mode == ButtonMode.TEXT_ICON) "TEXT_ICON" else "TEXT").putBoolean("textBackground", enabled).apply()
            }
            updates.add { sample.configure(it.copy(mode = if (it.mode == ButtonMode.ICON) ButtonMode.TEXT else it.mode, textBackground = enabled), .65f) }
        }
        updates.add { val v=if(it.mode!=ButtonMode.ICON) VISIBLE else GONE; backgroundHeading.visibility=v; backgrounds.visibility=v }
        val colorOptions = LinearLayout(context).apply { orientation = VERTICAL }
        addView(colorOptions, LayoutParams(-1,-2))
        colorOptions.addView(text(R.string.plain_text_color,15f).apply { setPadding(0,dp(18),0,dp(10)) })
        val colors = LinearLayout(context).apply { orientation = HORIZONTAL }
        colorOptions.addView(colors, LayoutParams(-1,-2))
        for (choice in PlainTextColor.entries) {
            val sample = ClearButtonView(context).apply { isClickable=false; isFocusable=false }
            val holder = FrameLayout(context).apply {
                tag = "plain-color-preview"
                background = android.graphics.drawable.GradientDrawable().apply { setColor(0xff28242e.toInt()); cornerRadius=dp(10).toFloat() }
                addView(sample,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
            }
            option(colors,if(choice==PlainTextColor.WHITE) R.string.color_white else R.string.color_theme,holder,58,
                { it.plainTextColor==choice }) { prefs.edit().putString("plainTextColor",choice.name).apply() }
            updates.add { sample.configure(it.copy(mode=if(it.mode==ButtonMode.ICON) ButtonMode.TEXT else it.mode,textBackground=false,plainTextColor=choice),.65f) }
        }
        updates.add { colorOptions.visibility=if(!it.textBackground && it.mode!=ButtonMode.ICON) VISIBLE else GONE }
        textPaddingSlider()
        val arrangementHeading = heading(R.string.icon_arrangement)
        val arrangementRows=mutableListOf<View>()
        for (pair in IconArrangement.entries.chunked(2)) {
            val arrangements = row()
            arrangementRows.add(arrangements)
            for (arrangement in pair) {
                val sample = ClearButtonView(context).apply { isClickable = false; isFocusable = false }
                val holder = FrameLayout(context).apply { addView(sample, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
                val name = when(arrangement) {
                    IconArrangement.START -> R.string.icon_before
                    IconArrangement.TOP -> R.string.icon_above
                }
                option(arrangements, name, holder, 68, { it.mode == ButtonMode.TEXT_ICON && it.arrangement == arrangement }) {
                    prefs.edit().putString("buttonMode", "TEXT_ICON").putString("iconArrangement", arrangement.name).apply()
                }
                updates.add { sample.configure(it.copy(mode = ButtonMode.TEXT_ICON, arrangement = arrangement), .65f) }
            }
        }
        updates.add { val v=if(it.mode==ButtonMode.TEXT_ICON) VISIBLE else GONE; arrangementHeading.visibility=v; arrangementRows.forEach { row -> row.visibility=v } }
        val shapeHeading = heading(R.string.icon_shape)
        val shapeSample = ClearButtonView(context).apply { isClickable = false; isFocusable = false }
        val shapeName = TextView(context).apply { textSize = 15f; setTextColor(color(com.google.android.material.R.attr.colorOnSurface)) }
        val shapeContent = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(shapeSample, LayoutParams(dp(56), dp(56)))
            addView(shapeName, LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            addView(android.widget.ImageView(context).apply { setImageResource(android.R.drawable.arrow_down_float); imageTintList = android.content.res.ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurface)) }, LayoutParams(dp(24), dp(24)))
        }
        val shapeCard = MaterialCardView(context).apply {
            radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = color(com.google.android.material.R.attr.colorOutlineVariant)
            isClickable = true; isFocusable = true; contentDescription = context.getString(R.string.shape_material)
            addView(shapeContent); setOnClickListener { showMaterialShapes() }
        }
        addView(shapeCard,LayoutParams(-1,-2))
        updates.add { val v=if(it.mode==ButtonMode.ICON) VISIBLE else GONE; shapeHeading.visibility=v; shapeCard.visibility=v }
        updates.add { shapeSample.configure(it.copy(mode = ButtonMode.ICON), .85f); shapeName.text = MaterialShapeCatalog.label(context, it.materialShape) }
        heading(R.string.placement)
        val positions = row()
        for (position in ButtonPosition.entries) {
            val sample = RecentsPreviewView(context, true)
            val name = when (position) { ButtonPosition.BOTTOM_CENTER -> R.string.position_bottom_center; ButtonPosition.TOP_RIGHT -> R.string.position_top_right; ButtonPosition.BOTTOM_RIGHT -> R.string.position_bottom_right }
            option(positions, name, sample, 96, { it.position == position }) {
                prefs.edit().putString("buttonPosition", position.name).apply()
            }
            updates.add { sample.configure(it.copy(position = position)) }
        }
        val manualControls = LinearLayout(context).apply { orientation = VERTICAL }
        addView(manualControls, LayoutParams(-1, -2))
        manualControls.addView(text(R.string.drag_hint, 13f).apply { setPadding(0, dp(16), 0, dp(8)) })
        val editPosition = com.google.android.material.button.MaterialButton(context).apply {
            setOnClickListener { if (PositionEditing.active(prefs)) PositionEditing.finish(prefs) else PositionEditing.begin(prefs) }
        }
        manualControls.addView(editPosition, LayoutParams(-1, -2))
        updates.add { editPosition.setText(if (PositionEditing.active(prefs)) R.string.finish_position else R.string.edit_position) }
        manualSlider(manualControls, true)
        manualSlider(manualControls, false)
        manualControls.addView(com.google.android.material.button.MaterialButton(context).apply {
            setText(R.string.reset_position)
            setOnClickListener { val editor = prefs.edit(); ManualPlacement.keysFor(ButtonAppearance.read(prefs)).forEach { editor.remove(it) }; editor.apply() }
        }, LayoutParams(-1, -2))
        updates.add { manualControls.visibility = if (it.position == ButtonPosition.BOTTOM_RIGHT) VISIBLE else GONE }
        marginSlider(R.string.vertical_margin, true, 12f, 180f)
        marginSlider(R.string.horizontal_margin, false, 12f, 72f)
        refresh()
    }
    private fun showMaterialShapes() {
        val grid = android.widget.GridLayout(context).apply { columnCount = 3; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val scroll = android.widget.ScrollView(context).apply { addView(grid) }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shape_material).setView(scroll).setNegativeButton(android.R.string.cancel, null).create()
        val style = ButtonAppearance.read(prefs)
        for (name in MaterialShapeCatalog.names) {
            val tile = LinearLayout(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(10), dp(4), dp(10))
                isClickable = true; isFocusable = true; contentDescription = MaterialShapeCatalog.label(context, name)
                background = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let { a -> a.getDrawable(0).also { a.recycle() } }
                addView(ClearButtonView(context).apply { configure(style.copy(mode = ButtonMode.ICON, materialShape = name), .85f); isClickable = false; isFocusable = false }, LayoutParams(dp(56), dp(56)))
                addView(TextView(context).apply { text = MaterialShapeCatalog.label(context, name); textSize = 11f; gravity = Gravity.CENTER; setTextColor(color(com.google.android.material.R.attr.colorOnSurface)) }, LayoutParams(-1, dp(36)))
                isSelected = style.materialShape == name
                setOnClickListener { prefs.edit().putString("materialShape", name).putString("buttonMode", "ICON").apply(); dialog.dismiss() }
            }
            val card = MaterialCardView(context).apply {
                radius = dp(12).toFloat(); cardElevation = 0f
                strokeWidth = dp(if (style.materialShape == name) 2 else 0)
                strokeColor = color(androidx.appcompat.R.attr.colorPrimary)
                addView(tile)
            }
            grid.addView(card, android.widget.GridLayout.LayoutParams().apply { width = 0; height = dp(112); columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f) })
        }
        dialog.show()
    }
    private fun text(res: Int, size: Float) = TextView(context).apply {
        setText(res); textSize = size; setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }
    private fun heading(res: Int): TextView {
        val view=text(res,15f).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
            setPadding(0,dp(18),0,dp(10))
        }
        addView(view)
        return view
    }
    private fun row() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        this@AppearanceSettings.addView(this, LayoutParams(-1, -2))
    }
    private fun option(row: LinearLayout, res: Int, image: View, imageHeight: Int,
                       selected: (ButtonAppearance) -> Boolean, click: () -> Unit) {
        val label = text(res, 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(6), dp(10), dp(6), dp(10))
            addView(image, LayoutParams(-1, dp(imageHeight)))
            addView(label, LayoutParams(-1, -2))
        }
        val tile = MaterialCardView(context).apply {
            radius = dp(16).toFloat(); cardElevation = 0f; isClickable = true; isFocusable = true
            contentDescription = context.getString(res)
            addView(content)
            setOnClickListener { click() }
        }
        row.addView(tile, LayoutParams(0, dp(imageHeight + 64), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        updates.add {
            val dark = image.tag == "plain-color-preview" || (!it.textBackground && it.plainTextColor == PlainTextColor.WHITE)
            image.background = if (dark) android.graphics.drawable.GradientDrawable().apply { setColor(0xff28242e.toInt()); cornerRadius=dp(10).toFloat() } else null
            val checked = selected(it)
            tile.isSelected = checked
            tile.strokeWidth = dp(if (checked) 2 else 1)
            tile.strokeColor = color(if (checked) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant)
            tile.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
    }
    private fun defaultCaption(owner: LinearLayout = this, id: String, restore: () -> Unit): TextView {
        val caption=TextView(context).apply {
            tag="slider-default";textSize=12f
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val reset=com.google.android.material.button.MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            tag="restore-$id";setText(R.string.restore_default);isAllCaps=false;textSize=12f
            backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            strokeWidth=0;insetTop=0;insetBottom=0;minWidth=0;minimumWidth=0
            minHeight=dp(48);minimumHeight=dp(48);maxWidth=dp(200);maxLines=2
            setPaddingRelative(dp(12),dp(4),dp(12),dp(4))
            setOnClickListener { restore() }
        }
        val row=LinearLayout(context).apply {
            orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            addView(caption,LayoutParams(0,-2,1f));addView(reset,LayoutParams(-2,-2))
        }
        owner.addView(row,LayoutParams(-1,-2))
        return caption
    }
    private fun defaultState(caption: TextView, visibility: Int, enabled: Boolean) {
        val row=caption.parent as LinearLayout
        row.visibility=visibility
        row.getChildAt(1).isEnabled=enabled
    }
    private fun setDefault(caption: TextView, value: Int, unit: String) {
        val number=java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(value)
        caption.text=context.getString(R.string.default_value,if(unit=="%") "$number%" else "$number $unit")
    }
    private fun textPaddingSlider() {
        val caption = text(R.string.text_padding, 14f).apply { setPadding(0, dp(18), 0, dp(4)) }
        addView(caption)
        val slider = Slider(context).apply { valueFrom = 0f; valueTo = 48f; stepSize = 1f; isTickVisible = false; contentDescription = context.getString(R.string.text_padding_label) }
        addView(slider, LayoutParams(-1, -2))
        val defaults = defaultCaption(id="padding") {
            val style=ButtonAppearance.read(prefs)
            val value=ButtonAppearance(textBackground=style.textBackground).effectiveTextPadding
            prefs.edit().putInt(if(style.textBackground) "filledTextPadding" else "plainTextPadding",value).apply()
        }
        updates.add {
            val preset=ButtonAppearance(textBackground=it.textBackground).effectiveTextPadding
            setDefault(defaults,preset,"dp")
            val visible=if(it.mode!=ButtonMode.ICON) VISIBLE else GONE
            caption.visibility=visible; slider.visibility=visible
            defaultState(defaults,visible,it.effectiveTextPadding!=preset)
            caption.text = context.getString(R.string.text_padding, it.effectiveTextPadding)
            slider.value = it.effectiveTextPadding.toFloat()
            slider.isEnabled = it.mode != ButtonMode.ICON
            caption.alpha = if (slider.isEnabled) 1f else .5f
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val key = if (ButtonAppearance.read(prefs).textBackground) "filledTextPadding" else "plainTextPadding"
                prefs.edit().putInt(key, value.roundToInt()).apply()
            }
        }
    }
    private fun manualSlider(parent: LinearLayout, horizontal: Boolean) {
        val caption = TextView(context).apply { textSize = 14f; setPadding(0, dp(12), 0, dp(4)) }
        parent.addView(caption)
        val slider = Slider(context).apply { valueFrom = 0f; valueTo = 100f; stepSize = .1f; isTickVisible = false }
        parent.addView(slider, LayoutParams(-1, -2))
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val defaults = defaultCaption(parent,if(horizontal) "manual-x" else "manual-y") {
            val style=ButtonAppearance.read(prefs)
            val preset=ManualPlacement.fractions(ButtonAppearance(mode=style.mode,arrangement=style.arrangement),landscape)
            prefs.edit().putInt(ManualPlacement.key(style,landscape,horizontal),if(horizontal) preset.first else preset.second).apply()
        }
        updates.add {
            val base=ButtonAppearance(mode=it.mode,arrangement=it.arrangement)
            val preset=ManualPlacement.fractions(base,landscape)
            setDefault(defaults,(if(horizontal) preset.first else preset.second)/10,"%")
            val pair = ManualPlacement.fractions(it, landscape)
            val current=if(horizontal) pair.first else pair.second
            val baseline=if(horizontal) preset.first else preset.second
            defaultState(defaults,VISIBLE,current!=baseline)
            val n = current / 10f
            slider.value = n
            caption.text = context.getString(if (horizontal) R.string.position_x else R.string.position_y, n.roundToInt())
        }
        slider.addOnChangeListener { _, value, fromUser -> if (fromUser) prefs.edit().putInt(ManualPlacement.key(ButtonAppearance.read(prefs), landscape, horizontal), (value * 10).roundToInt()).apply() }
    }
    private fun marginSlider(res: Int, vertical: Boolean, min: Float, max: Float) {
        val caption = TextView(context).apply { textSize = 14f; setPadding(0, dp(20), 0, dp(6)); setTextColor(color(com.google.android.material.R.attr.colorOnSurface)) }
        addView(caption)
        val slider = Slider(context).apply { valueFrom = 0f; valueTo = max; stepSize = 1f; isTickVisible = false }
        addView(slider, LayoutParams(-1, -2))
        val defaults = defaultCaption(id="margin-${if(vertical) "vertical" else "side"}") {
            val style=ButtonAppearance.read(prefs)
            val base=ButtonAppearance(mode=style.mode,arrangement=style.arrangement,position=style.position)
            if(!vertical && style.position!=ButtonPosition.TOP_RIGHT) return@defaultCaption
            val target=if(style.position==ButtonPosition.TOP_RIGHT) ManualPlacement.topKey(style,!vertical) else ManualPlacement.bottomKey(style)
            prefs.edit().putInt(target,if(vertical) base.effectiveVerticalMargin else base.effectiveHorizontalMargin).apply()
        }
        updates.add {
            val base=ButtonAppearance(mode=it.mode,arrangement=it.arrangement,position=it.position)
            setDefault(defaults,if(vertical) base.effectiveVerticalMargin else base.effectiveHorizontalMargin,"dp")
            val manual = it.position == ButtonPosition.BOTTOM_RIGHT
            val enabled = !manual && (vertical || it.position == ButtonPosition.TOP_RIGHT)
            caption.visibility = if (manual) GONE else VISIBLE; slider.visibility = caption.visibility
            slider.isEnabled = enabled; caption.alpha = if (enabled) 1f else .5f
            val n = if (vertical) it.effectiveVerticalMargin else it.effectiveHorizontalMargin
            val baseline=if(vertical) base.effectiveVerticalMargin else base.effectiveHorizontalMargin
            defaultState(defaults,caption.visibility,enabled && n!=baseline)
            defaults.alpha=caption.alpha
            slider.value = n.toFloat(); caption.text = context.getString(res, n)
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val style=ButtonAppearance.read(prefs)
                val top=style.position==ButtonPosition.TOP_RIGHT
                if(!vertical && !top) return@addOnChangeListener
                prefs.edit().putInt(if(top) ManualPlacement.topKey(style,!vertical) else ManualPlacement.bottomKey(style),
                    value.roundToInt().coerceAtLeast(if (top) 0 else min.toInt())).apply()
            }
        }
    }
    private fun refresh() { val value = ButtonAppearance.read(prefs); updates.forEach { it(value) }; preview?.configure(value) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); prefs.registerOnSharedPreferenceChangeListener(this); refresh() }
    override fun onDetachedFromWindow() { prefs.unregisterOnSharedPreferenceChangeListener(this); super.onDetachedFromWindow() }
    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) { if (key in ButtonAppearance.keys || key == PositionEditing.KEY) refresh() }
}
