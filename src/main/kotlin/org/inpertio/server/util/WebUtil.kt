package org.inpertio.server.util

import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

object WebUtil {

    private val matcher = AntPathMatcher()

    /**
     * We have web controllers which receive target path as a trailing request path. It's not possible to define
     * a static mapping for them then.
     *
     * This utility method allows parsing such trailing request path.
     */
    fun getTrailingPath(request: HttpServletRequest): String {
        val requestPath = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        val controllerPath = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).toString()
        return matcher.extractPathWithinPattern(controllerPath, requestPath)
    }
}