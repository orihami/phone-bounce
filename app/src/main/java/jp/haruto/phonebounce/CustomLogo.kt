package jp.haruto.phonebounce

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** 「画像」スタイルの形。選んだ画像を、アプリの中に PNG で1枚だけ持つ（元のアプリの custom_logo に相当）。 */
object CustomLogo {
    private const val MAX_SIDE = 512

    fun file(ctx: Context) = File(ctx.filesDir, "custom_logo.png")

    fun exists(ctx: Context) = file(ctx).exists()

    /** 保存した画像を、「白＋アルファ」の形にして返す */
    fun loadMask(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val src = BitmapFactory.decodeFile(f.path) ?: return null
        return LogoPainter.toMask(src)
    }

    /** 選んだ画像（Uri）を、大きすぎれば縮めて保存する。成功したら true */
    fun saveFrom(ctx: Context, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bm = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bm == null) false else {
            val k = MAX_SIDE.toFloat() / maxOf(bm.width, bm.height)
            val out = if (k < 1f) Bitmap.createScaledBitmap(bm, (bm.width * k).toInt().coerceAtLeast(1), (bm.height * k).toInt().coerceAtLeast(1), true) else bm
            FileOutputStream(file(ctx)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Exception) {
        false
    }
}
