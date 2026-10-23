package com.tangem.sdk.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tangem.Log
import com.tangem.sdk.R
import com.tangem.sdk.SessionViewDelegateState
import com.tangem.sdk.ui.widget.StateWidget
import java.util.concurrent.atomic.AtomicBoolean

open class BaseSdkDialog(context: Context) : BottomSheetDialog(context) {

    init {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    protected val stateWidgets = mutableListOf<StateWidget<*>>()
    private val isDismissedProgrammatically = AtomicBoolean(false)

    protected open fun setStateAndShow(
        state: SessionViewDelegateState,
        vararg views: StateWidget<SessionViewDelegateState>,
    ) {
        Log.view { "setStateAndShow: state: $state" }
        views.forEach { it.setState(state) }

        val toHide = stateWidgets.filter { !views.contains(it) && it.isVisible() }
        val toShow = views.filter { !it.isVisible() }

        toHide.forEach { it.showWidget(false) }
        toShow.forEach { it.showWidget(true) }
    }

    protected fun enableBottomSheetAnimation() {
        (findDesignBottomSheetView()?.parent as? ViewGroup)?.let {
            TransitionManager.beginDelayedTransition(it)
        }
    }

    protected fun findDesignBottomSheetView(): View? {
        return delegate.findViewById(com.google.android.material.R.id.design_bottom_sheet)
    }

    protected fun performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val llHeader = findViewById<LinearLayout>(R.id.llHeader)!!
            llHeader.isHapticFeedbackEnabled = true
            llHeader.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }

    override fun dismiss() {
        Log.view { "dismiss" }
        val dismissedProgrammatically = isDismissedProgrammatically.getAndSet(false)
        if (!dismissedProgrammatically) {
            stateWidgets.forEach { it.onBottomSheetDismiss() }
        }
        stateWidgets.forEach { it.onDismiss() }
        if (ownerActivity == null || ownerActivity?.isFinishing == true) return

        super.dismiss()
    }

    /**
     * Dismiss bottom sheet without triggering StateWidget.onBottomSheetDismiss() callback.
     */
    fun dismissInternal() {
        Log.view { "dismissInternal" }
        isDismissedProgrammatically.set(true)
        dismiss()
    }
}
