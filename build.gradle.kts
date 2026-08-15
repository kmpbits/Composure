import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = property("GROUP") as String
    version = property("VERSION_NAME") as String
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()

            pom {
                url.set("https://github.com/kmpbits/Composure")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kmpbits")
                        name.set("Joel Caetano")
                        url.set("https://github.com/kmpbits")
                    }
                }
                scm {
                    url.set("https://github.com/kmpbits/Composure")
                    connection.set("scm:git:git://github.com/kmpbits/Composure.git")
                    developerConnection.set("scm:git:ssh://git@github.com/kmpbits/Composure.git")
                }
            }
        }
    }
}
