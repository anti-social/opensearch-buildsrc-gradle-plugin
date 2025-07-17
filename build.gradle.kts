import java.io.IOException
import java.util.Properties
import kotlin.io.path.div
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.api.Git

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    }
}

plugins {
    `kotlin-dsl`
    idea
}

val defaultOpensearchVersion = readVersion("opensearch-default.version")
val nebulaVersion = "12.0.0"

val fallbackPluginVersion = "v0.0.0-os$defaultOpensearchVersion"
val pluginVersion = try {
    val git = Git.open(project.rootDir.getParentFile().resolve(".git"))
    git.describe().setTags(true).setMatch("v*-os*").call() ?: fallbackPluginVersion
} catch (e: IOException) {
    fallbackPluginVersion
}

class GitDescribe(val describe: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups =
        "v(?<plugin>${VERSION_REGEX})-os(?<opensearch>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
            .matchEntire(describe)!!
            .groups

    val plugin = matchedGroups["plugin"]!!.value
    val opensearch = matchedGroups["opensearch"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun opensearchVersion() = if (hasProperty("opensearchVersion")) {
        property("opensearchVersion")
    } else {
        // When adopting to new Opensearch version
        // create `buildSrc/opensearch.version` file so IDE can fetch correct version of Opensearch
        readVersion("../opensearch.version") ?: opensearch
    }

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
val describe = GitDescribe(pluginVersion)

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
            put("tag", describe.describe)
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
