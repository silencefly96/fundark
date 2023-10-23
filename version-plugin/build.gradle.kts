plugins {
    `kotlin-dsl`
}
gradlePlugin {
    plugins.register("versionBasePlugin") {
        id = "version-base-plugin"
        implementationClass = "com.silencefly96.version.VersionBasePlugin"
    }
    plugins.register("versionTestPlugin") {
        id = "version-test-plugin"
        implementationClass = "com.silencefly96.version.VersionTestPlugin"
    }
    plugins.register("versionThirdPlugin") {
        id = "version-third-plugin"
        implementationClass = "com.silencefly96.version.VersionThirdPlugin"
    }
    plugins.register("composingBuildPlugin") {
        id = "composing-build-plugin"
        implementationClass = "com.silencefly96.version.ComposingBuildPlugin"
    }
}