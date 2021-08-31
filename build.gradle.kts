buildscript {
    val kotlin_version by extra("1.5.30")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("me.champeau.jmh:jmh-gradle-plugin:0.6.5")
    }
}

group = "dev.jomu"
version = "1.0"

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        google()
    }
}