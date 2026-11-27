package com.yuanguangbb.ledger.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** 图片解码工具：先量尺寸再按 2 的幂降采样，避免大图把内存打满 */
object ImageUtils {

    /**
     * @param targetPx 最长边期望像素，默认 1080（屏幕够用，内存友好）
     */
    fun decodeSampled(path: String, targetPx: Int = 1080): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > targetPx) sample *= 2

        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()
}