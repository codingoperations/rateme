package io.feeba.ui

import android.annotation.TargetApi
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.Window
import io.feeba.Feeba
import java.lang.ref.WeakReference

internal object ViewUtils {
    private val MARGIN_ERROR_PX_SIZE = dpToPx(24)

    /**
     * Check if the keyboard is currently being shown.
     * Does not work for cases when keyboard is full screen.
     */
    fun isKeyboardUp(activityWeakReference: WeakReference<Activity?>): Boolean {
        val metrics = DisplayMetrics()
        val visibleBounds = Rect()
        var view: View? = null
        var isOpen = false
        if (activityWeakReference.get() != null) {
            val window = activityWeakReference.get()!!.window
            view = window.decorView
            view.getWindowVisibleDisplayFrame(visibleBounds)
            window.windowManager.defaultDisplay.getMetrics(metrics)
        }
        if (view != null) {
            val heightDiff = metrics.heightPixels - visibleBounds.bottom
            isOpen = heightDiff > MARGIN_ERROR_PX_SIZE
        }
        return isOpen
    }

    // Ensures the root decor view is ready by checking the following;
    //   1. Is fully attach to the root window and insets are available
    //   2. Ensure if any Activities are changed while waiting we use the updated one
    fun decorViewReady(activity: Activity, runnable: Runnable) {
        val listenerKey = "decorViewReady:$runnable"
        activity.window.decorView.post {
            if (isActivityFullyReady(activity)) runnable.run()
        }
    }

    private fun getWindowVisibleDisplayFrame(activity: Activity): Rect {
        val rect = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(rect)
        return rect
    }

    fun getCutoutAndStatusBarInsets(activity: Activity): IntArray {
        val frame = getWindowVisibleDisplayFrame(activity)
        val contentView = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT)
        var rightInset = 0f
        var leftInset = 0f
        val topInset = (frame.top - contentView.top) / Resources.getSystem().displayMetrics.density
        val bottomInset = (contentView.bottom - frame.bottom) / Resources.getSystem().displayMetrics.density
        // API 29 is the only version where the IAM bleeds under cutouts in immersize mode
        // All other versions will not need left and right insets.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val cutout = activity.windowManager.defaultDisplay.cutout
            if (cutout != null) {
                rightInset = cutout.safeInsetRight / Resources.getSystem().displayMetrics.density
                leftInset = cutout.safeInsetLeft / Resources.getSystem().displayMetrics.density
            }
        }
        return intArrayOf(Math.round(topInset), Math.round(bottomInset), Math.round(rightInset), Math.round(leftInset))
    }

    fun getFullbleedWindowWidth(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = activity.window.decorView
            decorView.width
        } else {
            getWindowWidth(activity)
        }
    }

    fun getWindowWidth(activity: Activity): Int {
        return getWindowVisibleDisplayFrame(activity).width()
    }

    // Due to differences in accounting for keyboard, navigation bar, and status bar between
    //   Android versions have different implementation here
    fun getWindowHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindowHeightAPI23Plus(activity) else getWindowHeightLollipop(
            activity
        )
    }

    // Requirement: Ensure DecorView is ready by using ViewUtils.decorViewReady
    @TargetApi(Build.VERSION_CODES.M)
    private fun getWindowHeightAPI23Plus(activity: Activity): Int {
        val decorView = activity.window.decorView
        // Use use stable heights as SystemWindowInset subtracts the keyboard
        val windowInsets = decorView.rootWindowInsets ?: return decorView.height
        return decorView.height -
                windowInsets.stableInsetBottom -
                windowInsets.stableInsetTop
    }

    private fun getWindowHeightLollipop(activity: Activity): Int {
        // getDisplaySizeY - works correctly expect for landscape due to a bug.
        return if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) getWindowVisibleDisplayFrame(activity).height() else getDisplaySizeY(
            activity
        )
        //  getWindowVisibleDisplayFrame - Doesn't work for portrait as it subtracts the keyboard height.
    }

    private fun getDisplaySizeY(activity: Activity): Int {
        val point = Point()
        activity.windowManager.defaultDisplay.getSize(point)
        return point.y
    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    // Ensures the Activity is fully ready by;
    //   1. Ensure it is attached to a top-level Window by checking if it has an IBinder
    //   2. If Android M or higher ensure WindowInsets exists on the root window also
    fun isActivityFullyReady(activity: Activity): Boolean {
        val hasToken = activity.window.decorView.applicationWindowToken != null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return hasToken
        val decorView = activity.window.decorView
        val insetsAttached = decorView.rootWindowInsets != null
        return hasToken && insetsAttached
    }
}