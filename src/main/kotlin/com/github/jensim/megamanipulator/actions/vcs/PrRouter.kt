package com.github.jensim.megamanipulator.actions.vcs

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.actions.vcs.bitbucketserver.BitbucketServerClient
import com.github.jensim.megamanipulator.actions.vcs.githubcom.GithubComClient
import com.github.jensim.megamanipulator.settings.CodeHostSettings
import com.github.jensim.megamanipulator.settings.CodeHostSettings.BitBucketSettings
import com.github.jensim.megamanipulator.settings.CodeHostSettings.GitHubSettings
import com.github.jensim.megamanipulator.settings.SettingsFileOperator
import com.intellij.notification.NotificationType.WARNING
import java.util.concurrent.atomic.AtomicLong

@SuppressWarnings("TooManyFunctions")
class PrRouter(
    private val settingsFileOperator: SettingsFileOperator,
    private val bitbucketServerClient: BitbucketServerClient,
    private val githubComClient: GithubComClient,
    private val notificationsOperator: NotificationsOperator,
) {

    companion object {

        val instance by lazy {
            PrRouter(
                settingsFileOperator = SettingsFileOperator.instance,
                bitbucketServerClient = BitbucketServerClient.instance,
                githubComClient = GithubComClient.instance,
                notificationsOperator = NotificationsOperator.instance
            )
        }
    }

    private val lastSettingsWarning = AtomicLong()

    private fun resolve(searchHost: String, codeHost: String): CodeHostSettings? {
        val resolved = settingsFileOperator.readSettings()
            ?.searchHostSettings?.get(searchHost)?.codeHostSettings?.get(codeHost)
        if (resolved == null) {
            val last = lastSettingsWarning.get()
            val current = System.currentTimeMillis()
            if (last < (current - 100)) {
                lastSettingsWarning.set(current)
                notificationsOperator.show(
                    title = "Missing config",
                    body = "Failed finding config for '$searchHost'/'$codeHost'",
                    type = WARNING
                )
            }
        }
        return resolved
    }

    suspend fun addDefaultReviewers(pullRequest: PullRequestWrapper): PullRequestWrapper? {
        val settings = resolve(pullRequest.searchHostName(), pullRequest.codeHostName())
        return when {
            settings is BitBucketSettings && pullRequest is BitBucketPullRequestWrapper -> bitbucketServerClient.addDefaultReviewers(settings, pullRequest)
            settings is GitHubSettings && pullRequest is GithubComPullRequestWrapper -> githubComClient.addDefaultReviewers(settings, pullRequest)
            settings == null -> null
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }

    suspend fun createPr(title: String, description: String, repo: SearchResult): PullRequestWrapper? {
        return when (val settings = resolve(repo.searchHostName, repo.codeHostName)) {
            is BitBucketSettings -> bitbucketServerClient.createPr(title, description, settings, repo)
            is GitHubSettings -> githubComClient.createPr(title, description, settings, repo)
            null -> null
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }

    suspend fun createFork(repo: SearchResult): String? {
        return when (val settings = resolve(repo.searchHostName, repo.codeHostName)) {
            is BitBucketSettings -> bitbucketServerClient.createFork(settings, repo)
            is GitHubSettings -> githubComClient.createFork(settings, repo)
            null -> null
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }

    suspend fun updatePr(newTitle: String, newDescription: String, pullRequest: PullRequestWrapper): PullRequestWrapper? {
        val settings = resolve(pullRequest.searchHostName(), pullRequest.codeHostName())
        return when {
            settings is BitBucketSettings && pullRequest is BitBucketPullRequestWrapper -> bitbucketServerClient.updatePr(newTitle, newDescription, settings, pullRequest)
            settings is GitHubSettings && pullRequest is GithubComPullRequestWrapper -> githubComClient.updatePr(newTitle, newDescription, settings, pullRequest)
            settings == null -> null
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }

    suspend fun getAllPrs(searchHost: String, codeHost: String): List<PullRequestWrapper>? {
        return resolve(searchHost, codeHost)?.let {
            when (it) {
                is BitBucketSettings -> bitbucketServerClient.getAllPrs(searchHost, codeHost, it)
                is GitHubSettings -> githubComClient.getAllPrs(searchHost, codeHost, it)
            }
        }
    }

    suspend fun closePr(dropForkOrBranch: Boolean, pullRequest: PullRequestWrapper) {
        val settings = resolve(pullRequest.searchHostName(), pullRequest.codeHostName())
        when {
            settings is BitBucketSettings && pullRequest is BitBucketPullRequestWrapper -> bitbucketServerClient.closePr(dropForkOrBranch, settings, pullRequest)
            settings is GitHubSettings && pullRequest is GithubComPullRequestWrapper -> githubComClient.closePr(dropForkOrBranch, settings, pullRequest)
            settings == null -> Unit
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }

    suspend fun getPrivateForkReposWithoutPRs(searchHost: String, codeHost: String): List<RepoWrapper>? {
        return resolve(searchHost, codeHost)?.let {
            when (it) {
                is BitBucketSettings -> bitbucketServerClient.getPrivateForkReposWithoutPRs(searchHost, codeHost, it)
                is GitHubSettings -> githubComClient.getPrivateForkReposWithoutPRs(searchHost, codeHost, it)
            }
        }
    }

    suspend fun deletePrivateRepo(fork: RepoWrapper) {
        resolve(fork.getSearchHost(), fork.getCodeHost())?.let { settings ->
            when {
                settings is BitBucketSettings && fork is BitBucketRepoWrapping -> bitbucketServerClient.deletePrivateRepo(fork, settings)
                settings is GitHubSettings && fork is GithubComRepoWrapping -> githubComClient.deletePrivateRepo(fork, settings)
                else -> throw IllegalArgumentException("Unable to match config correctly")
            }
        }
    }

    suspend fun getRepo(searchResult: SearchResult): RepoWrapper? {
        val settings = resolve(searchResult.searchHostName, searchResult.codeHostName)
        return settings?.let {
            when (it) {
                is BitBucketSettings -> bitbucketServerClient.getRepo(searchResult, it)
                is GitHubSettings -> githubComClient.getRepo(searchResult, it)
            }
        }
    }

    suspend fun commentPR(comment: String, pullRequest: PullRequestWrapper) {
        val settings = resolve(pullRequest.searchHostName(), pullRequest.codeHostName())
        when {
            settings is BitBucketSettings && pullRequest is BitBucketPullRequestWrapper -> bitbucketServerClient.commentPR(comment, pullRequest, settings)
            settings is GitHubSettings && pullRequest is GithubComPullRequestWrapper -> githubComClient.commentPR(comment, pullRequest, settings)
            settings == null -> Unit
            else -> throw IllegalArgumentException("Unable to match config correctly")
        }
    }
}
