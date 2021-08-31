pluginManagement {
    repositories {
        google()
        jcenter()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    
}
rootProject.name = "workflow-compose"


include(":sample:android")
include(":sample:desktop")
include(":sample:common")
include("runtime")
include("ui")
include("benchmark")
include(":benchmark2")
