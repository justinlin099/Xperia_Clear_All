package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Native Material surface and native layout widgets, shared by the overlay and every preview. */
internal class ClearButtonView(baseContext: Context) : FrameLayout(com.google.android.material.color.DynamicColors.wrapContextIfAvailable(androidx.appcompat.view.ContextThemeWrapper(baseContext, R.style.Theme_XperiaRecents))) {
    private var appearance = ButtonAppearance()
    private var previewScale = 1f
    private val ink get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer)
    private val fill get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
    private val ripple get() = com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorControlHighlight)
    val feedbackInset get() = if (appearance.mode!=ButtonMode.ICON && !appearance.textBackground) dp((8-appearance.effectiveTextPadding).coerceAtLeast(0).toFloat()).roundToInt() else 0
    private var content: View? = null
    private var surface: MaterialButton? = null
    private var label: TextView? = null
    // Sony Launcher 17 OverviewActionButton: DeviceDefault borderless font, weight600.
    // Resolve the font through the same platform widget family, retaining OEM/CJK fallback.
    private data class ActionTextStyle(val typeface: android.graphics.Typeface, val letterSpacing: Float)
    private val overviewActionTextStyle by lazy {
        val platformTheme = android.view.ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        val platformButton = android.widget.Button(platformTheme, null, android.R.attr.borderlessButtonStyle)
        ActionTextStyle(android.graphics.Typeface.create(platformButton.typeface, 600, false), platformButton.letterSpacing)
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density * previewScale
    private fun stacked() = appearance.mode == ButtonMode.TEXT_ICON && appearance.arrangement == IconArrangement.TOP
    init { isClickable = true; isFocusable = true; contentDescription = context.getString(R.string.clear_all); configure(appearance) }
    private fun TextView.configureLabel(color: Int, sizeSp: Float = 15f) {
        text = resources.getString(R.string.clear_all); setTextColor(color)
        textSize = sizeSp * previewScale; isAllCaps = false; maxLines = 1
        gravity = Gravity.CENTER; textAlignment = TEXT_ALIGNMENT_CENTER
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,
            maxOf(1, ((if (sizeSp <= 12f) 9 else 10) * previewScale).roundToInt()), maxOf(2, (sizeSp * previewScale).roundToInt()), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }
    fun configure(value: ButtonAppearance, scale: Float = 1f) {
        appearance = value; previewScale = scale
        removeAllViews(); surface = null; label = null
        foreground=null
        setPadding(feedbackInset,0,feedbackInset,0)
        val child: View
        if (value.mode == ButtonMode.ICON) {
            child = AppCompatImageButton(context).apply {
                setImageResource(R.drawable.ic_clear_all); imageTintList = ColorStateList.valueOf(ink)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val pad = dp(16f).roundToInt(); setPadding(pad, pad, pad, pad)
                fun shape(color: Int) = MaterialShapeCatalog.drawable(value.materialShape, color)
                background = RippleDrawable(ColorStateList.valueOf(ripple), shape(fill), shape(Color.WHITE))
            }
        } else {
            val color = if (value.textBackground) ink else if (value.plainTextColor == PlainTextColor.WHITE) Color.WHITE else com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            val padding = dp(value.effectiveTextPadding.toFloat()).roundToInt()
            val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                configureLabel(color)
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                insetTop = 0; insetBottom = 0; strokeWidth = 0
                cornerRadius = dp(24f).roundToInt()
                backgroundTintList = ColorStateList.valueOf(if (value.textBackground) fill else Color.TRANSPARENT)
                rippleColor = ColorStateList.valueOf(if(value.textBackground) ripple else Color.TRANSPARENT)
                setPaddingRelative(padding, 0, padding, 0)
            }
            surface = button
            if(!value.textBackground) {
                val mask=com.google.android.material.shape.MaterialShapeDrawable(button.shapeAppearanceModel).apply { fillColor=ColorStateList.valueOf(Color.WHITE) }
                foreground=RippleDrawable(ColorStateList.valueOf(ripple),null,mask)
            }
            if (value.mode == ButtonMode.TEXT_ICON) {
                // Keep the MaterialButton surface; arrange real View children with a common
                // center line. Compound drawables use different positioning rules.
                button.text = ""; button.setPadding(0, 0, 0, 0)
                val text = AppCompatTextView(context).apply {
                    configureLabel(color, if (stacked()) 14f else 15f)
                    if (stacked()) {
                        typeface = overviewActionTextStyle.typeface
                        letterSpacing = overviewActionTextStyle.letterSpacing
                    } else typeface = button.typeface
                    includeFontPadding = false
                }
                val icon = AppCompatImageView(context).apply {
                    setImageResource(if (stacked()) R.drawable.ic_clear_all_compact else R.drawable.ic_clear_all); imageTintList = ColorStateList.valueOf(color)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
                label = text
                val group = LinearLayout(context).apply {
                    orientation = if (stacked()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    setPaddingRelative(padding, 0, padding, 0)
                    val imageParams = LinearLayout.LayoutParams(dp(24f).roundToInt(), dp(if (stacked()) 12f else 24f).roundToInt()).apply { gravity = Gravity.CENTER }
                    val textParams = LinearLayout.LayoutParams(if (stacked()) -1 else -2, -2)
                    when (value.arrangement) {
                        IconArrangement.TOP -> { imageParams.bottomMargin = dp(8f).roundToInt(); addView(icon, imageParams); addView(text, textParams) }
                        IconArrangement.START -> { imageParams.marginEnd = dp(8f).roundToInt(); addView(icon, imageParams); addView(text, textParams) }
                    }
                }
                child = FrameLayout(context).apply {
                    addView(button, LayoutParams(-1, -1))
                    addView(group, LayoutParams(-1, -2, Gravity.CENTER))
                }
            } else {
                label = button
                child = button
            }
        }
        child.isClickable = false; child.isFocusable = false
        surface?.apply { isClickable = false; isFocusable = false }
        child.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(child, LayoutParams(-1, -1, Gravity.CENTER)); content = child
        requestLayout()
    }
    fun preferredSize(): Pair<Int, Int> {
        if (appearance.mode == ButtonMode.ICON) return dp(56f).roundToInt() to dp(56f).roundToInt()
        val text = checkNotNull(label)
        val textWidth = ceil(text.paint.measureText(resources.getString(R.string.clear_all)))
        val bodyWidth = when {
            stacked() -> maxOf(textWidth, dp(24f))
            appearance.mode == ButtonMode.TEXT_ICON -> textWidth + dp(32f)
            else -> textWidth
        }
        val width = maxOf(dp(48f), bodyWidth + dp(2f * appearance.effectiveTextPadding))
        val height = if (stacked()) maxOf(dp(64f), text.paint.fontSpacing + dp(44f)) else dp(48f)
        return ceil(width).toInt()+2*feedbackInset to ceil(height).toInt()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = preferredSize()
        val width = resolveSize(size.first, widthMeasureSpec)
        if (appearance.mode == ButtonMode.TEXT_ICON) label?.maxWidth = (width - 2*feedbackInset - dp(2f * appearance.effectiveTextPadding) - if (stacked()) 0f else dp(32f)).roundToInt().coerceAtLeast(1)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(resolveSize(size.second, heightMeasureSpec), MeasureSpec.EXACTLY))
    }
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        content?.isPressed = isPressed; content?.isEnabled = isEnabled
        surface?.isPressed = isPressed; surface?.isEnabled = isEnabled
    }
    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
}
