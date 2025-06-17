import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization)
            implementation(libs.oauth)
            implementation(libs.google.api)
            implementation(libs.logback)
        }
    }
}


compose.desktop {
    application {
        mainClass = "ru.cororo.youtubecounter.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "YouTubeCounter"
            packageVersion = "1.0.0"
            description = "Viewers and likes counter for youtube streams"
            copyright = "© 2025 TheSirCororo. All rights reserved."
            vendor = "TheSirCororo"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                iconFile.set(project.file("src/desktopMain/composeResources/drawable/favicon.ico"))
                shortcut = true
                menuGroup = "YouTubeCounter"
            }
        }
    }
}
