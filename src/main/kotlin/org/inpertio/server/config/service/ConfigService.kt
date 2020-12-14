package org.inpertio.server.config.service

import org.inpertio.server.util.ProcessingResult

interface ConfigService {

    /**
     * Allows getting configs for the target paths in the target format
     */
    fun <T : Any> getConfigs(branch: String, paths: List<String>, format: ConfigFormat<T>): ProcessingResult<T, String>
}