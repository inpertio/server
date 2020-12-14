package org.inpertio.server.config.web.v1

import org.inpertio.server.config.service.ConfigService
import org.inpertio.server.config.service.impl.KeyValueFormat
import org.inpertio.server.util.WebUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class ConfigControllerV1(
    private val service: ConfigService,
    private val format: KeyValueFormat
) {

    @RequestMapping("/api/keyValue/v1/{branch}/**")
    fun getKeyValue(@PathVariable branch: String, request: HttpServletRequest): ResponseEntity<String> {
        val paths = WebUtil.getTrailingPath(request).split(",").filter(String::isNotBlank)
        val result = service.getConfigs(branch, paths, format)
        return if (result.success) {
            ResponseEntity.ok(result.successValue.entries.joinToString(separator = "\n") { "${it.key}=${it.value}" })
        } else {
            ResponseEntity(result.failureValue, HttpStatus.BAD_REQUEST)
        }
    }
}