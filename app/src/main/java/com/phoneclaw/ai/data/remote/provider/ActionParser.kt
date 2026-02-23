package com.phoneclaw.ai.data.remote.provider

import com.phoneclaw.ai.domain.model.ActionStep
import com.phoneclaw.ai.domain.model.ScrollDirection
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import timber.log.Timber

object ActionParser {
    
    private val gson = Gson()
    
    fun parseActions(jsonResponse: String): Result<List<ActionStep>> {
        return try {
            Timber.d("Raw LLM response (first 500 chars): ${jsonResponse.take(500)}")
            
            // Clean up response - extract JSON array if wrapped in markdown or thinking tags
            val cleanJson = extractJsonArray(jsonResponse)
            
            Timber.d("Cleaned JSON: $cleanJson")
            
            // Handle empty array
            if (cleanJson == "[]" || cleanJson.isBlank()) {
                return Result.success(emptyList())
            }
            
            val jsonArray = gson.fromJson(cleanJson, JsonArray::class.java)
            val actions = mutableListOf<ActionStep>()
            
            for (element in jsonArray) {
                // Skip non-object elements (some models return mixed content)
                if (!element.isJsonObject) {
                    Timber.w("Skipping non-object element: $element")
                    continue
                }
                val obj = element.asJsonObject
                val action = parseAction(obj)
                if (action != null) {
                    actions.add(action)
                }
            }
            
            Result.success(actions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse actions from: ${jsonResponse.take(500)}")
            Result.failure(e)
        }
    }
    
    private fun extractJsonArray(response: String): String {
        var json = response.trim()
        
        // Remove common thinking/reasoning tags
        val thinkPatterns = listOf(
            "<think>" to "</think>",
            "<thinking>" to "</thinking>",
            "<reason>" to "</reason>",
            "<reasoning>" to "</reasoning>"
        )
        
        for ((startTag, endTag) in thinkPatterns) {
            val endIndex = json.indexOf(endTag)
            if (endIndex != -1) {
                json = json.substring(endIndex + endTag.length).trim()
            }
            // Also remove if start tag exists without end
            val startIndex = json.indexOf(startTag)
            if (startIndex != -1) {
                val nextEndIndex = json.indexOf(endTag, startIndex)
                if (nextEndIndex != -1) {
                    json = json.substring(0, startIndex) + json.substring(nextEndIndex + endTag.length)
                }
            }
        }
        
        // Remove markdown code blocks if present
        json = json.replace(Regex("```json\\s*"), "")
        json = json.replace(Regex("```\\s*"), "")
        
        // Remove any leading text before the JSON array
        val arrayStartIndex = json.indexOf('[')
        val objectStartIndex = json.indexOf('{')
        
        // Prefer array, but fall back to single object wrapped in array
        if (arrayStartIndex != -1) {
            val endIndex = json.lastIndexOf(']')
            if (endIndex > arrayStartIndex) {
                json = json.substring(arrayStartIndex, endIndex + 1)
            }
        } else if (objectStartIndex != -1) {
            // Single object - wrap in array
            val endIndex = json.lastIndexOf('}')
            if (endIndex > objectStartIndex) {
                json = "[" + json.substring(objectStartIndex, endIndex + 1) + "]"
            }
        }
        
        // Clean up common JSON issues
        json = json.trim()
        
        // Fix common LLM mistake: "x": 854, 167 -> "x": 854, "y": 167
        json = json.replace(Regex("\"x\":\\s*(\\d+),\\s*(\\d+)"), "\"x\": $1, \"y\": $2")
        
        // Remove trailing commas before ] or }
        json = json.replace(Regex(",\\s*]"), "]")
        json = json.replace(Regex(",\\s*\\}"), "}")
        
        return json
    }
    
    private fun parseAction(obj: JsonObject): ActionStep? {
        val actionType = obj.get("action")?.asString?.uppercase() ?: return null
        
        return when (actionType) {
            "CLICK" -> {
                val x = obj.get("x")?.takeIf { !it.isJsonNull }?.asInt
                val y = obj.get("y")?.takeIf { !it.isJsonNull }?.asInt
                val description = obj.get("description")?.asString
                ActionStep.Click(x = x, y = y, description = description)
            }
            
            "TYPE" -> {
                val text = obj.get("text")?.asString ?: return null
                val field = obj.get("field")?.asString
                ActionStep.Type(text = text, targetField = field)
            }
            
            "SCROLL" -> {
                val directionStr = obj.get("direction")?.asString?.uppercase() ?: "DOWN"
                val direction = try {
                    ScrollDirection.valueOf(directionStr)
                } catch (e: Exception) {
                    ScrollDirection.DOWN
                }
                val amount = obj.get("amount")?.asInt ?: 500
                ActionStep.Scroll(direction = direction, amount = amount)
            }
            
            "SWIPE" -> {
                val startX = obj.get("startX")?.asInt ?: obj.get("start_x")?.asInt ?: 500
                val startY = obj.get("startY")?.asInt ?: obj.get("start_y")?.asInt ?: 1000
                val endX = obj.get("endX")?.asInt ?: obj.get("end_x")?.asInt ?: 500
                val endY = obj.get("endY")?.asInt ?: obj.get("end_y")?.asInt ?: 500
                val duration = obj.get("duration")?.asLong ?: 300L
                ActionStep.Swipe(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    duration = duration
                )
            }
            
            "WAIT" -> {
                val duration = obj.get("duration")?.asLong 
                    ?: obj.get("milliseconds")?.asLong 
                    ?: 1000L
                ActionStep.Wait(milliseconds = duration)
            }
            
            "LAUNCH_APP" -> {
                val packageName = obj.get("package")?.asString 
                    ?: obj.get("packageName")?.asString 
                    ?: return null
                ActionStep.LaunchApp(packageName = packageName)
            }
            
            "PRESS_BACK", "BACK" -> ActionStep.PressBack
            
            "PRESS_HOME", "HOME" -> ActionStep.PressHome
            
            "SCREENSHOT" -> ActionStep.Screenshot
            
            else -> {
                Timber.w("Unknown action type: $actionType")
                null
            }
        }
    }
}
