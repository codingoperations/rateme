package io.feeba.survey

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.webkit.WebView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import androidx.cardview.widget.CardView
import androidx.core.widget.PopupWindowCompat
import io.feeba.Feeba
import io.feeba.Utils
import io.feeba.data.Position
import io.feeba.data.Position.BOTTOM_BANNER
import io.feeba.data.Position.CENTER_MODAL
import io.feeba.data.Position.FULL_SCREEN
import io.feeba.data.Position.TOP_BANNER
import io.feeba.data.SurveyPresentation
import io.feeba.lifecycle.LogLevel
import io.feeba.lifecycle.Logger
import io.feeba.lifecycle.WebViewManager
import io.feeba.ui.AnimationUtils
import io.feeba.ui.DraggableRelativeLayout
import io.feeba.ui.DraggableRelativeLayout.Companion.DRAGGABLE_DIRECTION_DOWN
import io.feeba.ui.DraggableRelativeLayout.Companion.DRAGGABLE_DIRECTION_UP
import io.feeba.ui.FeebaBounceInterpolator
import io.feeba.ui.ViewUtils
import io.least.ui.dpToPx

/**
 * Layout Documentation
 * ### Modals & Banners ###
 * - WebView
 * - width  = MATCH_PARENT
 * - height = PX height provided via a JS event for the content
 * - Parent Layouts
 * - width  = MATCH_PARENT
 * - height = WRAP_CONTENT - Since the WebView is providing the height.
 * ### Fullscreen ###
 * - WebView
 * - width  = MATCH_PARENT
 * - height = MATCH_PARENT
 * - Parent Layouts
 * - width  = MATCH_PARENT
 * - height = MATCH_PARENT
 */
private const val IN_APP_MESSAGE_CARD_VIEW_TAG = "IN_APP_MESSAGE_CARD_VIEW_TAG"
private val ACTIVITY_BACKGROUND_COLOR_EMPTY = Color.parseColor("#00000000")
private val ACTIVITY_BACKGROUND_COLOR_FULL = Color.parseColor("#BB000000")
private const val IN_APP_BANNER_ANIMATION_DURATION_MS = 1000
private const val IN_APP_CENTER_ANIMATION_DURATION_MS = 1000
private const val IN_APP_BACKGROUND_ANIMATION_DURATION_MS = 400
private const val ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600
private const val ACTIVITY_INIT_DELAY = 200

internal class SurveyViewController(private val webView: WebView, content: SurveyPresentation, private val disableDragDismiss: Boolean) {
    private var popupWindow: PopupWindow? = null

    internal interface SurveyViewLifecycleListener {
        fun onSurveyWasShown()
        fun onSurveyWillDismiss()
        fun onSurveyWasDismissed()
    }

    private val handler = Handler()
    private val pageWidth: Int
    private var pageHeight: Int
    private var marginPxSizeLeft: Int = dpToPx(24f)
    private var marginPxSizeRight: Int = dpToPx(24f)
    private var marginPxSizeTop: Int = dpToPx(24f)
    private var marginPxSizeBottom: Int = dpToPx(24f)
    private val displayDuration: Double
    private val hasBackground: Boolean
    private var shouldDismissWhenActive = false
    private val DRAG_THRESHOLD_PX_SIZE: Int = dpToPx(4f)

    /**
     * Simple getter to know when the MessageView is in a dragging state
     */
    var isDragging = false
        private set
    private val messageContent: SurveyPresentation
    private val displayLocation: Position
    private var parentRelativeLayout: RelativeLayout? = null
    private var draggableRelativeLayout: DraggableRelativeLayout? = null
    private var messageController: SurveyViewLifecycleListener? = null
    private var scheduleDismissRunnable: Runnable? = null

    init {
        displayLocation = content.displayLocation
        pageHeight = content.pageHeight
        pageWidth = ViewGroup.LayoutParams.MATCH_PARENT
        displayDuration = content.displayDuration
        hasBackground = !displayLocation.isBanner()
        webView.setBackgroundColor(Color.TRANSPARENT)
        messageContent = content
        setMarginsFromContent(content)
    }

    /**
     * For now we only support default margin or no margin.
     * Any non-zero value will be treated as default margin
     * @param content in app message content and style
     */
    private fun setMarginsFromContent(content: SurveyPresentation) {
        marginPxSizeTop = if (content.useHeightMargin) dpToPx(24f) else 0
        marginPxSizeBottom = if (content.useHeightMargin) dpToPx(24f) else 0
        marginPxSizeLeft = if (content.useWidthMargin) dpToPx(24f) else 0
        marginPxSizeRight = if (content.useWidthMargin) dpToPx(24f) else 0
    }

    fun setMessageController(messageController: SurveyViewLifecycleListener?) {
        this.messageController = messageController
    }

    fun showView(activity: Activity) {
        delayShowUntilAvailable(activity)
    }

    fun checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            shouldDismissWhenActive = false
            finishAfterDelay(null)
        }
    }

    /**
     * This will fired when the device is rotated for example with a new provided height for the WebView
     * Called to shrink or grow the WebView when it receives a JS resize event with a new height.
     *
     * @param pageHeight the provided height
     */
    fun updateHeight(pageHeight: Int, currentActivity: Activity) {
//        this.pageHeight = pageHeight
//        Utils.runOnMainUIThread(Runnable {
//            val layoutParams = webView.layoutParams
//            if (layoutParams == null) {
//                Logger.log(
//                    LogLevel.WARN,
//                    "WebView height update skipped because of null layoutParams, new height will be used once it is displayed."
//                )
//                return@Runnable
//            }
//            layoutParams.height = pageHeight
//            // We only need to update the WebView size since it's parent layouts are set to
//            //   WRAP_CONTENT to always match the height of the WebView. (Expect for fullscreen)
//            webView.layoutParams = layoutParams
//
//            // draggableRelativeLayout comes in null here sometimes, this is due to the IAM
//            //  not being ready to be shown yet
//            // When preparing the IAM, the correct height will be set and handle this job, so
//            //  all bases are covered and the draggableRelativeLayout will never have the wrong height
//            if (draggableRelativeLayout != null) draggableRelativeLayout?.setParams(
//                createDraggableLayoutParams(
//                    pageHeight,
//                    displayLocation,
//                    disableDragDismiss,
//                    ViewUtils.getWindowHeight(currentActivity)
//                )
//            )
//        })
    }

    fun showSurvey(currentActivity: Activity) {
        /* IMPORTANT
         * The only place where currentActivity should be assigned to InAppMessageView */
        val webViewLayoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            pageHeight
        )
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        val relativeLayoutParams = if (hasBackground) createParentRelativeLayoutParams() else null
        showDraggableView(
            displayLocation,
            webViewLayoutParams,
            relativeLayoutParams,
            createDraggableLayoutParams(pageHeight, displayLocation, disableDragDismiss, ViewUtils.getWindowHeight(currentActivity)),
            currentActivity
        )
    }

    private fun createParentRelativeLayoutParams(): LayoutParams {
        val relativeLayoutParams = LayoutParams(pageWidth, LayoutParams.MATCH_PARENT)
        when (displayLocation) {
            TOP_BANNER -> {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            }

            BOTTOM_BANNER -> {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            }

            CENTER_MODAL, FULL_SCREEN -> relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        }
        return relativeLayoutParams
    }

    private fun createDraggableLayoutParams(pageHeight: Int, displayLocation: Position, disableDragging: Boolean, windowHeight: Int): DraggableRelativeLayout.Params {
        var pageHeight = pageHeight
        val draggableParams: DraggableRelativeLayout.Params = DraggableRelativeLayout.Params()
        draggableParams.maxXPos = marginPxSizeRight
        draggableParams.maxYPos = marginPxSizeTop
        draggableParams.draggingDisabled = disableDragging
        draggableParams.messageHeight = pageHeight
        draggableParams.height = windowHeight
        when (displayLocation) {
            TOP_BANNER -> draggableParams.dragThresholdY = marginPxSizeTop - DRAG_THRESHOLD_PX_SIZE
            Position.BOTTOM_BANNER -> {
                draggableParams.posY = windowHeight - pageHeight
                draggableParams.dragThresholdY = marginPxSizeBottom + DRAG_THRESHOLD_PX_SIZE
            }

            Position.FULL_SCREEN -> {
                run {
                    pageHeight = windowHeight - (marginPxSizeBottom + marginPxSizeTop)
                    draggableParams.messageHeight = pageHeight
                }
                val y = windowHeight / 2 - pageHeight / 2
                draggableParams.dragThresholdY = y + DRAG_THRESHOLD_PX_SIZE
                draggableParams.maxYPos = y
                draggableParams.posY = y
            }

            Position.CENTER_MODAL -> {
                val y = windowHeight / 2 - pageHeight / 2
                draggableParams.dragThresholdY = y + DRAG_THRESHOLD_PX_SIZE
                draggableParams.maxYPos = y
                draggableParams.posY = y
            }
        }
        draggableParams.dragDirection =
            if (displayLocation === TOP_BANNER) DRAGGABLE_DIRECTION_UP else DRAGGABLE_DIRECTION_DOWN
        return draggableParams
    }

    private fun showDraggableView(
        displayLocation: Position,
        relativeLayoutParams: LayoutParams,
        draggableRelativeLayoutParams: LayoutParams?,
        webViewLayoutParams: DraggableRelativeLayout.Params,
        currentActivity: Activity
    ) {
        Utils.runOnMainUIThread(Runnable {
//            if (webView == null) return@Runnable
            webView.layoutParams = relativeLayoutParams
            val context = Feeba.appContext
            setUpDraggableLayout(context, draggableRelativeLayoutParams, webViewLayoutParams)
            setUpParentRelativeLayout(context)
            parentRelativeLayout?.let { createPopupWindow(it, currentActivity) }
            if (messageController != null) {
                val localDraggableRef = draggableRelativeLayout
                val localParentRef = parentRelativeLayout
                if (localDraggableRef != null && localParentRef != null) {
                    animateSurvey(displayLocation, localDraggableRef, localParentRef)
                }
            }
            startDismissTimerIfNeeded(currentActivity)
        })
    }

    /**
     * Create a new Android PopupWindow that draws over the current Activity
     *
     * @param parentRelativeLayout root layout to attach to the pop up window
     */
    private fun createPopupWindow(parentRelativeLayout: RelativeLayout, currentActivity: Activity) {
        val popupWindow = PopupWindow(
            parentRelativeLayout,
            if (hasBackground) WindowManager.LayoutParams.MATCH_PARENT else pageWidth,
            if (hasBackground) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        this.popupWindow = popupWindow
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isTouchable = true
        // NOTE: This is required for getting fullscreen under notches working in portrait mode
        popupWindow.isClippingEnabled = false
        var gravity = 0
        if (!hasBackground) {
            when (displayLocation) {
                TOP_BANNER -> gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                BOTTOM_BANNER -> gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                CENTER_MODAL, FULL_SCREEN -> gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // Using panel for fullbleed IAMs and dialog for non-fullbleed. The attached dialog type
        // does not allow content to bleed under notches but panel does.
        val displayType = if (messageContent.isFullBleed) WindowManager.LayoutParams.TYPE_APPLICATION_PANEL else WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        PopupWindowCompat.setWindowLayoutType(
            popupWindow,
            displayType
        )
        popupWindow.showAtLocation(
            currentActivity.window.decorView.rootView,
            gravity,
            0,
            0
        )
    }

    private fun setUpParentRelativeLayout(context: Context) {
        val parentRelativeLayout = RelativeLayout(context).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clipChildren = false
            clipToPadding = false
            addView(draggableRelativeLayout)
        }
        this.parentRelativeLayout = parentRelativeLayout
    }

    private fun setUpDraggableLayout(
        context: Context,
        relativeLayoutParams: LayoutParams?,
        draggableParams: DraggableRelativeLayout.Params
    ) {
        draggableRelativeLayout = DraggableRelativeLayout(context).apply {
            setParams(draggableParams)
            setListener(object : DraggableRelativeLayout.DraggableListener {
                override fun onDismiss() {
                    if (messageController != null) {
                        messageController!!.onSurveyWillDismiss()
                    }
                    finishAfterDelay(null)
                }

                override fun onDragStart() {
                    isDragging = true
                }

                override fun onDragEnd() {
                    isDragging = false
                }
            })


            setPadding(marginPxSizeLeft, marginPxSizeTop, marginPxSizeRight, marginPxSizeBottom)
            clipChildren = false
            clipToPadding = false

            val cardView: CardView = createCardView(context)
            cardView.tag = IN_APP_MESSAGE_CARD_VIEW_TAG
            cardView.addView(webView)
            addView(cardView)
        }
        if (relativeLayoutParams != null) draggableRelativeLayout?.setLayoutParams(relativeLayoutParams)

        if (webView.parent != null) (webView.parent as ViewGroup).removeAllViews()

    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     */
    private fun createCardView(context: Context): CardView {
        val cardView = CardView(context)
        val height = if (displayLocation === Position.FULL_SCREEN) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val cardViewLayoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
        cardViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        cardView.layoutParams = cardViewLayoutParams

        // Set the initial elevation of the CardView to 0dp if using Android 6 API 23
        //  Fixes bug when animating a elevated CardView class
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) cardView.cardElevation = 0f else cardView.cardElevation = dpToPx(5f).toFloat()
        cardView.radius = dpToPx(8f).toFloat()
        cardView.clipChildren = false
        cardView.clipToPadding = false
        cardView.preventCornerOverlap = false
        cardView.setCardBackgroundColor(Color.TRANSPARENT)
        return cardView
    }

    /**
     * Schedule dismiss behavior, if IAM has a dismiss after X number of seconds timer.
     */
    private fun startDismissTimerIfNeeded(currentActivity: Activity) {
        if (displayDuration <= 0) return

        if (scheduleDismissRunnable == null) {
            val scheduleDismissRunnable = Runnable {
                messageController?.onSurveyWillDismiss()
                if (currentActivity != null) {
                    dismissAndAwaitNextMessage(null)
                    scheduleDismissRunnable = null
                } else {
                    // For cases when the app is on background and the dismiss is triggered
                    shouldDismissWhenActive = true
                }
            }
            this.scheduleDismissRunnable = scheduleDismissRunnable
            handler.postDelayed(scheduleDismissRunnable, displayDuration.toLong() * 1000)
        }
    }

    // Do not add view until activity is ready
    private fun delayShowUntilAvailable(currentActivity: Activity) {
        if (ViewUtils.isActivityFullyReady(currentActivity) && parentRelativeLayout == null) {
            showSurvey(currentActivity)
            return
        }
        Handler().postDelayed({ delayShowUntilAvailable(currentActivity) }, ACTIVITY_INIT_DELAY.toLong())
    }

    /**
     * Trigger the [.draggableRelativeLayout] dismiss animation
     */
    fun dismissAndAwaitNextMessage(callback: WebViewManager.OneSignalGenericCallback?) {
        if (draggableRelativeLayout == null) {
            Logger.log(LogLevel.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already", Throwable())
            dereferenceViews()
            callback?.onComplete()
            return
        }
        draggableRelativeLayout?.dismiss()
        finishAfterDelay(callback)
    }

    /**
     * Finishing on a timer as continueSettling does not return false
     * when using smoothSlideViewTo on Android 4.4
     */
    private fun finishAfterDelay(callback: WebViewManager.OneSignalGenericCallback?) {
        Utils.runOnMainThreadDelayed(Runnable {
            if (hasBackground && parentRelativeLayout != null) animateAndDismissLayout(parentRelativeLayout!!, callback) else {
                cleanupViewsAfterDismiss()
                callback?.onComplete()
            }
        }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS)
    }

    /**
     * IAM has been fully dismissed, remove all views and call the onMessageWasDismissed callback
     */
    private fun cleanupViewsAfterDismiss() {
        removeAllViews()
        if (messageController != null) messageController!!.onSurveyWasDismissed()
    }

    /**
     * Remove all views and dismiss PopupWindow
     */
    fun removeAllViews() {
        Logger.log(LogLevel.DEBUG, "InAppMessageView removing views")
        if (scheduleDismissRunnable != null) {
            // Dismissed before the dismiss delay
            handler.removeCallbacks(scheduleDismissRunnable!!)
            scheduleDismissRunnable = null
        }
        if (draggableRelativeLayout != null) draggableRelativeLayout?.removeAllViews()
        if (popupWindow != null) popupWindow?.dismiss()
        dereferenceViews()
    }

    /**
     * Cleans all layout references so this can be cleaned up in the next GC
     */
    private fun dereferenceViews() {
        // Dereference so this can be cleaned up in the next GC
        parentRelativeLayout = null
        draggableRelativeLayout = null
//        webView = null
    }

    private fun animateSurvey(displayLocation: Position, messageView: View, backgroundView: View) {
        val messageViewCardView: CardView = messageView.findViewWithTag<CardView>(IN_APP_MESSAGE_CARD_VIEW_TAG)
        val cardViewAnimCallback = createAnimationListener(messageViewCardView)
        when (displayLocation) {
            TOP_BANNER -> animateTop(messageViewCardView, webView.height, cardViewAnimCallback)
            BOTTOM_BANNER -> animateBottom(messageViewCardView, webView.height, cardViewAnimCallback)
            CENTER_MODAL, FULL_SCREEN -> animateCenter(messageView, backgroundView, cardViewAnimCallback, null)
        }
    }

    private fun createAnimationListener(messageViewCardView: CardView): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // For Android 6 API 23 devices, waits until end of animation to set elevation of CardView class
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                    messageViewCardView.cardElevation = dpToPx(5f).toFloat()
                }
                if (messageController != null) {
                    messageController!!.onSurveyWasShown()
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        }
    }

    private fun animateTop(messageView: View, height: Int, cardViewAnimCallback: Animation.AnimationListener) {
        // Animate the message view from above the screen downward to the top
        AnimationUtils.animateViewByTranslation(
            messageView,
            (-height - marginPxSizeTop).toFloat(),
            0f,
            IN_APP_BANNER_ANIMATION_DURATION_MS,
            FeebaBounceInterpolator(0.1, 8.0),
            cardViewAnimCallback
        )
            .start()
    }

    private fun animateBottom(messageView: View, height: Int, cardViewAnimCallback: Animation.AnimationListener) {
        // Animate the message view from under the screen upward to the bottom
        AnimationUtils.animateViewByTranslation(
            messageView,
            (height + marginPxSizeBottom).toFloat(),
            0f,
            IN_APP_BANNER_ANIMATION_DURATION_MS,
            FeebaBounceInterpolator(0.1, 8.0),
            cardViewAnimCallback
        )
            .start()
    }

    private fun animateCenter(messageView: View, backgroundView: View, cardViewAnimCallback: Animation.AnimationListener, backgroundAnimCallback: Animator.AnimatorListener?) {
        // Animate the message view by scale since it settles at the center of the screen
        val messageAnimation: Animation = AnimationUtils.animateViewSmallToLarge(
            messageView,
            IN_APP_CENTER_ANIMATION_DURATION_MS,
            FeebaBounceInterpolator(0.1, 8.0),
            cardViewAnimCallback
        )

        // Animate background behind the message so it doesn't just show the dark transparency
        val backgroundAnimation = animateBackgroundColor(
            backgroundView,
            IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
            ACTIVITY_BACKGROUND_COLOR_EMPTY,
            ACTIVITY_BACKGROUND_COLOR_FULL,
            backgroundAnimCallback
        )
        messageAnimation.start()
        backgroundAnimation.start()
    }

    private fun animateAndDismissLayout(backgroundView: View, callback: WebViewManager.OneSignalGenericCallback?) {
        val animCallback: Animator.AnimatorListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                cleanupViewsAfterDismiss()
                callback?.onComplete()
            }
        }

        // Animate background behind the message so it hides before being removed from the view
        animateBackgroundColor(
            backgroundView,
            IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
            ACTIVITY_BACKGROUND_COLOR_FULL,
            ACTIVITY_BACKGROUND_COLOR_EMPTY,
            animCallback
        )
            .start()
    }

    private fun animateBackgroundColor(backgroundView: View, duration: Int, startColor: Int, endColor: Int, animCallback: Animator.AnimatorListener?): ValueAnimator {
        return AnimationUtils.animateViewColor(
            backgroundView,
            duration,
            startColor,
            endColor,
            animCallback
        )
    }

    override fun toString(): String {
        return "InAppMessageView{" +
                ", pageWidth=" + pageWidth +
                ", pageHeight=" + pageHeight +
                ", displayDuration=" + displayDuration +
                ", hasBackground=" + hasBackground +
                ", shouldDismissWhenActive=" + shouldDismissWhenActive +
                ", isDragging=" + isDragging +
                ", disableDragDismiss=" + disableDragDismiss +
                ", displayLocation=" + displayLocation +
                ", webView=" + webView +
                '}'
    }

}