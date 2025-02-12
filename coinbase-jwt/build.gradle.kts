plugins {
    id("java")
    id("application")
}

group = "org.example"
version = "unspecified"

application {
    mainClass.set("org.example.Main")
}

repositories {
    mavenCentral()
}

dependencies {
implementation("com.nimbusds:nimbus-jose-jwt:9.39")
implementation("org.bouncycastle:bcpkix-jdk18on:1.78")
implementation("io.github.cdimascio:java-dotenv:5.2.2")
testImplementation(platform("org.junit:junit-bom:5.10.0"))
testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
