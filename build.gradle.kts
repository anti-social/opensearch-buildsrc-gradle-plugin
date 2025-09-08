import java.io.IOException
import java.util.Properties
import kotlin.io.path.div

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    `kotlin-dsl`
    idea
}

val nebulaVersion = "12.0.0"

val defaultOpensearchVersion = readVersion("../opensearch.version") ?: readVersion("opensearch-default.version")
val fallbackTagVersion = "v0.0.0-os$defaultOpensearchVersion"

class GitDescribe(val tagVersion: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups =
        "v(?<plugin>${VERSION_REGEX})-os(?<opensearch>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
            .matchEntire(tagVersion)!!
            .groups

    val plugin = matchedGroups["plugin"]!!.value
    val opensearch = matchedGroups["opensearch"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun opensearchVersion() = opensearch

    fun pluginVersion() = buildString {
        append(plugin)
        if (abbrev != null) {
            append("-$abbrev")
        }
    }

    fun projectVersion() = buildString {
        append("$plugin-os${opensearchVersion()}")
        if (abbrev != null) {
            append("-$abbrev")
        }
    }
}
val describe = GitDescribe(
    project.findProperty("tagVersion")?.toString() ?: fallbackTagVersion
)

val generatedResourcesDir = layout.buildDirectory.map {
    it.dir("generated-resources").dir("main")
}

sourceSets {
    main {
        output.dir(mapOf("builtBy" to "generateVersionProperties"), generatedResourcesDir)
    }
}

tasks.create("generateVersionProperties") {
    outputs.dir(generatedResourcesDir)
    doLast {
        val versionProps = Properties().apply {
            put("tag", describe.tagVersion)
            put("projectVersion", describe.projectVersion())
            put("pluginVersion", describe.pluginVersion())
            put("opensearchVersion", describe.opensearchVersion())
        }
        generatedResourcesDir.get()
            .file("opensearch-plugin-versions.properties")
            .asFile
            .writer()
            .use {
               versionProps.store(it, null)
            }
    }
}

repositories {
    mavenLocal()
    gradlePluginPortal()
}

// kotlinDslPluginOptions {
//     experimentalWarning.set(false)
// }

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}

dependencies {
    // implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
    implementation("org.opensearch.gradle:build-tools:${describe.opensearchVersion()}")
    implementation("com.netflix.nebula:gradle-ospackage-plugin:${nebulaVersion}")
    constraints {
        // Due to end of jCenter repository
        implementation("com.avast.gradle:gradle-docker-compose-plugin:0.14.2")
        implementation("com.netflix.nebula:nebula-core:4.0.1")
    }
}

// Utils

fun readVersion(fileName: String): String? {
    project.projectDir.toPath().resolve(fileName).toFile().let {
        if (it.exists()) {
            val opensearchVersion = it.readText().trim()
            if (!opensearchVersion.startsWith('#')) {
                return opensearchVersion
            }
            return null
        }
        return null
    }
}
