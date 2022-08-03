package com.github.jensim.megamanipulator.actions.search.github

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.http.HttpClientProvider
import com.github.jensim.megamanipulator.settings.SerializationHolder
import com.github.jensim.megamanipulator.settings.types.searchhost.GithubSearchSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GitHubSearchClientTest {

    private val clientProviderMockk: HttpClientProvider = mockk()
    private val notificationsOperatorMockk: NotificationsOperator = mockk {
        every { show(any(), any(), any()) } returns Unit
    }
    private val client = GitHubSearchClient(mockk(), clientProviderMockk, notificationsOperatorMockk)

    @ValueSource(
        strings = [
            "https://github.com/search?q=org%3Amega-manipulator+foo&type=code",
            "https://github.com/search?q=org%3Amega-manipulator+search&type=commits",
            "https://github.com/search?q=org%3Amega-manipulator+mega-manipulator&type=repositories",
        ]
    )
    @ParameterizedTest
    fun search(search: String) = runBlocking {
        every { clientProviderMockk.getClient(any(), any()) } returns HttpClient(Apache) {
            install(JsonFeature) {
                this.serializer = JacksonSerializer(jackson = SerializationHolder.readable)
            }
            /*install(ContentNegotiation) {
                jackson {
                    confReadable()
                }
            }*/
        }
        val response: Set<SearchResult> = client.search("Blaha", GithubSearchSettings("jensim"), search)

        assertThat(response, hasItem(SearchResult("mega-manipulator", "mega-manipulator", "github.com", "Blaha")))
    }
}
