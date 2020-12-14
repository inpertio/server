package org.inpertio.server.config.service

import java.io.File

/**
 * Generally a request for config files is processed in two steps:
 * 1. Target config files are discovered
 * 2. Config files content is prepared in the target format
 *
 * This interface represents a strategy which converts given config files to the target format
 */
interface ConfigFormat<T> {

    fun format(configFiles: List<File>): T
}