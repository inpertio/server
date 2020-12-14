package org.inpertio.server.git.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class RemoteGitParameters(@Value("\${${Parameter.URI}:}") val uri: String) {

    init {
        if (uri.isBlank()) {
            throw IllegalStateException(
                    "No remote repo uri is provided, expected for it to be defined via '${Parameter.URI}' property"
            )
        }
    }

    private object Parameter {
        const val URI = "INPERTIO_CONFIG_REPO_URI"
    }
}