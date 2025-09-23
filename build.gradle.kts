import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.buildpack.platform.build.PullPolicy

plugins {
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.0"
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.jpa") version "2.2.20"
}

group = "com.albert"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

// Version catalog
val springDocVersion = "2.8.13"
val kotlinLoggingVersion = "7.0.13"
val mockkVersion = "1.13.8"
val restAssuredVersion = "5.5.6"


dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springDocVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-jdbc")
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("io.rest-assured:spring-mock-mvc:$restAssuredVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootBuildImage {
    builder.set("paketobuildpacks/builder-jammy-buildpackless-tiny:latest")
    if (project.hasProperty("native")) {
        buildpacks.set(listOf("paketobuildpacks/java-native-image"))
    }

    environment.set(
        mutableMapOf<String, String>("BP_JVM_VERSION" to "21", "BP_JVM_TYPE" to "JRE").apply {
            if (project.hasProperty("native")) {
                put("BP_NATIVE_IMAGE", "true")
                put(
                    "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
                    """
                    --no-fallback
                    --enable-url-protocols=http,https
                    -H:+ReportExceptionStackTraces
                    """.trimIndent()
                        .replace("\n", " "),
                )
            }
        },
    )

    pullPolicy.set(PullPolicy.IF_NOT_PRESENT)

    imageName.set("${project.group}/${project.name}:${project.version}")

    tags.set(
        listOf(
            "${project.group}/${project.name}:latest",
            "${project.group}/${project.name}:${project.version}",
        ),
    )
}

graalvmNative { binaries { named("main") { imageName.set("${project.group}.${project.name}") } } }
