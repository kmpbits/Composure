import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // iOS targets only — no Android, no JVM/Desktop.
    // Build the XCFramework with:
    //   ./gradlew :composure-ios:assembleComposureIosDebugXCFramework
    val xcf = XCFramework("composureIos")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "composure_ios"
            isStatic = true
            binaryOption("bundleId", "io.github.kmpbits.composure.ios")
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Exposes all composure-core types transitively to Swift
            api(project(":composure-core"))
        }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        )
    )
    pom {
        name.set("Composure iOS")
        description.set("Swift-friendly bridge layer for Composure — the Kotlin side backing ComposureFormKit.swift.")
    }
}
