package com.justinlin099.xperiarecentsrootless

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.android.material.color.MaterialColors
import kotlin.math.min
import kotlin.math.roundToInt

/** One uniformly scaled display. The native button is measured at real overlay size. */
internal class RecentsPreviewView(context: Context, private val compact: Boolean = false) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val button = ClearButtonView(context).apply { isClickable = false; isFocusable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private var style = ButtonAppearance()
    private val metrics get() = context.getSystemService(WindowManager::class.java).currentWindowMetrics
    private var screen = Rect()
    private var safe = Rect()
    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    init { setWillNotDraw(false); clipChildren = true; addView(button); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun configure(style: ButtonAppearance) {
        this.style = style
        button.configure(style) // Never round fonts, padding and icon geometry at a smaller size.
        requestLayout(); invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val m = metrics
        screen = Rect(0, 0, m.bounds.width().coerceAtLeast(1), m.bounds.height().coerceAtLeast(1))
        val insets = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        safe = Rect(insets.left, insets.top, screen.right - insets.right, screen.bottom - insets.bottom)
        val size = button.preferredSize()
        val edge = ((if (style.position == ButtonPosition.BOTTOM_CENTER) 24 else style.effectiveHorizontalMargin + 12) * density).roundToInt()
        val available = if (style.position == ButtonPosition.BOTTOM_RIGHT) safe.width() - (8*density).roundToInt() else screen.width() - edge
        button.measure(MeasureSpec.makeMeasureSpec(size.first.coerceAtMost(available.coerceAtLeast(1)), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size.second, MeasureSpec.EXACTLY))
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(suggestedMinimumHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        scale = min(width.toFloat()/screen.width(), height.toFloat()/screen.height())
        originX = (width - screen.width()*scale)/2f
        originY = (height - screen.height()*scale)/2f
        val bw = button.measuredWidth; val bh = button.measuredHeight
        val side = (style.effectiveHorizontalMargin*density).roundToInt()
        val vertical = (style.effectiveVerticalMargin*density).roundToInt()
        val point = when (style.position) {
            ButtonPosition.BOTTOM_RIGHT -> {
                val area = Rect(safe).apply { inset((4*density).roundToInt()-button.feedbackInset, (4*density).roundToInt()) }
                val fractions = ManualPlacement.fractions(style, resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                ManualPlacement.pixels(area, bw, bh, fractions.first, fractions.second)
            }
            ButtonPosition.TOP_RIGHT -> android.graphics.Point(safe.right-bw-side+button.feedbackInset, safe.top+vertical)
            ButtonPosition.BOTTOM_CENTER -> android.graphics.Point(safe.centerX()-bw/2, safe.bottom-bh-vertical)
        }
        button.pivotX=0f; button.pivotY=0f; button.scaleX=scale; button.scaleY=scale
        val x=(originX+point.x*scale).roundToInt(); val y=(originY+point.y*scale).roundToInt()
        button.layout(x,y,x+bw,y+bh)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.translate(originX,originY); canvas.scale(scale,scale)
        val w=screen.width().toFloat(); val h=screen.height().toFloat(); val d=density
        val white=style.mode!=ButtonMode.ICON && !style.textBackground && style.plainTextColor==PlainTextColor.WHITE
        fun theme(attr:Int)=MaterialColors.getColor(this,attr)
        val radius=24*d
        brush.style=Paint.Style.FILL
        brush.color=if(white) 0xff211d28.toInt() else theme(com.google.android.material.R.attr.colorSurfaceContainerLow)
        canvas.drawRoundRect(0f,0f,w,h,radius,radius,brush)
        brush.color=if(white) 0xff756c7f.toInt() else theme(com.google.android.material.R.attr.colorOutlineVariant)
        brush.style=Paint.Style.STROKE; brush.strokeWidth=d
        canvas.drawRoundRect(d,d,w-d,h-d,radius,radius,brush)
        brush.style=Paint.Style.FILL
        brush.color=if(white) 0xff39343f.toInt() else theme(com.google.android.material.R.attr.colorSurfaceContainerHighest)
        // Approximate the Xperia card, while preserving the actual display aspect ratio.
        canvas.drawRoundRect(RectF(w*.15f,h*.10f,w*.85f,h*.80f),18*d,18*d,brush)
        brush.color=if(white) 0xff756c7f.toInt() else theme(com.google.android.material.R.attr.colorOutlineVariant)
        for(i in 0..2) canvas.drawRoundRect(w*.27f,h*(.24f+i*.035f),w*(if(i==1) .65f else .57f),h*(.244f+i*.035f),d,d,brush)
        if(!compact) {
            for(x in listOf(.30f,.50f,.70f)) {
                canvas.drawRoundRect(w*x-7*d,h*.84f,w*x+7*d,h*.84f+14*d,3*d,3*d,brush)
                canvas.drawRoundRect(w*x-22*d,h*.88f,w*x+22*d,h*.88f+3*d,d,d,brush)
            }
            canvas.drawRoundRect(w*.38f,h-10*d,w*.62f,h-7*d,2*d,2*d,brush)
        }
        canvas.restore()
    }
}
