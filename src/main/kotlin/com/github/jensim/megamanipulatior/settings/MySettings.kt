package com.github.jensim.megamanipulatior.settings

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.File

enum class HttpsOverride {
    ALLOW_SELF_SIGNED_CERT,
    ALLOW_ANYTHING,
}

enum class AuthMethod {
    TOKEN,
    USERNAME_PASSWORD,
}

enum class ForkSetting {
    /**
     * Will require write access to the repo
     */
    PLAIN_BRANCH,

    /**
     * When not permitted to push into origin, attempt fork strategy
     */
    LAZY_FORK,

    /**
     * Fork before push, for every repo
     */
    EAGER_FORK,
}

data class MegaManipulatorSettings(
    val concurrency: Int = 5,
    val defaultHttpsOverride: HttpsOverride?,
    val searchHostSettings: Map<String, SearchHostSettingsWrapper>,
) {
    init {
        require(searchHostSettings.isNotEmpty()) {
            """
            |Please add one or search host settings.
            |Available types are ${SearchHostType.values()} 
            |""".trimMargin()
        }
    }

    fun resolveHttpsOverride(searchHostName: String): HttpsOverride? = searchHostSettings[searchHostName]
        ?.settings?.httpsOverride ?: defaultHttpsOverride

    fun resolveHttpsOverride(searchHostName: String, codeHostName: String): HttpsOverride? = searchHostSettings[searchHostName]
        ?.codeHostSettings?.get(codeHostName)?.settings?.httpsOverride ?: defaultHttpsOverride

    // fun resolveSettings(searchHostName: String): SearchHostSettings = TODO()

    fun resolveSettings(repoDir: File): Pair<SearchHostSettings, CodeHostSettings>? {
        val codeHostDir: String = repoDir.parentFile.parentFile.name
        val searchHostDir: String = repoDir.parentFile.parentFile.parentFile.name
        return resolveSettings(searchHostDir, codeHostDir)
    }

    fun resolveSettings(searchHostName: String, codeHostName: String): Pair<SearchHostSettings, CodeHostSettings>? {
        return searchHostSettings[searchHostName]?.settings?.let { first ->
            searchHostSettings[searchHostName]?.codeHostSettings?.get(codeHostName)?.settings?.let { second ->
                Pair(first, second)
            }
        }
    }
}

private fun validateBaseUrl(baseUrl: String) {
    require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
        "baseUrl must start with http:// or https://"
    }
    listOf<Char>('/', '?', '=', '&').forEach {
        require(!baseUrl.endsWith(it)) {
            "baseUrl must not end in '$it'"
        }
    }
}

enum class SearchHostType {
    SOURCEGRAPH
}

data class SearchHostSettingsWrapper(
    val type: SearchHostType,
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes(
        value = [
            JsonSubTypes.Type(value = SourceGraphSettings::class, name = "SOURCEGRAPH"),
        ]
    )
    val settings: SearchHostSettings,
    val codeHostSettings: Map<String, CodeHostSettingsWrapper>,

    ) {
    init {
        require(codeHostSettings.isNotEmpty()) {
            """
            |Please add one or code host settings.
            |Available types are ${CodeHostType.values()} 
            |""".trimMargin()
        }
    }
}

sealed class SearchHostSettings(
    open val baseUrl: String,
    open val httpsOverride: HttpsOverride?,
    open val authMethod: AuthMethod,
    open val username: String?
) {
    fun validate() {
        validateBaseUrl(baseUrl)
        if (authMethod == AuthMethod.USERNAME_PASSWORD) {
            require(!username.isNullOrEmpty()) { "$baseUrl: username is required for auth method USERNAME_PASSWORD" }
        }
    }
}

data class SourceGraphSettings(
    override val baseUrl: String,
    override val httpsOverride: HttpsOverride?,
    override val authMethod: AuthMethod,
    override val username: String?
) : SearchHostSettings(
    baseUrl,
    httpsOverride,
    authMethod,
    username
) {
    init {
        validate()
    }
}

enum class CodeHostType {
    BITBUCKET_SERVER,
    GITHUB,
}

sealed class CodeHostSettings(
    open val baseUrl: String,
    open val clonePattern: String,
    open val httpsOverride: HttpsOverride?,
    open val authMethod: AuthMethod,
    open val username: String?,
    open val forkSetting: ForkSetting,
    open val forkRepoPrefix: String?,
) {
    internal fun validate() {
        validateBaseUrl(baseUrl)
        for (word in listOf("project", "repo")) {
            require(clonePattern.contains("{$word}")) {
                "clonePattern must contain {$word}, try something like ssh://git@bitbucket.example.com/{project}/{repo}.git"
            }
        }
        if (authMethod == AuthMethod.USERNAME_PASSWORD) {
            require(!username.isNullOrEmpty()) { "$baseUrl: username is required for auth method USERNAME_PASSWORD" }
        }
        if (forkSetting != ForkSetting.PLAIN_BRANCH) {
            require(username != null) { "username is required if forkSetting is not ${ForkSetting.PLAIN_BRANCH.name}" }
            require(forkRepoPrefix != null) { "forkRepoPrefix is required if forkSetting is not ${ForkSetting.PLAIN_BRANCH.name}" }
        }
    }

    fun cloneUrl(project: String, repo: String) = clonePattern
        .replace("{project}", project)
        .replace("{repo}", repo)
}

enum class CloneType {
    SSH,
    HTTPS // TODO
}

data class BitBucketSettings(
    override val baseUrl: String,
    override val clonePattern: String,
    override val httpsOverride: HttpsOverride?,
    override val authMethod: AuthMethod,
    override val username: String?,
    override val forkSetting: ForkSetting,
    override val forkRepoPrefix: String?,
) : CodeHostSettings(
    baseUrl,
    clonePattern,
    httpsOverride,
    authMethod,
    username,
    forkSetting,
    forkRepoPrefix
) {
    init {
        validate()
    }
}

data class GitHubSettings(
    override val baseUrl: String,
    override val clonePattern: String,
    override val httpsOverride: HttpsOverride?,
    override val authMethod: AuthMethod,
    override val username: String?,
    override val forkSetting: ForkSetting,
    override val forkRepoPrefix: String?,
) : CodeHostSettings(
    baseUrl,
    clonePattern,
    httpsOverride,
    authMethod,
    username,
    forkSetting,
    forkRepoPrefix,
) {
    init {
        validate()
    }
}

data class CodeHostSettingsWrapper(
    val type: CodeHostType,
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes(
        value = [
            JsonSubTypes.Type(value = BitBucketSettings::class, name = "BITBUCKET_SERVER"),
            JsonSubTypes.Type(value = GitHubSettings::class, name = "GITHUB"),
        ]
    )
    val settings: CodeHostSettings
)