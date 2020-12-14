package org.inpertio.server.di

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
open class DiConfiguration {

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    open fun logger(injectionPoint: InjectionPoint): Logger {
        return LoggerFactory.getLogger(getHolderClass(injectionPoint))
    }

    private fun getHolderClass(injectionPoint: InjectionPoint): Class<*> {
        return injectionPoint.methodParameter?.containingClass
               ?: injectionPoint.field?.declaringClass
               ?: throw IllegalArgumentException("Can't deduce holder class for injection point $injectionPoint")
    }
}