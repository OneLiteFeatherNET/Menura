plugins {
    alias(libs.plugins.micronaut.application)
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation(project(":core"))
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly(libs.postgresql)
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
}

application {
    mainClass.set("net.onelitefeather.menura.server.Application")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        annotations("net.onelitefeather.menura.server.*")
    }
}
