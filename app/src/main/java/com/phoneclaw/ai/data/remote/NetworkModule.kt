package com.phoneclaw.ai.data.remote

import com.phoneclaw.ai.data.remote.dto.*
import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(OpenAIContent::class.java, OpenAIContentSerializer())
        .registerTypeAdapter(ClaudeContent::class.java, ClaudeContentSerializer())
        .registerTypeAdapter(GeminiPart::class.java, GeminiPartSerializer())
        .create()
    
    val openAIService: OpenAIApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenAIApiService::class.java)
    }
    
    val claudeService: ClaudeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ClaudeApiService::class.java)
    }
    
    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GeminiApiService::class.java)
    }
}

// Custom serializers for sealed classes
class OpenAIContentSerializer : JsonSerializer<OpenAIContent> {
    override fun serialize(
        src: OpenAIContent,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return when (src) {
            is OpenAIContent.Text -> JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", src.text)
            }
            is OpenAIContent.ImageUrl -> JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", src.imageUrl.url)
                    addProperty("detail", src.imageUrl.detail)
                })
            }
        }
    }
}

class ClaudeContentSerializer : JsonSerializer<ClaudeContent> {
    override fun serialize(
        src: ClaudeContent,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return when (src) {
            is ClaudeContent.Text -> JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", src.text)
            }
            is ClaudeContent.Image -> JsonObject().apply {
                addProperty("type", "image")
                add("source", JsonObject().apply {
                    addProperty("type", src.source.type)
                    addProperty("media_type", src.source.mediaType)
                    addProperty("data", src.source.data)
                })
            }
        }
    }
}

class GeminiPartSerializer : JsonSerializer<GeminiPart> {
    override fun serialize(
        src: GeminiPart,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return when (src) {
            is GeminiPart.Text -> JsonObject().apply {
                addProperty("text", src.text)
            }
            is GeminiPart.InlineData -> JsonObject().apply {
                add("inline_data", JsonObject().apply {
                    addProperty("mime_type", src.inlineData.mimeType)
                    addProperty("data", src.inlineData.data)
                })
            }
        }
    }
}
