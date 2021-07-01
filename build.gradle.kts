import java.nio.file.Files

plugins {
    id("net.researchgate.release") version "2.8.1"
    java
    application
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))

repositories {
    mavenCentral()
}

val slf4jVersion = "1.7.28"
val logbackVersion = "1.2.3"
val jacksonVersion = "2.9.8"
val guavaVersion = "28.1-jre"
val templarVersion = "0.2.0"
dependencies {
    implementation(group = "org.slf4j", name = "slf4j-api", version = slf4jVersion)
    compileOnly(group = "com.techshroom", name = "jsr305-plus", version = "0.0.1")
    implementation(group = "org.mapdb", name = "mapdb", version = "3.0.7")
    implementation(group = "ch.qos.logback", name = "logback-core", version = logbackVersion)
    implementation(group = "ch.qos.logback", name = "logback-classic", version = logbackVersion)
    implementation(group = "com.techshroom", name = "lettar", version = "0.5.1")
    implementation(group = "com.techshroom.templar", name = "templar-core", version = templarVersion)
    implementation(group = "com.techshroom.templar", name = "templar-codec-jackson", version = templarVersion)

    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jacksonVersion)
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jacksonVersion)
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-core", version = jacksonVersion)
    implementation(group = "com.fasterxml.jackson.datatype", name = "jackson-datatype-jsr310", version = jacksonVersion)
    implementation(group = "com.fasterxml.jackson.datatype", name = "jackson-datatype-jdk8", version = jacksonVersion)
    implementation(group = "com.fasterxml.jackson.datatype", name = "jackson-datatype-guava", version = jacksonVersion)

    implementation(group = "org.apache.velocity", name = "velocity-engine-core", version = "2.1")

    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)

    implementation(group = "org.openjfx", name = "javafx-base", version = "12.0.2")
    listOf("mac", "win", "linux").forEach {
        implementation(group = "org.openjfx", name = "javafx-base", version = "12.0.2", classifier = it)
    }

    implementation(group = "com.techshroom", name = "greenish-jungle", version = "0.0.3")

    compileOnly(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc6")
    annotationProcessor(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc6")
    compileOnly(group = "com.google.auto.value", name = "auto-value-annotations", version = "1.6.6")
    annotationProcessor(group = "com.google.auto.value", name = "auto-value", version = "1.6.6")

    testImplementation(group = "junit", name = "junit", version = "4.12")
}

application.mainClass.set("net.octyl.ytmp3.YoutubeMp3Server")

tasks.run.configure {
    environment("YTMP3_HOST", "localhost")
    environment("YTMP3_PORT", "9000")
}

// Super exciting! We"re doing ES6 transpilation!
val destDirBase = project.file("$buildDir/transpiled")
val transpileJavascript by tasks.registering {
    val sourceDir = file("transpile-source/javascript")
    val destinationDir = destDirBase.resolve("javascript")

    inputs.files("package.json", ".babelrc")
    inputs.dir(sourceDir)

    outputs.dir(destinationDir)

    doLast {
        project.exec {
            executable = project.file("./node_modules/.bin/babel").absolutePath
            args = listOf("-d", destinationDir, sourceDir).map { it.toString() }
        }
    }
}
val transpileScss by tasks.registering {
    val sourceDir = file("transpile-source/scss")
    val destinationDir = destDirBase.resolve("stylesheets")

    inputs.files("package.json")
    inputs.dir(sourceDir)

    outputs.dir(destinationDir)

    doLast {
        val cssMiddleman = Files.createTempDirectory("yt-mp3-css-middleman").toFile()
        project.exec {
            executable = project.file("./node_modules/.bin/sass").absolutePath
            args = listOf("--style=compressed",
                    "--no-source-map",
                    "--update",
                    "$sourceDir:$cssMiddleman")
        }
        project.exec {
            executable = project.file("./node_modules/.bin/postcss").absolutePath
            args = (listOf("--use", "autoprefixer", "--dir", destinationDir) +
                    project.fileTree(cssMiddleman).files).map { it.toString() }
        }
    }
}

val transpileResources by tasks.registering {
    dependsOn(transpileJavascript, transpileScss)
}

sourceSets.main {
    output.dir(mapOf("builtBy" to "transpileResources"), destDirBase)
}
