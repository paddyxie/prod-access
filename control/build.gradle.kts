plugins {
    id("prodaccess.java-conventions")
    alias(libs.plugins.spring.boot)
}

// The built UI (served as static resources) and the runner jar (forked by the end-to-end tests).
val webDist = configurations.dependencyScope("webDist")
val webDistFiles = configurations.resolvable("webDistFiles") { extendsFrom(webDist.get()) }
val runnerJar = configurations.dependencyScope("runnerJar")
val runnerJarFiles = configurations.resolvable("runnerJarFiles") { extendsFrom(runnerJar.get()) }

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)

    webDist(project(":web", "dist"))
    runnerJar(project(":runner", "shadowRuntimeElements")) { isTransitive = false }
}

tasks.processResources {
    from(webDistFiles) { into("static") }
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "control.jar"
}

val runnerJarArg = CommandLineArgumentProvider { listOf("-Dprodaccess.runner.jar=${runnerJarFiles.get().singleFile}") }

tasks.bootRun {
    workingDir = rootDir
    environment("SPRING_PROFILES_DEFAULT", providers.environmentVariable("SPRING_PROFILES_DEFAULT").orElse("local").get())
    inputs.files(runnerJarFiles)
    jvmArgumentProviders.add(runnerJarArg)
}

tasks.test {
    inputs.files(runnerJarFiles)
    jvmArgumentProviders.add(runnerJarArg)
}
