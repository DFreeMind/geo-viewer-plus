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

sourceSets.main {
    resources.srcDir(bundledWebAssets)
}

group = "cn.duqimeng.geo-viewer-plus"
version = providers.gradleProperty("pluginVersion").get()

java {
    // DataGrip 2026.1 ships Java 25 class files. Compile against its SDK while
    // retaining Java 17 bytecode for the plugin's declared compatibility range.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
}

dependencies {
    // The SDK exposes DataGrip's content modules as separate jars. They are provided
    // by DataGrip at runtime, so keep them compile-only for this plugin.
    compileOnly(fileTree("tools/datagrip-sdk/plugins/DatabaseTools/lib/modules") { include("*.jar") })
    compileOnly(fileTree("tools/datagrip-sdk/plugins/grid-plugin/lib/modules") { include("*.jar") })
    // The DataGrid API lives in the separately bundled grid-core plugin.
    compileOnly(fileTree("tools/datagrip-sdk/plugins/grid-core-plugin/lib/modules") { include("*.jar") })
    // JCEF is packaged as a bundled plugin in current DataGrip builds, not in lib/.
    compileOnly(fileTree("tools/datagrip-sdk/plugins/jcef-plugin/lib/modules") { include("*.jar") })
    compileOnly(fileTree("tools/datagrip-sdk/lib") { include("*.jar") })
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

intellij {
    localPath.set(rootProject.file("tools/datagrip-sdk").absolutePath)
    plugins.set(listOf("DatabaseTools", "intellij.grid.plugin", "jcef-plugin"))
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
        sinceBuild.set("261")
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

    withType<JavaCompile> {
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
