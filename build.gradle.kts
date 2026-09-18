plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.geoviewerplus"
version = "0.6.0"

repositories {
    mavenCentral()
}

dependencies {
    // The SDK exposes DataGrip's content modules as separate jars. They are provided
    // by DataGrip at runtime, so keep them compile-only for this plugin.
    compileOnly(fileTree("tools/datagrip-sdk/plugins/DatabaseTools/lib/modules") { include("*.jar") })
    compileOnly(fileTree("tools/datagrip-sdk/plugins/grid-core-plugin/lib/modules") { include("*.jar") })
    compileOnly(fileTree("tools/datagrip-sdk/plugins/jcef-plugin/lib/modules") { include("*.jar") })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

intellij {
    localPath.set(rootProject.file("tools/datagrip-sdk").absolutePath)
    plugins.set(listOf("DatabaseTools", "intellij.grid.core.plugin", "com.intellij.modules.jcef"))
    instrumentCode.set(false)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("262")
        untilBuild.set("262.*")
    }

    withType<JavaCompile> {
        options.release.set(17)
        options.encoding = "UTF-8"
    }
}
