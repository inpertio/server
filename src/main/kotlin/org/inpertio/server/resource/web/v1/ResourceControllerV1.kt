package org.inpertio.server.resource.web.v1

import org.inpertio.server.resource.service.ResourceService
import org.inpertio.server.util.WebUtil
import org.slf4j.Logger
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.InputStreamSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/resource/v1")
class ResourceControllerV1(
    private val service: ResourceService,
    private val logger: Logger
) {

    @RequestMapping("/{branch}/**")
    fun getResource(@PathVariable branch: String, request: HttpServletRequest): ResponseEntity<InputStreamSource> {
        val resourcePath = WebUtil.getTrailingPath(request)
        val result = service.getResource(branch, resourcePath)
        if (logger.isDebugEnabled) {
            logger.debug("Got the following result on attempt to get resource '{}' in branch '{}': {}",
                         branch, resourcePath, resourcePath)
        }
        return if (result.success) {
            ResponseEntity.ok(InputStreamResource(result.successValue))
        } else {
            ResponseEntity(InputStreamResource(result.failureValue.toByteArray().inputStream()), HttpStatus.BAD_REQUEST)
        }
    }

    companion object {
        private val ANT_MATCHER = AntPathMatcher()
    }
}