package org.inpertio.server.util

import java.io.File

object FileUtil {

    fun ensureDirectoryExists(dir: File) {
        if (dir.isDirectory) {
            return
        }

        if (dir.exists()) {
            throw IllegalStateException(
                    "Can't prepare a directory at path '${dir.absolutePath}' - a file at that path already exists"
            )
        }

        if (!dir.mkdirs()) {
            throw IllegalStateException("Can't create directory at path ${dir.absolutePath}")
        }
    }
}