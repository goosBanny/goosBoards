plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.goosbanny"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("net.openhft:zero-allocation-hashing:0.16")
    implementation("com.tchristofferson:ConfigUpdater:2.2")
    implementation("com.h2database:h2:2.2.224")
    implementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("com.github.retrooper:packetevents-spigot:2.13.0")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")
    testImplementation("io.netty:netty-buffer:4.1.100.Final")
    testImplementation("io.netty:netty-transport:4.1.100.Final")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.github.benmanes.caffeine", "me.goosbanny.goosboards.libs.caffeine")
    relocate("net.openhft", "me.goosbanny.goosboards.libs.zah")
    relocate("com.tchristofferson.configupdater", "me.goosbanny.goosboards.libs.configupdater")
    relocate("org.h2", "me.goosbanny.goosboards.libs.h2")
    relocate("com.zaxxer.hikari", "me.goosbanny.goosboards.libs.hikari")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
