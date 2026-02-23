package com.phoneclaw.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class AutomationAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: AutomationAccessibilityService? = null
        
        fun getInstance(): AutomationAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.d("Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can use this to monitor UI changes if needed
    }
    
    override fun onInterrupt() {
        Timber.d("Accessibility Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.d("Accessibility Service destroyed")
    }
    
    // Get the root node of the current window
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }
    
    // Perform a click at coordinates
    suspend fun performClick(x: Int, y: Int): Boolean {
        return performGesture(createClickGesture(x.toFloat(), y.toFloat()))
    }
    
    // Perform a click on an element by finding it
    suspend fun performClickOnElement(description: String): Boolean {
        val node = findNodeByDescription(description)
        if (node != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()
            Timber.d("Found element '$description' at ($centerX, $centerY)")
            return performClick(centerX, centerY)
        }
        Timber.w("Element not found: $description")
        return false
    }
    
    // Find a node by its text, content description, or view ID
    fun findNodeByDescription(description: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        
        // Build search terms including translations for common UI elements
        val searchTerms = buildSearchTerms(description.lowercase())
        Timber.d("Searching for element with terms: $searchTerms")
        
        return findNodeRecursive(root, searchTerms)
    }
    
    private fun buildSearchTerms(description: String): List<String> {
        val terms = mutableListOf(description)
        
        // Add common translations (English -> Chinese)
        val translations = mapOf(
            "search" to listOf("搜索", "搜尋", "查找"),
            "settings" to listOf("设置", "設置", "设定"),
            "back" to listOf("返回", "后退"),
            "home" to listOf("首页", "主页"),
            "profile" to listOf("我的", "个人", "我"),
            "menu" to listOf("菜单", "更多"),
            "play" to listOf("播放", "播放"),
            "pause" to listOf("暂停"),
            "next" to listOf("下一首", "下一个"),
            "previous" to listOf("上一首", "上一个"),
            "share" to listOf("分享"),
            "like" to listOf("喜欢", "收藏", "赞"),
            "comment" to listOf("评论"),
            "download" to listOf("下载"),
            "close" to listOf("关闭", "关"),
            "cancel" to listOf("取消"),
            "confirm" to listOf("确定", "确认"),
            "ok" to listOf("确定", "好的"),
            "send" to listOf("发送"),
            "input" to listOf("输入"),
            "music" to listOf("音乐", "歌曲"),
            "video" to listOf("视频"),
            "photo" to listOf("照片", "图片"),
        )
        
        // Check each word in description for translations
        val words = description.split(" ", "_", "-")
        for (word in words) {
            translations[word]?.let { terms.addAll(it) }
        }
        
        // Also add the individual words as search terms
        if (words.size > 1) {
            terms.addAll(words)
        }
        
        return terms.distinct()
    }
    
    private fun findNodeRecursive(node: AccessibilityNodeInfo, searchTerms: List<String>): AccessibilityNodeInfo? {
        // Check text
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        
        val matches = searchTerms.any { term ->
            nodeText.contains(term) || contentDesc.contains(term) || viewId.contains(term)
        }
        
        if (matches) {
            Timber.d("Found matching node: text='$nodeText', desc='$contentDesc', clickable=${node.isClickable}")
            // Prefer clickable nodes
            if (node.isClickable) {
                return node
            }
        }
        
        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, searchTerms)
            if (found != null) {
                return found
            }
        }
        
        // Return this node if it matches even if not clickable (parent might handle click)
        if (matches) {
            return node
        }
        
        return null
    }
    
    // Perform a swipe gesture
    suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 300): Boolean {
        return performGesture(createSwipeGesture(
            startX.toFloat(), startY.toFloat(),
            endX.toFloat(), endY.toFloat(),
            duration
        ))
    }
    
    // Perform scroll
    suspend fun performScroll(direction: String, amount: Int = 500): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        
        return when (direction.uppercase()) {
            "UP" -> performSwipe(centerX, centerY + amount / 2, centerX, centerY - amount / 2, 300)
            "DOWN" -> performSwipe(centerX, centerY - amount / 2, centerX, centerY + amount / 2, 300)
            "LEFT" -> performSwipe(centerX + amount / 2, centerY, centerX - amount / 2, centerY, 300)
            "RIGHT" -> performSwipe(centerX - amount / 2, centerY, centerX + amount / 2, centerY, 300)
            else -> false
        }
    }
    
    // Type text into focused field
    suspend fun performTypeText(text: String): Boolean {
        // Try multiple strategies to find the text field
        var targetNode = findFocusedEditText()
        
        if (targetNode == null) {
            Timber.d("No focused edit text, trying to find any editable field...")
            targetNode = findAnyEditableField()
        }
        
        if (targetNode != null) {
            Timber.d("Found editable node: ${targetNode.className}, text=${targetNode.text}")
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (result) {
                return true
            }
            // Try alternative: focus first, then set text
            Timber.d("ACTION_SET_TEXT failed, trying to focus first...")
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            kotlinx.coroutines.delay(100)
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        
        Timber.w("No editable text field found on screen")
        return false
    }
    
    private fun findFocusedEditText(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        // First try using the system's focus finder
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            return focused
        }
        // Fall back to manual search
        return findEditTextRecursive(root, requireFocus = true)
    }
    
    private fun findAnyEditableField(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findEditTextRecursive(root, requireFocus = false)
    }
    
    private fun findEditTextRecursive(node: AccessibilityNodeInfo, requireFocus: Boolean): AccessibilityNodeInfo? {
        val isMatch = if (requireFocus) {
            node.isFocused && node.isEditable
        } else {
            node.isEditable
        }
        
        if (isMatch) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditTextRecursive(child, requireFocus)
            if (found != null) {
                return found
            }
        }
        
        return null
    }
    
    // Press back button
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    // Press home button
    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    // Launch an app by package name
    fun launchApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                true
            } else {
                Timber.w("Could not find launch intent for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch app: $packageName")
            false
        }
    }
    
    // Gesture helpers
    private fun createClickGesture(x: Float, y: Float): GestureDescription {
        val path = Path()
        path.moveTo(x, y)
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        return builder.build()
    }
    
    private fun createSwipeGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): GestureDescription {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        return builder.build()
    }
    
    private suspend fun performGesture(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Timber.d("Gesture completed")
                    continuation.resume(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Timber.w("Gesture cancelled")
                    continuation.resume(false)
                }
            }
            
            val result = dispatchGesture(gesture, callback, null)
            if (!result) {
                Timber.w("Failed to dispatch gesture")
                continuation.resume(false)
            }
        }
    }
}
