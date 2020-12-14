package org.inpertio.server.config.service.impl

import org.inpertio.server.config.service.ConfigFormat
import org.inpertio.server.config.service.ConfigService
import org.inpertio.server.git.service.GitService
import org.inpertio.server.util.ProcessingResult
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashSet

@Component
class ConfigServiceImpl(
    private val gitService: GitService,
    private val logger: Logger
) : ConfigService {

    override fun <T : Any> getConfigs(
        branch: String,
        paths: List<String>,
        format: ConfigFormat<T>
    ): ProcessingResult<T, String> {
        logger.debug("Got a request to get configs for paths {} in branch '{}' with format", paths, branch, format)
        val result = gitService.withBranch<ProcessingResult<T, String>>(branch) { _, rootDir ->
            val inputFiles = paths.map { path ->
                File(rootDir, path).apply {
                    if (!exists()) {
                        logger.info("No path '{}' is found in branch '{}'", path, branch)
                        return@withBranch ProcessingResult.failure("path '$path' doesn't exist in branch $branch")
                    }
                }
            }
            if (logger.isDebugEnabled) {
                logger.debug("Found the following config files for paths {}: {}",
                             paths, inputFiles.map(File::getAbsolutePath))
            }
            ProcessingResult.success(getConfigs(inputFiles, format))
        }
        return if (result == null) {
            logger.info("Can't return {} configs for paths {} in branch '{}' - the branch doesn't exist",
                        format, paths, branch)
            ProcessingResult.failure("branch '$branch' doesn't exist")
        } else {
            result
        }
    }

    private fun <T> getConfigs(inputFiles: List<File>, format: ConfigFormat<T>): T {
        val configFiles = LinkedHashSet<File>()
        for (file in inputFiles) {
            file.walkTopDown().filter(File::isFile).forEach(configFiles::add)
        }
        return format.format(configFiles.toList())
    }
}