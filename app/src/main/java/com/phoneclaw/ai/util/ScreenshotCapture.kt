package com.phoneclaw.ai.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class ScreenshotCapture(private val context: Context) {
    
    companion object {
        const val REQUEST_CODE_MEDIA_PROJECTION = 1001
        
        private var mediaProjection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var imageReader: ImageReader? = null
        private var resultCode: Int = 0
        private var resultData: Intent? = null
        private var screenWidth: Int = 0
        private var screenHeight: Int = 0
        private var screenDensity: Int = 0
        
        fun setMediaProjectionData(code: Int, data: Intent) {
            resultCode = code
            resultData = data
        }
        
        fun hasPermission(): Boolean = resultData != null && resultCode == Activity.RESULT_OK
        
        fun clearPermission() {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            resultCode = 0
            resultData = null
        }
    }
    
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    
    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    fun getScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    private fun ensureMediaProjection(): Boolean {
        if (mediaProjection != null && virtualDisplay != null && imageReader != null) {
            return true
        }
        
        val data = resultData ?: return false
        
        try {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
            
            // Create MediaProjection only once
            if (mediaProjection == null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                Timber.d("Created new MediaProjection")
            }
            
            val projection = mediaProjection ?: return false
            
            // Create ImageReader only once
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
                Timber.d("Created new ImageReader")
            }
            
            // Create VirtualDisplay only once
            if (virtualDisplay == null) {
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null, null
                )
                Timber.d("Created new VirtualDisplay")
            }
            
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup MediaProjection")
            return false
        }
    }
    
    suspend fun captureAndEncode(): String? {
        if (!hasPermission()) {
            Timber.w("No media projection permission")
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = captureScreen()
                if (bitmap != null) {
                    encodeBitmapToBase64(bitmap)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Screenshot capture failed")
                null
            }
        }
    }
    
    private suspend fun captureScreen(): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (!ensureMediaProjection()) {
                    Timber.w("MediaProjection not available")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                
                val reader = imageReader
                if (reader == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                
                // Give it a moment to capture fresh frame
                Thread.sleep(100)
                
                val image = reader.acquireLatestImage()
                val bitmap = if (image != null) {
                    val result = imageToBitmap(image, screenWidth, screenHeight)
                    image.close()
                    result
                } else {
                    Timber.w("No image available from ImageReader")
                    null
                }
                
                continuation.resume(bitmap)
            } catch (e: Exception) {
                Timber.e(e, "Error capturing screen")
                continuation.resume(null)
            }
        }
    }
    
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Crop to actual size if needed
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }
    }
    
    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // Scale down for API efficiency
        val maxDimension = 1024
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f
        )
        
        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
