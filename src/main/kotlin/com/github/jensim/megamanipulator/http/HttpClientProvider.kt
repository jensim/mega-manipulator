package com.github.jensim.megamanipulator.http

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.project.lazyService
import com.github.jensim.megamanipulator.settings.SettingsFileOperator
import com.github.jensim.megamanipulator.settings.passwords.PasswordsOperator
import com.github.jensim.megamanipulator.settings.types.AuthMethod
import com.github.jensim.megamanipulator.settings.types.AuthMethod.JUST_TOKEN
import com.github.jensim.megamanipulator.settings.types.AuthMethod.NONE
import com.github.jensim.megamanipulator.settings.types.AuthMethod.USERNAME_TOKEN
import com.github.jensim.megamanipulator.settings.types.CodeHostSettings
import com.github.jensim.megamanipulator.settings.types.HostWithAuth
import com.github.jensim.megamanipulator.settings.types.HttpsOverride
import com.github.jensim.megamanipulator.settings.types.SearchHostSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.headers
import kotlinx.serialization.json.Json
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.ssl.SSLContextBuilder
import java.security.cert.X509Certificate

class HttpClientProvider @NonInjectable constructor(
    project: Project,
    settingsFileOperator: SettingsFileOperator?,
    passwordsOperator: PasswordsOperator?,
    notificationsOperator: NotificationsOperator?,
) {

    private val notificationsOperator: NotificationsOperator by lazyService(project, notificationsOperator)
    private val settingsFileOperator: SettingsFileOperator by lazyService(project, settingsFileOperator)
    private val passwordsOperator: PasswordsOperator by lazyService(project, passwordsOperator)

    constructor(project: Project) : this(
        project = project,
        settingsFileOperator = null,
        passwordsOperator = null,
        notificationsOperator = null
    )

    private class TrustAnythingStrategy : TrustStrategy {
        override fun isTrusted(p0: Array<out X509Certificate>?, p1: String?): Boolean = true
    }

    private fun bakeClient(installs: HttpClientConfig<ApacheEngineConfig>.() -> Unit): HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                }
            )
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
        installs()
    }

    private fun HttpClientConfig<ApacheEngineConfig>.trustSelfSignedClient() {
        engine {
            customizeClient {
                setSSLContext(
                    SSLContextBuilder
                        .create()
                        .loadTrustMaterial(TrustSelfSignedStrategy())
                        .build()
                )
                setSSLHostnameVerifier(NoopHostnameVerifier())
            }
        }
    }

    private fun HttpClientConfig<ApacheEngineConfig>.trustAnyClient() {
        engine {
            customizeClient {
                setSSLContext(
                    SSLContextBuilder
                        .create()
                        .loadTrustMaterial(TrustAnythingStrategy())
                        .build()
                )
                setSSLHostnameVerifier(NoopHostnameVerifier())
            }
        }
    }

    fun getClient(searchHostName: String, settings: SearchHostSettings): HttpClient {
        val httpsOverride: HttpsOverride? = settingsFileOperator.readSettings()?.resolveHttpsOverride(searchHostName)
        val password: String = getPassword(settings.authMethod, settings.baseUrl, settings.username)
        return getClient(httpsOverride, settings, password)
    }

    fun getClient(searchHostName: String, codeHostName: String, settings: CodeHostSettings): HttpClient {
        val httpsOverride: HttpsOverride? =
            settingsFileOperator.readSettings()?.resolveHttpsOverride(searchHostName, codeHostName)
        val password: String = getPassword(settings.authMethod, settings.baseUrl, settings.username ?: "token")
        return getClient(httpsOverride, settings, password)
    }

    private fun getPassword(authMethod: AuthMethod, baseUrl: String, username: String?) = try {
        when (authMethod) {
            USERNAME_TOKEN -> passwordsOperator.getPassword(username!!, baseUrl)
            JUST_TOKEN -> passwordsOperator.getPassword(username ?: "token", baseUrl)
            NONE -> ""
        }!!
    } catch (e: Exception) {
        notificationsOperator.show(
            title = "Password not set",
            body = "Password was not set for $authMethod: $username@$baseUrl",
            type = NotificationType.WARNING
        )
        throw e
    }

    fun getClient(httpsOverride: HttpsOverride?, auth: HostWithAuth, password: String): HttpClient {
        return bakeClient {

            install(HttpTimeout) {
                connectTimeoutMillis = 1_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            when (httpsOverride) {
                HttpsOverride.ALLOW_SELF_SIGNED_CERT -> trustSelfSignedClient()
                HttpsOverride.ALLOW_ANYTHING -> trustAnyClient()
            }
            auth.getAuthHeaderValue(password)?.let {
                installBasicAuth(it)
            }
        }
    }

    private fun HttpClientConfig<ApacheEngineConfig>.installBasicAuth(headerValue: String) {
        defaultRequest {
            headers {
                append("Authorization", headerValue)
            }
        }
    }
}
