package dev.lastlight.reborn.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SaveBackupManager {
    fun countSaveFiles(context: Context): Int {
        val dir = GamePaths.savesDir(context)
        return dir.walkTopDown().count { it.isFile }
    }

    fun exportToZip(context: Context, out: OutputStream): Int {
        val root = GamePaths.savesDir(context)
        var count = 0
        ZipOutputStream(out).use { zos ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(root).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(rel))
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
                count++
            }
        }
        return count
    }

    fun importFromZip(context: Context, input: InputStream): Int {
        val root = GamePaths.savesDir(context)
        val canonicalRoot = root.canonicalPath + File.separator
        var count = 0
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val dst = File(root, entry.name)
                    if (dst.canonicalPath.startsWith(canonicalRoot)) {
                        dst.parentFile?.mkdirs()
                        FileOutputStream(dst).use { zis.copyTo(it) }
                        count++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    fun importFromPeanutsTest(context: Context): Int {
        val candidates = listOf(
            File("/sdcard/Android/data/dev.nxnative.android.peanutstest/files/saves/${GamePaths.TITLE_ID}"),
            File("/sdcard/Android/data/dev.nxnative.android.peanuts/files/saves/${GamePaths.TITLE_ID}")
        )
        val src = candidates.firstOrNull { it.isDirectory && it.walkTopDown().any { f -> f.isFile } } ?: return 0
        val dstRoot = GamePaths.savesDir(context)
        var count = 0
        src.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(src).path
            val dst = File(dstRoot, rel)
            dst.parentFile?.mkdirs()
            f.copyTo(dst, overwrite = true)
            count++
        }
        return count
    }
}
