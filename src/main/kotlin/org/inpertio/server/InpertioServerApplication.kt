package org.inpertio.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan

@ComponentScan("org.inpertio")
@SpringBootApplication
open class InpertioServerApplication {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(InpertioServerApplication::class.java).run(*args)
        }
    }
}