package com.exog007.fardriver.app.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

/**
 * 背景图片存取
 *
 * 从相册选的图会缩放到合理尺寸（长边 2000px）后以 JPEG 存到 App 私有目录，
 * 这样不会占用太多空间，读取也快。
 */
object BackgroundImageStore {

    private const val FILE_NAME = "bg-photo.jpg"
    private const val MAX_SIDE = 2000

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    /**
     * 保存图片
     * @return 是否成功
     */
    fun save(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val scaled = scaleDown(bitmap, MAX_SIDE)
            FileOutputStream(file(context)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun load(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context) {
        try { file(context).delete() } catch (_: Exception) { }
    }

    /**
     * 缩小到长边不超过 maxSide（等比）
     */
    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, newW, newH, true)
        if (scaled !== src) src.recycle()
        return scaled
    }
}
