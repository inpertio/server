package org.inpertio.server.git.config

import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

@Component
class LocalGitParameters(
    @Value("\${${Parameter.LOCAL_ROOT}:}") rootPath: String,
    logger: Logger
) {

    val rootDir: File

    init {
        rootDir = if (rootPath.isBlank()) {
            Files.createTempDirectory("").toFile().apply {
                logger.info("No root local dir path is defined explicitly via '{}' property, using auto-generated "
                            + "directory at {}", Parameter.LOCAL_ROOT, absolutePath)
            }
        } else {
            File(rootPath).apply {
                if (!isDirectory) {
                    val ok = mkdirs()
                    if (!ok) {
                        throw IllegalStateException("Can't create a directory $rootPath as a local data root")
                    }
                }
                logger.info("Using pre-configured root local dir {}", rootPath)
            }
        }
    }

    private object Parameter {
        const val LOCAL_ROOT = "local.data.root.path"
    }
}