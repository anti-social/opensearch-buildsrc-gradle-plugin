import java.util.Properties

object Versions {
    private val versionProps = this::class.java
        .getResourceAsStream("/opensearch-plugin-versions.properties")
        .use {
            Properties().apply {
                load(it)
            }
        }

    val project = versionProps["projectVersion"]!!.toString()
    val opensearch = versionProps["opensearchVersion"]!!.toString()
    val plugin = versionProps["pluginVersion"]!!.toString()
}
