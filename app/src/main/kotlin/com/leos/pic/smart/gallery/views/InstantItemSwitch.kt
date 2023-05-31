package ca.on.sudbury.hojat.smartgallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import ca.on.sudbury.hojat.smartgallery.helpers.CLICK_MAX_DISTANCE
import ca.on.sudbury.hojat.smartgallery.helpers.CLICK_MAX_DURATION
import ca.on.sudbury.hojat.smartgallery.helpers.DRAG_THRESHOLD
import kotlin.math.abs

// handle only one finger clicks, pass other events to the parent view and ignore it when received again
class InstantItemSwitch(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {
    private var mTouchDownTime = 0L
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var passTouches = false
    private var dragThreshold = DRAG_THRESHOLD * context.resources.displayMetrics.density

    var parentView: ViewGroup? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (passTouches) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                passTouches = false
            }
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (passTouches) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x
                mTouchDownY = event.y
                mTouchDownTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val diffX = mTouchDownX - event.x
                val diffY = mTouchDownY - event.y
                if (abs(diffX) < CLICK_MAX_DISTANCE && abs(diffY) < CLICK_MAX_DISTANCE && System.currentTimeMillis() - mTouchDownTime < CLICK_MAX_DURATION) {
                    performClick()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (passTouches) {
                    return false
                }

                val diffX = mTouchDownX - event.x
                val diffY = mTouchDownY - event.y
                if (abs(diffX) > dragThreshold || abs(diffY) > dragThreshold) {
                    if (!passTouches) {
                        event.action = MotionEvent.ACTION_DOWN
                        event.setLocation(event.rawX, event.y)
                        parentView?.dispatchTouchEvent(event)
                    }
                    passTouches = true
                    parentView?.dispatchTouchEvent(event)
                    return false
                }
            }
        }
        return true
    }
}
