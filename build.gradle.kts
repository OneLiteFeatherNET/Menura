plugins {
    base
}

// gradle.properties' `version` line carries a `# x-release-please-version` marker comment that
// release-please rewrites on every release. Java .properties keeps the trailing comment, so strip it here.
allprojects {
    group = "net.onelitefeather"
    version = (version as String).substringBefore('#').trim()
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        add("testImplementation", platform(rootProject.libs.junit.bom))
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testRuntimeOnly", rootProject.libs.junit.platform.launcher)
    }

    tasks {
        withType<JavaCompile>().configureEach {
            options.release.set(25)
            options.encoding = "UTF-8"
        }
        withType<JacocoReport>().configureEach {
            dependsOn(withType<Test>())
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
        withType<Test>().configureEach {
            // Integration tests (@Tag("integration")) need Docker for Testcontainers. Run the rest without it
            // via ./gradlew test -PexcludeTags=integration.
            useJUnitPlatform {
                providers.gradleProperty("excludeTags").orNull?.let { excludeTags(it) }
            }
            // Coverage reports follow every test run, so `./gradlew test` leaves them in build/reports/jacoco.
            finalizedBy("jacocoTestReport")
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

// Asserts the repository layout of the skeleton (task 1.1 of add-project-skeleton).
val verifyLayout = tasks.register("verifyLayout") {
    group = "verification"
    description = "Asserts that the Menura modules and placeholder directories exist."
    doLast {
        val root = rootProject.projectDir
        val problems = mutableListOf<String>()
        val modules = rootProject.subprojects.map { it.name }.toSet()
        listOf("core", "server", "jobs").forEach { module ->
            if (module !in modules) problems.add("module '$module' is not included in settings.gradle.kts")
        }
        listOf("web", "desktop", "obs-plugin", "overlay", "contracts/openapi", "deploy/helm/menura").forEach { dir ->
            if (!File(root, dir).isDirectory) problems.add("directory '$dir' is missing")
        }
        if (!File(root, "docker-compose.yml").isFile) problems.add("file 'docker-compose.yml' is missing")
        if (problems.isNotEmpty()) {
            throw GradleException("Repository layout is incomplete:\n - " + problems.joinToString("\n - "))
        }
    }
}

tasks.named("check") {
    dependsOn(verifyLayout)
}
