package org.inpertio.server.config.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.inpertio.server.config.service.ConfigFormat
import org.springframework.stereotype.Component
import java.io.File

@Component
class KeyValueFormat : ConfigFormat<Map<String, String>> {

    private val mapper = ObjectMapper(YAMLFactory())

    override fun format(configFiles: List<File>): Map<String, String> {
        return configFiles.fold(mutableMapOf()) { holder, file ->
            add(holder, file)
            holder
        }
    }

    private fun add(holder: MutableMap<String, String>, file: File) {
        val configData = mapper.readValue(file, Map::class.java)
        add(holder, configData, emptyList())
    }

    private fun add(holder: MutableMap<String, String>, config: Any, path: List<String>) {
        when (config) {
            is Map<*, *> -> for ((key, value) in config) {
                add(holder, value as Any, path + key.toString())
            }
            is Collection<*> -> config.forEachIndexed { i, value ->
                add(holder, value as Any, path.subList(0, path.size - 1) + (path.last() + "[$i]"))
            }
            else -> holder[path.joinToString(".")] = config.toString()
        }
    }

    override fun toString(): String {
        return "key/value"
    }
}