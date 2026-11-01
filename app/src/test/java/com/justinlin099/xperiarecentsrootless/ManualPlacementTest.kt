package com.justinlin099.xperiarecentsrootless

import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManualPlacementTest {
    @Test fun customTouchPassesFingerPositionToNativeRipple() {
        var hotspot:Pair<Float,Float>?=null
        val view=object:View(RuntimeEnvironment.getApplication()) {
            override fun drawableHotspotChanged(x:Float,y:Float) { super.drawableHotspotChanged(x,y);hotspot=x to y }
        }
        val touch=DragTouchListener(view,{Point()}, {_,_->}, {},allowDrag=false)
        val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,17f,23f,0)
        touch.onTouch(view,down);down.recycle()
        assertEquals(17f to 23f,hotspot)
        val move=MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,18f,24f,0)
        touch.onTouch(view,move);move.recycle()
        assertEquals(18f to 24f,hotspot)
    }
    @Test fun positionProfilesHaveIndependentDefaults() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("position-defaults-test", 0)
        prefs.edit().putString("buttonMode","TEXT").putInt(ManualPlacement.bottomKey("TEXT"),67).putString("buttonPosition", "TOP_RIGHT").apply()
        val style = ButtonAppearance.read(prefs)
        assertEquals(0, style.effectiveVerticalMargin); assertEquals(23, style.effectiveHorizontalMargin)
        assertEquals(67, style.copy(position = ButtonPosition.BOTTOM_CENTER).effectiveVerticalMargin)
        assertEquals(760 to 790, ManualPlacement.fractions(style, false))
        prefs.edit().putInt(ManualPlacement.key(style,false,true),123).putInt(ManualPlacement.key(style,false,false),987).putInt(ManualPlacement.key(style,true,true),432).putInt(ManualPlacement.key(style,true,false),678).apply()
        assertEquals(123 to 987, ManualPlacement.fractions(ButtonAppearance.read(prefs), false))
        assertEquals(432 to 678, ManualPlacement.fractions(ButtonAppearance.read(prefs), true))
    }
    @Test fun savedPositionSurvivesScreenResizeAndClampsToSystemBarSafeArea() {
        val area = Rect(20, 50, 1020, 2050)
        val point = ManualPlacement.pixels(area, 100, 200, 850, 780)
        assertEquals(850 to 780, ManualPlacement.normalized(area, 100, 200, point.x, point.y))
        assertEquals(Point(20, 1850), ManualPlacement.clamp(area, 100, 200, -999, 9999))
        assertEquals(Point(920, 50), ManualPlacement.clamp(area, 100, 200, 9999, -999))
        assertEquals(Point(10, 10), ManualPlacement.pixels(Rect(10, 10, 20, 20), 100, 100, 1000, 1000))
    }
    private class Harness(allowClick: Boolean = true, allowDrag: Boolean = true) {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context).apply { isEnabled = true; isClickable = true }
        var clicks = 0; var commits = 0; var point = Point(100, 200)
        val listener = DragTouchListener(view, { Point(point) }, { x, y -> point = Point(x,y) }, { commits++ }, allowClick = allowClick, allowDrag = allowDrag)
        init { view.setOnClickListener { clicks++ } }
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 20, action, x, y, 0)
            listener.onTouch(view, event); event.recycle()
        }
    }
    @Test fun dragReturningToStartNeverClearsAndReleaseSavesOnce() {
        val h = Harness()
        h.send(MotionEvent.ACTION_DOWN, 110f, 210f)
        h.send(MotionEvent.ACTION_MOVE, 250f, 350f)
        assertEquals(Point(240,340), h.point)
        h.send(MotionEvent.ACTION_MOVE, 110f, 210f)
        h.send(MotionEvent.ACTION_UP, 110f, 210f)
        assertEquals(0,h.clicks); assertEquals(1,h.commits)
    }
    @Test fun smallTapJitterClicksNormallyButCancellationAndSecondFingerNeverClickOrSave() {
        val h = Harness()
        h.send(MotionEvent.ACTION_DOWN, 110f, 210f); h.send(MotionEvent.ACTION_UP, 111f, 211f)
        assertEquals(1,h.clicks); assertEquals(0,h.commits)
        h.send(MotionEvent.ACTION_DOWN, 110f, 210f); h.send(MotionEvent.ACTION_MOVE, 250f, 350f)
        h.send(MotionEvent.ACTION_CANCEL, 250f, 350f)
        assertEquals(Point(100,200),h.point)
        h.send(MotionEvent.ACTION_DOWN, 110f, 210f); h.send(MotionEvent.ACTION_POINTER_DOWN, 110f, 210f)
        h.send(MotionEvent.ACTION_UP, 110f, 210f)
        assertEquals(1,h.clicks); assertEquals(0,h.commits)
    }
    @Test fun disablingDuringGestureCancelsWithoutClearing() {
        val h = Harness()
        h.send(MotionEvent.ACTION_DOWN, 110f, 210f); h.send(MotionEvent.ACTION_MOVE, 250f, 350f)
        h.view.isEnabled = false; h.send(MotionEvent.ACTION_UP, 250f, 350f)
        assertEquals(Point(100,200),h.point); assertEquals(0,h.clicks); assertEquals(0,h.commits)
    }
    @Test fun lockedModeCannotMoveAndRejectsSwipesEvenWhenTheyReturnToStart() {
        val h=Harness(allowClick=true,allowDrag=false)
        h.send(MotionEvent.ACTION_DOWN,110f,210f);h.send(MotionEvent.ACTION_MOVE,250f,350f)
        h.send(MotionEvent.ACTION_MOVE,110f,210f);h.send(MotionEvent.ACTION_UP,110f,210f)
        assertEquals(Point(100,200),h.point);assertEquals(0,h.commits);assertEquals(0,h.clicks)
        h.send(MotionEvent.ACTION_DOWN,110f,210f);h.send(MotionEvent.ACTION_UP,110f,210f)
        assertEquals(1,h.clicks)
    }
    @Test fun adjustmentModeTapNeverClearsButDragCanSave() {
        val h=Harness(allowClick=false,allowDrag=true)
        h.send(MotionEvent.ACTION_DOWN,110f,210f);h.send(MotionEvent.ACTION_UP,110f,210f)
        assertEquals(0,h.clicks);assertEquals(0,h.commits)
        h.send(MotionEvent.ACTION_DOWN,110f,210f);h.send(MotionEvent.ACTION_MOVE,250f,350f);h.send(MotionEvent.ACTION_UP,250f,350f)
        assertEquals(0,h.clicks);assertEquals(1,h.commits)
    }
    @Test fun adjustmentSessionIsExplicitAndExpiresAutomatically() {
        val p=RuntimeEnvironment.getApplication().getSharedPreferences("edit-session-test",0)
        assertFalse(PositionEditing.active(p));PositionEditing.begin(p);assertTrue(PositionEditing.active(p))
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(61))
        assertFalse(PositionEditing.active(p));PositionEditing.begin(p);assertTrue(PositionEditing.active(p))
        PositionEditing.finish(p);assertFalse(PositionEditing.active(p))
    }

}
