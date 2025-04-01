plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "ms.maxwillia.tradercli"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":app"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "ms.maxwillia.tradercli.TraderCLI"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ms.maxwillia.tradercli.TraderCLI"
    }
}