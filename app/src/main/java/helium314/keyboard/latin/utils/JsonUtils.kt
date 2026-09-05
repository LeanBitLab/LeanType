/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.util.JsonReader
import android.util.JsonWriter
import java.io.Closeable
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

object JsonUtils {
    private const val TAG = "JsonUtils"

    private const val INTEGER_CLASS_NAME = "Integer"
    private const val STRING_CLASS_NAME = "String"

    private const val EMPTY_STRING = ""

    @JvmStatic
    fun jsonStrToList(s: String): List<Any> {
        val list = ArrayList<Any>()
        val reader = JsonReader(StringReader(s))
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == INTEGER_CLASS_NAME) {
                        list.add(reader.nextInt())
                    } else if (name == STRING_CLASS_NAME) {
                        list.add(reader.nextString())
                    } else {
                        Log.w(TAG, "Invalid name: $name")
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
            reader.endArray()
            return list
        } catch (ignored: IOException) {
        } finally {
            close(reader)
        }
        return emptyList()
    }

    @JvmStatic
    fun listToJsonStr(list: List<Any>?): String {
        if (list.isNullOrEmpty()) {
            return EMPTY_STRING
        }
        val sw = StringWriter()
        val writer = JsonWriter(sw)
        try {
            writer.beginArray()
            for (o in list) {
                writer.beginObject()
                if (o is Int) {
                    writer.name(INTEGER_CLASS_NAME).value(o.toLong())
                } else if (o is String) {
                    writer.name(STRING_CLASS_NAME).value(o)
                }
                writer.endObject()
            }
            writer.endArray()
            return sw.toString()
        } catch (ignored: IOException) {
        } finally {
            close(writer)
        }
        return EMPTY_STRING
    }

    private fun close(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            // Ignore
        }
    }
}
