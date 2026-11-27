package com.yuanguangbb.ledger.ui.add

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** 单据照片存到 App 私有目录，账单里只存绝对路径 */
object ReceiptStore {

    private const val DIR = "receipts"

    fun save(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        runCatching { path?.let { File(it).delete() } }
    }
}