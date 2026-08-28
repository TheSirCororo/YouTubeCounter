import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain = getByName("desktopMain")

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
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
            implementation(libs.javaKeyring)
        }
    }
}

val appVersion = version.toString()

compose.desktop {
    application {
        mainClass = "ru.cororo.youtubecounter.MainKt"

        buildTypes.release.proguard {
            version.set("7.10.0")
            configurationFiles.from("rules.pro")
        }

        nativeDistributions {
            modules("jdk.httpserver", "jdk.unsupported", "java.naming", "jdk.security.auth")
            targetFormats(
                TargetFormat.Msi,                                          // Windows
                TargetFormat.Dmg,                                          // macOS
                TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm, // Linux
            )
            packageName = "YouTubeCounter"
            packageVersion = appVersion
            description = "Viewers and likes counter for youtube streams"
            copyright = "© 2026 TheSirCororo. All rights reserved."
            vendor = "TheSirCororo"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                iconFile.set(project.file("src/desktopMain/composeResources/drawable/favicon.ico"))
                shortcut = true
                menuGroup = "YouTubeCounter"
            }

            linux {
                iconFile.set(project.file("src/desktopMain/composeResources/drawable/icon.png"))
                shortcut = true
                menuGroup = "YouTubeCounter"
                rpmLicenseType = "MIT"
            }

            macOS {
                bundleID = "ru.cororo.youtubecounter"
                dockName = "YouTubeCounter"
                // Unsigned: without an Apple Developer ID, Gatekeeper asks the user to
                // allow the app on first launch.
            }
        }
    }
}
