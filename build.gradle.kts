plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// The version lives in gradle.properties and drives the desktop package version,
// the server, and the docker image tag. `-PappVersion=1.0.5` overrides it for a
// one-off build; CI passes a git tag through that way.
allprojects {
    version = (findProperty("appVersion") as? String)?.takeIf(String::isNotBlank) ?: version
}
