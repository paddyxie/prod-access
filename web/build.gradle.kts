// React + MUI single-page app. `npm run build` type-checks, lints (UI conventions included) and bundles into dist/,
// which the control app serves. A convention violation therefore fails the Gradle build.
import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

node {
    version = libs.versions.nodejs
    download = true // the same Node everywhere, including the Docker build
    npmInstallCommand = "ci"
}

val sources = listOf("src", "index.html", "package.json", "package-lock.json", "tsconfig.json", "vite.config.ts", "eslint.config.js")

val bundle = tasks.register<NpmTask>("bundle") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "build")
    inputs.files(sources)
    outputs.dir("dist")
}

tasks.assemble { dependsOn(bundle) }

configurations.consumable("dist")

artifacts {
    add("dist", layout.projectDirectory.dir("dist")) { builtBy(bundle) }
}
