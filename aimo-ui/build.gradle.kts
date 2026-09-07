
plugins {
    id("org.ivcode.gradle-publish").version("0.1-SNAPSHOT")
}

tasks.register<Exec>("npmInstall") {
    group = "build"
    description = "Install npm dependencies in the www directory"
    workingDir = projectDir

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (isWindows) {
        commandLine("npm.cmd", "install")
    } else {
        commandLine("npm", "install")
    }
}

tasks.named<Copy>("processResources") {
    dependsOn("build-resources")
    from("dist") {
        into("static")
    }
}

tasks.register<Exec>("build-resources") {
    group = "build"
    description = "Run npm run build in the www directory"
    dependsOn("npmInstall")
    workingDir = projectDir

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (isWindows) {
        commandLine("npm.cmd", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
}
