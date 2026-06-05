plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "ru.vp"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

application {
    mainClass.set("ru.vp.MainKt")
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.3.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })
}

tasks.named("build") {
    dependsOn("fatJar")
}
