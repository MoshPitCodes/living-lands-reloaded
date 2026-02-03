plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

import java.util.Properties

group = "com.hytaleserver.ll-reloaded"

val modVersion: String = Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}.getProperty("mod.version", "0.0.0")

version = modVersion

repositories {
    mavenCentral()
}

dependencies {
    // Hytale Server API (compile only - provided at runtime)
    compileOnly(files("libs/hytale-server/HytaleServer.jar"))
    
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // SQLite JDBC driver
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    
    // Jackson for YAML configuration (cleaner serialization than SnakeYAML)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    
    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        // Use JVM 21 to match toolchain
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    // Match Kotlin's JVM target for compatibility
    targetCompatibility = "21"
    sourceCompatibility = "21"
}

tasks {
    test {
        useJUnitPlatform()
    }
    
    processResources {
        filesMatching("manifest.json") {
            expand(mapOf("version" to version))
        }
    }
    
    // Regular JAR (without dependencies) - mark as "thin"
    jar {
        archiveBaseName.set("livinglands-reloaded")
        archiveClassifier.set("thin")  // Mark this as the thin JAR (not used for deployment)
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }
    
    // Shadow JAR (with all dependencies) - this is the primary artifact for deployment
    shadowJar {
        archiveBaseName.set("livinglands-reloaded")
        archiveClassifier.set("")  // No classifier - this becomes livinglands-reloaded-1.0.0-beta.jar
        
        // Relocate dependencies to avoid conflicts (NOT sqlite - it uses JDBC which needs original package)
        relocate("com.fasterxml.jackson", "com.livinglands.libs.jackson")
        relocate("kotlinx.coroutines", "com.livinglands.libs.coroutines")
        
        // Don't minimize - it causes issues with JDBC drivers and coroutines
        // minimize {
        //     exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        //     exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
        //     exclude(dependency("org.yaml:snakeyaml:.*"))
        //     exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        //     exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common:.*"))
        // }
    }
    
    build {
        dependsOn(shadowJar)
    }
}
