// The sandboxed script runner: a self-contained jar that the control app forks once per run.
plugins {
    id("prodaccess.java-conventions")
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.graalvm.polyglot)
    implementation(libs.graalvm.js.language)
    implementation(libs.graalvm.truffle.runtime)
}

application {
    mainClass = "io.prodaccess.runner.RunnerMain"
}

tasks.shadowJar {
    archiveFileName = "runner.jar"
    mergeServiceFiles() // Truffle registers languages through META-INF/services
}
