package com.vidya.ai.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Only active for WhatsApp, and only for 25 seconds after the user pressed "WhatsApp खोलें" in
 * VIDYA's confirmation card. It presses the Send button of the message the user just approved.
 * It never reads or stores message text.
 */
class VidyaAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        if (SystemClock.elapsedRealtime() > sendUntil) return
        val root = rootInActiveWindow ?: return
        if (clickSend(root)) sendUntil = 0
    }

    override fun onInterrupt() {}

    private fun isSendDesc(d: String?): Boolean {
        val s = d?.lowercase()?.trim() ?: return false
        return s == "send" || s.startsWith("send ") || s == "भेजें" || s == "भेजे" || s.startsWith("भेजें")
    }

    private fun clickSend(root: AccessibilityNodeInfo): Boolean {
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (n in nodes) {
                // the same button is the voice-note mic when the box is empty: only press when it says Send
                if (isSendDesc(n.contentDescription?.toString()) && n.isEnabled) {
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        return clickByDescription(root)
    }

    private fun clickByDescription(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        if (n.isClickable && isSendDesc(n.contentDescription?.toString())) {
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        for (i in 0 until n.childCount) {
            if (clickByDescription(n.getChild(i))) return true
        }
        return false
    }

    companion object {
        @Volatile private var sendUntil = 0L

        fun requestSend() { sendUntil = SystemClock.elapsedRealtime() + 25_000 }
        fun cancelSend() { sendUntil = 0 }
    }
}
