plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.anticheat"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Local stub JARs for Paper API and ProtocolLib (no network access in build env)
    compileOnly(files("libs/paper-api.jar"))
    compileOnly(files("libs/protocollib.jar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("okhttp3", "com.anticheat.libs.okhttp3")
    relocate("okio", "com.anticheat.libs.okio")
    relocate("com.google.gson", "com.anticheat.libs.gson")
    relocate("com.zaxxer.hikari", "com.anticheat.libs.hikari")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
