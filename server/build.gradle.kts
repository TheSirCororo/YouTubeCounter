plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "ru.cororo.youtubecounter"

// Pinned so the bytecode always matches the JRE of the Docker image built by the ktor plugin,
// regardless of which JDK Gradle itself runs on.
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.oauth)
    implementation(libs.google.api)
    implementation(libs.logback)
}

application {
    mainClass.set("ru.cororo.youtubecounter.GoogleAuthorizationServerKt")
}

ktor {
    docker {
        imageTag = version.toString()
        localImageName = "youtubecounter-backend"
    }
}