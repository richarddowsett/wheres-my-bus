plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    application
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "uk.co.wheresmybus"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.3"
val coroutinesVersion = "1.10.1"
val serializationVersion = "1.8.0"
val logbackVersion = "1.5.16"
val junitVersion = "5.11.4"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("uk.co.wheresmybus.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
}

kover {
    reports {
        filters {
            excludes {
                classes("uk.co.wheresmybus.ApplicationKt*")
            }
        }
        verify {
            rule("Minimum 80% code coverage") {
                minBound(80)
            }
        }
    }
}

tasks.check {
    dependsOn("koverVerify")
}
