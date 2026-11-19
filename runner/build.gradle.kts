// The sandboxed script runner: a self-contained jar that the control app forks once per run.
plugins {
    id("prodaccess.java-conventions")
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.quickjs4j)
}

application {
    mainClass = "io.prodaccess.runner.RunnerMain"
}

tasks.shadowJar {
    archiveFileName = "runner.jar"
}
