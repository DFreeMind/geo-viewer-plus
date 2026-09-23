import java.security.MessageDigest
import java.net.URI

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

data class WebAsset(val fileName: String, val url: String, val sha256: String)
val webAssets = listOf(
    WebAsset("leaflet.css", "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css", "a7837102824184820dfa198d1ebcd109ff6d0ff9a2672a074b9a1b4d147d04c6"),
    WebAsset("leaflet.js", "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", "db49d009c841f5ca34a888c96511ae936fd9f5533e90d8b2c4d57596f4e5641a"),
    WebAsset("leaflet-vectorgrid.js", "https://unpkg.com/leaflet.vectorgrid@1.3.0/dist/Leaflet.VectorGrid.bundled.js", "144c59f4da8a82a8d85a9c18c1a8bb62fdaec26cb2f624228e7f70bcd61ba061")
)
val bundledWebAssets = layout.buildDirectory.dir("generated/geo-viewer-plus-web-assets")
val mapPreviewOutput = layout.buildDirectory.file("preview/geo-viewer-plus-preview.html")
// Use tools/datagrip-sdk by default. Select a specific SDK for compatibility checks with
// -PdataGripSdkPath=tools/datagrip-sdk-231, for example.
val dataGripSdkDir = providers.gradleProperty("dataGripSdkPath")
    .map(::file)
    .orElse(rootProject.file("tools/datagrip-sdk"))
    .get()
val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion")
    .map(String::toInt)
    .orElse(21)
    .get()
val hasSplitGridPlugins = dataGripSdkDir.resolve("plugins/grid-impl").isDirectory
val compatibilityIdeBuilds = listOf(
    "DB-231.9011.35",
    "DB-232.10203.8",
    "DB-233.14015.137",
    "DB-241.19072.24",
    "DB-251.29188.65",
    "DB-253.33813.51",
    "DB-261.26222.86",
    "DB-262.10315.132",
)

val downloadWebAssets by tasks.registering {
    outputs.dir(bundledWebAssets)
    doLast {
        val output = bundledWebAssets.get().asFile
        output.mkdirs()
        for (asset in webAssets) {
            val target = output.resolve(asset.fileName)
            URI(asset.url).toURL().openStream().use { input -> target.outputStream().use(input::copyTo) }
            val digest = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            check(digest == asset.sha256) { "Checksum mismatch for ${asset.fileName}" }
        }
    }
}

val buildMapPreview by tasks.registering {
    group = "application"
    description = "Builds a standalone Geo Viewer Plus HTML preview with sample data."
    dependsOn(downloadWebAssets)
    val templateFile = layout.projectDirectory.file("src/main/resources/geo-viewer-plus.html")
    val bootstrapFile = layout.projectDirectory.file("src/preview/geo-viewer-plus-preview.js")
    inputs.files(templateFile, bootstrapFile)
    inputs.dir(bundledWebAssets)
    outputs.file(mapPreviewOutput)
    doLast {
        val assets = bundledWebAssets.get().asFile
        val html = templateFile.asFile.readText()
                .replace("__LEAFLET_CSS__", assets.resolve("leaflet.css").readText())
                .replace("__LEAFLET_JS__", assets.resolve("leaflet.js").readText())
                .replace("__LEAFLET_VECTORGRID_JS__", assets.resolve("leaflet-vectorgrid.js").readText())
                .replace("__GEO_VIEWER_READY__", bootstrapFile.asFile.readText())
        val output = mapPreviewOutput.get().asFile
        output.parentFile.mkdirs()
        output.writeText(html)
        logger.lifecycle("Standalone map preview: ${output.absolutePath}")
    }
}

sourceSets.main {
    resources.srcDir(bundledWebAssets)
}

group = "cn.duqimeng.geo-viewer-plus"
version = providers.gradleProperty("pluginVersion").get()

java {
    // Compile with the selected JDK and retain Java 17 bytecode for the DataGrip 2023.1 baseline.
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
}

repositories {
    mavenCentral()
}

dependencies {
    // The SDK exposes DataGrip's content modules as separate jars. They are provided
    // by DataGrip at runtime, so keep them compile-only for this plugin.
    compileOnly(fileTree(dataGripSdkDir.resolve("plugins/DatabaseTools/lib")) { include("*.jar") })
    compileOnly(fileTree(dataGripSdkDir.resolve("plugins/DatabaseTools/lib/modules")) { include("*.jar") })
    compileOnly(fileTree(dataGripSdkDir.resolve("plugins/grid-plugin/lib/modules")) { include("*.jar") })
    compileOnly(fileTree(dataGripSdkDir.resolve("plugins/grid-impl/lib")) { include("*.jar") })
    compileOnly(fileTree(dataGripSdkDir.resolve("plugins/grid-core-impl/lib")) { include("*.jar") })
    // JCEF is packaged in the platform libraries of supported DataGrip builds.
    compileOnly(fileTree(dataGripSdkDir.resolve("lib")) { include("*.jar") })
    compileOnly(fileTree(dataGripSdkDir.resolve("lib/modules")) { include("*.jar") })
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

intellij {
    localPath.set(dataGripSdkDir.absolutePath)
    // The implementation uses DataGrid classes provided by Data Editor UI.
    val gridPlugins = if (hasSplitGridPlugins) {
        listOf("intellij.grid.impl", "intellij.grid.core.impl")
    } else {
        listOf("intellij.grid.plugin")
    }
    plugins.set(listOf("DatabaseTools") + gridPlugins)
    instrumentCode.set(false)
}

tasks {
    processResources {
        dependsOn(downloadWebAssets)
    }
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("262.*")
        val releaseNotesFile = providers.gradleProperty("releaseNotesFile")
        if (releaseNotesFile.isPresent) {
            changeNotes.set(file(releaseNotesFile.get()).readText())
        }
    }

    publishPlugin {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
        channels = listOf("default")
    }

    runPluginVerifier {
        ideVersions.set(compatibilityIdeBuilds)
        localPaths.set(
            listOf("tools/datagrip-sdk-243")
                .map { rootProject.file(it) }
                .filter { it.isDirectory }
        )
    }

    withType<JavaCompile> {
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
