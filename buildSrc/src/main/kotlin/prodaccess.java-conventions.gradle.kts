// Shared by every Java module: Java 21, Spring Boot's dependency versions, JUnit 5.
plugins {
    java
}

group = "io.prodaccess"
version = "0.1.0-SNAPSHOT"

val libs = versionCatalogs.named("libs")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters") // Spring binds @PathVariable/@RequestParam by parameter name
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
