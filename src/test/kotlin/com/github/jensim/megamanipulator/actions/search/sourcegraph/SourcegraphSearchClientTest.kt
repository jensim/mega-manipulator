package com.github.jensim.megamanipulator.actions.search.sourcegraph

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.http.HttpClientProvider
import com.github.jensim.megamanipulator.settings.SettingsFileOperator
import com.github.jensim.megamanipulator.settings.types.CodeHostSettings.GitHubSettings
import com.github.jensim.megamanipulator.settings.types.ForkSetting.PLAIN_BRANCH
import com.github.jensim.megamanipulator.settings.types.MegaManipulatorSettings
import com.github.jensim.megamanipulator.settings.types.SearchHostSettings.SourceGraphSettings
import com.github.jensim.megamanipulator.test.EnvHelper
import com.github.jensim.megamanipulator.test.EnvHelper.EnvProperty.GITHUB_USERNAME
import com.github.jensim.megamanipulator.test.EnvHelper.EnvProperty.SRC_COM_ACCESS_TOKEN
import com.github.jensim.megamanipulator.test.TestPasswordOperator
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SourcegraphSearchClientTest {

    private val project: Project = mockk()
    private val envHelper = EnvHelper()
    private val password = envHelper.resolve(SRC_COM_ACCESS_TOKEN)!!
    private val codeHostName = "github.com"
    private val sourceGraphSettings = SourceGraphSettings(
        baseUrl = "https://sourcegraph.com",
        codeHostSettings = mapOf(
            codeHostName to GitHubSettings(
                username = envHelper.resolve(GITHUB_USERNAME)!!,
                forkSetting = PLAIN_BRANCH,
            )
        )
    )
    private val searchHostName = "sourcegraph.com"
    private val settings = MegaManipulatorSettings(
        searchHostSettings = mapOf(
            searchHostName to sourceGraphSettings
        )
    )
    private val settingsMock: SettingsFileOperator = mockk {
        every { readSettings() } returns settings
    }
    private val passwordsOperator = TestPasswordOperator(mapOf("token" to sourceGraphSettings.baseUrl to password))
    private val notificationsMock: NotificationsOperator = mockk()

    private val clientProvider = HttpClientProvider(
        project = mockk(),
        settingsFileOperator = settingsMock,
        passwordsOperator = passwordsOperator,
        notificationsOperator = notificationsMock
    )

    private val sourcegraphSearchClient = SourcegraphSearchClient(
        project = project,
        httpClientProvider = clientProvider,
        notificationsOperator = notificationsMock,
    )

    @Test
    internal fun `search for sourcegraph itself`() {
        val project = "sourcegraph"
        val repo = "sourcegraph"
        val result: Set<SearchResult> = runBlocking {
            sourcegraphSearchClient.search(searchHostName, sourceGraphSettings, "repo:$codeHostName/$project/$repo$ file:.go$ foo")
        }

        assertEquals(
            result,
            setOf(
                SearchResult(
                    searchHostName = searchHostName,
                    codeHostName = codeHostName,
                    project = project,
                    repo = repo,
                )
            )
        )
    }

    @Test
    internal fun `validate token test`() {
        val result = runBlocking {
            sourcegraphSearchClient.validateToken(searchHostName, sourceGraphSettings)
        }

        assertThat(result, equalTo("OK"))
    }
}
