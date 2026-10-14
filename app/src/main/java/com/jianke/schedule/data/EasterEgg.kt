package com.jianke.schedule.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 彩蛋图片的加载逻辑。
 *
 * 图片还没提供，所以这里**不写死路径**，而是按顺序找几个候选位置：
 *   1. assets/ 下的候选文件名（打包进 APK，推荐）
 *   2. App 私有目录 files/ 下的同名文件（方便临时替换）
 * 都找不到就返回 null，界面显示占位内容。
 *
 * 拿到图片后，把文件命名为下面任意一个放进 assets/ 即可，不用改代码。
 */
object EasterEgg {

    /** assets 目录下会依次尝试的文件名。 */
    val ASSET_CANDIDATES = listOf(
        "easter_egg.png",
        "easter_egg.jpg",
        "easter_egg.webp",
        "easter_egg.jpeg",
    )

    /** 换好图片后想让用户看到的那句话，也放在这里方便统一改。 */
    const val TITLE = "最初的起点"

    const val PLACEHOLDER_HINT = "照片还没放进来：把图片命名为 easter_egg.png 放进 assets/ 目录即可"

    fun load(context: Context): ImageBitmap? {
        for (name in ASSET_CANDIDATES) {
            loadFromAssets(context, name)?.let { return it }
        }
        for (name in ASSET_CANDIDATES) {
            loadFromFiles(context, name)?.let { return it }
        }
        return null
    }

    /** 当前实际命中的位置，用于在界面上提示（找不到时为空）。 */
    fun resolvedSource(context: Context): String? {
        for (name in ASSET_CANDIDATES) {
            try {
                context.assets.open(name).use { }
                return "assets/" + name
            } catch (t: Throwable) {
            }
        }
        for (name in ASSET_CANDIDATES) {
            val f = File(context.filesDir, name)
            if (f.exists()) return "files/" + name
        }
        return null
    }

    private fun loadFromAssets(context: Context, name: String): ImageBitmap? {
        return try {
            context.assets.open(name).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun loadFromFiles(context: Context, name: String): ImageBitmap? {
        return try {
            val f = File(context.filesDir, name)
            if (!f.exists()) return null
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (t: Throwable) {
            null
        }
    }
}
