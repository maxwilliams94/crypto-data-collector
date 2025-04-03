plugins {
    id("java")
    id("org.openapi.generator") version "7.12.0"
}

group = "ms.maxwillia.cryptodata.apis"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Dependencies required by the generated code.
    implementation("com.squareup.okhttp3:okhttp:4.9.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateFiriV2") {
    generatorName.set("java")
    inputSpec.set("$rootDir/apis/src/main/resources/firi-v2.yaml")
    outputDir.set("$buildDir/generated")
    apiPackage.set("ms.maxwillia.cryptodata.apis.firi.v2.api")
    invokerPackage.set("ms.maxwillia.cryptodata.apis.firi.v2.invoker")
    modelPackage.set("ms.maxwillia.cryptodata.apis.firi.v2.model")
    configOptions.put("dateLibrary", "java8")
    configOptions.put("library", "okhttp-gson")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateCoinbaseV3") {
    generatorName.set("java")
    inputSpec.set("$rootDir/apis/src/main/resources/coinbase-v3.yaml")
    outputDir.set("$buildDir/generated")
    apiPackage.set("ms.maxwillia.cryptodata.apis.coinbase.v3.api")
    invokerPackage.set("ms.maxwillia.cryptodata.apis.coinbase.v3.invoker")
    modelPackage.set("ms.maxwillia.cryptodata.apis.coinbase.v3.model")
    configOptions.put("dateLibrary", "java8")
    configOptions.put("library", "okhttp-gson")
    configOptions.put("org.openapitools.codegen.utils.oncelogger.enabled", "true")
}

tasks.register("generateClasses") {
    group = "build"
    description = "Generates the classes from the OpenAPI specifications."
    dependsOn("generateFiriV2")
    dependsOn("generateCoinbaseV3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("build") {
    dependsOn("generateClasses")
}