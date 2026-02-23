package com.phoneclaw.ai.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

class ScreenAnalyzer {
    
    private val accessibilityService: AutomationAccessibilityService?
        get() = AutomationAccessibilityService.getInstance()
    
    fun analyzeCurrentScreen(): String? {
        val service = accessibilityService ?: return null
        val root = service.getRootNode() ?: return null
        
        val sb = StringBuilder()
        sb.append("Screen Elements:\n")
        
        try {
            analyzeNode(root, sb, 0)
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing screen")
        }
        
        return sb.toString()
    }
    
    private fun analyzeNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString()?.take(50) ?: ""
        val contentDesc = node.contentDescription?.toString()?.take(50) ?: ""
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable
        
        // Only include meaningful nodes
        val hasContent = text.isNotBlank() || contentDesc.isNotBlank()
        val isInteractive = isClickable || isEditable || isScrollable
        
        if (hasContent || isInteractive) {
            sb.append(indent)
            sb.append("[$className]")
            
            if (text.isNotBlank()) {
                sb.append(" text=\"$text\"")
            }
            if (contentDesc.isNotBlank()) {
                sb.append(" desc=\"$contentDesc\"")
            }
            
            val attrs = mutableListOf<String>()
            if (isClickable) attrs.add("clickable")
            if (isEditable) attrs.add("editable")
            if (isScrollable) attrs.add("scrollable")
            
            if (attrs.isNotEmpty()) {
                sb.append(" [${attrs.joinToString(", ")}]")
            }
            
            sb.append(" bounds=${bounds.toShortString()}")
            sb.append("\n")
        }
        
        // Process children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            analyzeNode(child, sb, depth + 1)
        }
    }
    
    fun getInteractiveElements(): List<UIElement> {
        val service = accessibilityService ?: return emptyList()
        val root = service.getRootNode() ?: return emptyList()
        
        val elements = mutableListOf<UIElement>()
        collectInteractiveElements(root, elements)
        return elements
    }
    
    private fun collectInteractiveElements(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable
        
        if (isClickable || isEditable || isScrollable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            elements.add(
                UIElement(
                    text = node.text?.toString() ?: "",
                    contentDescription = node.contentDescription?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    bounds = bounds,
                    isClickable = isClickable,
                    isEditable = isEditable,
                    isScrollable = isScrollable,
                    resourceId = node.viewIdResourceName
                )
            )
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInteractiveElements(child, elements)
        }
    }
}

data class UIElement(
    val text: String,
    val contentDescription: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val resourceId: String?
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()
    
    val displayName: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            resourceId != null -> resourceId.substringAfterLast('/')
            else -> className.substringAfterLast('.')
        }
}
