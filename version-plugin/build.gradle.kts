plugins {
    `kotlin-dsl`
}
gradlePlugin {
    plugins.register("versionPlugin") {
        id = "version-plugin"
        implementationClass = "com.silencefly96.version.VersionPlugin"
    }
}