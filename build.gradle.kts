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

dependencies {
    compileOnly(libs.auto.service.annotations)
    compileOnly(libs.auto.value.annotations)
    annotationProcessor(libs.auto.service.processor)
    annotationProcessor(libs.auto.value.processor)
    compileOnly(libs.checkerframework.qual)

    implementation(libs.mapdb)

    implementation(platform(libs.log4j.bom))
    implementation(libs.log4j.api)
    runtimeOnly(libs.log4j.core)

    implementation(libs.lettar)
    implementation(libs.templar.core)
    implementation(libs.templar.codec.jackson)

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.guava)

    implementation(libs.velocity)

    implementation(libs.guava)

    implementation(libs.javafx.base)
    listOf("mac", "win", "linux").forEach {
        implementation(variantOf(libs.javafx.base) { classifier(it) })
    }

    implementation(libs.greenishJungle)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
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
