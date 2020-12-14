plugins {
    kotlin("jvm") version "1.4.10"
    id("org.springframework.boot") version "2.4.0"
}

apply(plugin = "io.spring.dependency-management")

group = "tech.harmonysoft"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.4")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r")
}

tasks {
    bootJar {
        archiveFileName.set("${project.name}.jar")
    }
}