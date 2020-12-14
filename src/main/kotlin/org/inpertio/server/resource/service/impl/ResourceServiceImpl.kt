package org.inpertio.server.resource.service.impl

import org.inpertio.server.git.service.GitService
import org.inpertio.server.resource.service.ResourceService
import org.inpertio.server.util.ProcessingResult
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream

@Component
class ResourceServiceImpl(
    private val gitService: GitService,
    private val logger: Logger
) : ResourceService {

    override fun getResource(branch: String, resourcePath: String): ProcessingResult<InputStream, String> {
        logger.debug("Got a request for resource '{}' in branch '{}'", resourcePath, branch)
        val result = gitService.withBranch<ProcessingResult<InputStream, String>>(branch) { _, rootDir ->
            val resource = File(rootDir, resourcePath)
            if (resource.isFile) {
                logger.debug("Returning with content from '{}' in branch {}", resource.absolutePath, branch)
                ProcessingResult.success(resource.inputStream())
            } else {
                logger.info("No resource at path '{}' is found in branch '{}'", resourcePath, branch)
                ProcessingResult.failure("no resource at path '$resourcePath' is found in branch '$branch'")
            }
        }

        return if (result == null) {
            logger.info("Can't return a resource '{}' in branch '{}' - the branch doesn't exist", resourcePath, branch)
            ProcessingResult.failure("unknown branch '$branch'")
        } else {
            result
        }
    }
}