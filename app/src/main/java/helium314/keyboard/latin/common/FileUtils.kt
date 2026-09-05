/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common

import android.content.Context
import android.net.Uri
import helium314.keyboard.latin.utils.ExecutorUtils
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A simple class to help with removing directories recursively.
 */
object FileUtils {

    @JvmStatic
    fun deleteRecursively(path: File): Boolean {
        if (path.isDirectory) {
            val files = path.listFiles()
            if (files != null) {
                for (child in files) {
                    deleteRecursively(child)
                }
            }
        }
        return path.delete()
    }

    @JvmStatic
    fun deleteFilteredFiles(dir: File, fileNameFilter: FilenameFilter?): Boolean {
        if (!dir.isDirectory) {
            return false
        }
        val files = dir.listFiles(fileNameFilter) ?: return false
        var hasDeletedAllFiles = true
        for (file in files) {
            if (!deleteRecursively(file)) {
                hasDeletedAllFiles = false
            }
        }
        return hasDeletedAllFiles
    }

    /**
     * copy data to file on different thread to avoid NetworkOnMainThreadException
     * still effectively blocking, as we only use small files which are mostly stored locally
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyContentUriToNewFile(uri: Uri, context: Context, outfile: File) {
        val allOk = booleanArrayOf(true)
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                copyStreamToNewFile(inputStream, outfile)
            } catch (e: IOException) {
                allOk[0] = false
            } finally {
                wait.countDown()
            }
        }
        try {
            if (!wait.await(10, TimeUnit.SECONDS)) {
                allOk[0] = false
            }
        } catch (e: InterruptedException) {
            allOk[0] = false
        }
        if (!allOk[0]) {
            throw IOException("could not copy from uri")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyStreamToNewFile(inputStream: InputStream?, outfile: File) {
        if (inputStream == null) {
            throw IOException("could not open input stream")
        }
        val parentFile = outfile.parentFile
        if (parentFile == null || (!parentFile.exists() && !parentFile.mkdirs())) {
            throw IOException("could not create parent folder")
        }
        val out = FileOutputStream(outfile)
        copyStreamToOtherStream(inputStream, out)
        out.close()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyStreamToOtherStream(`in`: InputStream, out: OutputStream) {
        val buf = ByteArray(1024)
        var len: Int
        while (`in`.read(buf).also { len = it } > 0) {
            out.write(buf, 0, len)
        }
        out.flush()
    }
}
