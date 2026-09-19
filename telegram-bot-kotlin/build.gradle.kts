plugins {
    kotlin("jvm")
    application
}

group = "com.example.telegrambot"
version = "1.0.0"

dependencies {
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // OkHttp & Okio for HTTP network calls to Telegram API & Cartesia Voice API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0")

    // JSON parsing
    implementation("org.json:json:20240303")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("org.slf4j:slf4j-api:2.0.16")
}

application {
    mainClass.set("com.example.telegrambot.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.telegrambot.MainKt"
    }
    // Fat JAR (all dependencies included) for simple Render / Docker deployment
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
