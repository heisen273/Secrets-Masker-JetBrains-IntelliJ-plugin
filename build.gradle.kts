plugins {
    id("org.jetbrains.intellij.platform") version "2.5.0"  // Updated plugin version
    java
    kotlin("jvm") version "1.9.24"
}

group = "com"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
//    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-reporting:1.13.1")


    implementation(kotlin("stdlib"))
    intellijPlatform {
        local("/Applications/PyCharm.app")
//        val type = "IC"
//        val version = "2024.3.0"
//        create(type, version)
      }

}

//intellijPlatform {
//    version.set("2024.2.0")  // Added .0 for full version format
//    type.set("IC")
//    plugins.set(listOf("java"))
//}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    patchPluginXml {
        sinceBuild.set("212")
        untilBuild.set("252.*")
    }
    buildPlugin {
        archiveBaseName.set("SecretsMasker")
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter("5.13.1")
        }
    }
}

tasks.withType<Test>().configureEach {
    val outputDir = reports.junitXml.outputLocation
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf(
            "-Djunit.platform.reporting.open.xml.enabled=true",
            "-Djunit.platform.reporting.output.dir=${outputDir.get().asFile.absolutePath}"
        )
    }
}

