package com.github.jensim.megamanipulator.actions.vcs.bitbucketserver

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.localrepo.LocalRepoOperator
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.actions.vcs.BitBucketPullRequestWrapper
import com.github.jensim.megamanipulator.actions.vcs.PullRequest
import com.github.jensim.megamanipulator.http.HttpClientProvider
import com.github.jensim.megamanipulator.settings.BitBucketSettings
import com.intellij.notification.NotificationType
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put

@SuppressWarnings("TooManyFunctions")
object BitbucketPrReceiver {

    private suspend fun getDefaultReviewers(client: HttpClient, settings: BitBucketSettings, pullRequest: BitBucketPullRequestWrapper): List<BitBucketUser> {
        return client.get("${settings.baseUrl}/rest/default-reviewers/1.0/projects/${pullRequest.project()}/repos/${pullRequest.repo()}/reviewers?sourceRepoId=${pullRequest.bitbucketPR.fromRef.repository.id}&targetRepoId=${pullRequest.bitbucketPR.toRef.repository.id}&sourceRefId=${pullRequest.bitbucketPR.fromRef.id}&targetRefId=${pullRequest.bitbucketPR.toRef.id}")
    }

    suspend fun addDefaultReviewers(settings: BitBucketSettings, pullRequest: BitBucketPullRequestWrapper): PullRequest {
        val client: HttpClient = HttpClientProvider.getClient(pullRequest.searchHostName(), pullRequest.codeHostName(), settings)
        val defaultReviewers = getDefaultReviewers(client, settings, pullRequest).map { BitBucketParticipant(BitBucketUser(it.name)) }
        val all = (pullRequest.bitbucketPR.reviewers + defaultReviewers).distinct()
        val bbPR2 = pullRequest.copy(bitbucketPR = pullRequest.bitbucketPR.copy(reviewers = all))
        return updatePr(client, settings, bbPR2)
    }

    private suspend fun getDefaultReviewers(client: HttpClient, settings: BitBucketSettings, repo: SearchResult, fromBranchRef: String, toBranchRef: String): List<BitBucketUser> {
        val bitBucketRepo: BitBucketRepo = getRepo(client, settings, repo)
        return client.get("${settings.baseUrl}/rest/default-reviewers/1.0/projects/${repo.project}/repos/${repo.repo}/reviewers?sourceRepoId=${bitBucketRepo.id}&targetRepoId=${bitBucketRepo.id}&sourceRefId=$fromBranchRef&targetRefId=$toBranchRef")
    }

    private suspend fun getRepo(client: HttpClient, settings: BitBucketSettings, repo: SearchResult): BitBucketRepo {
        return client.get("${settings.baseUrl}/rest/api/1.0/projects/${repo.project}/repos/${repo.repo}")
    }

    suspend fun createPr(title: String, description: String, settings: BitBucketSettings, repo: SearchResult): PullRequest {
        val client: HttpClient = HttpClientProvider.getClient(repo.searchHostName, repo.codeHostName, settings)

        val defaultBranch = client.get<BitBucketDefaultBranch>("${settings.baseUrl}/rest/api/1.0/projects/${repo.project}/repos/${repo.repo}/default-branch").id
        val localBranch: String = LocalRepoOperator.getBranch(repo)!!
        val reviewers = getDefaultReviewers(client, settings, repo, localBranch, defaultBranch)
            .map { BitBucketParticipant(BitBucketUser(name = it.name)) }
        val pr: BitBucketPullRequest = client.post("${settings.baseUrl}/rest/api/1.0/projects/${repo.project}/repos/${repo.repo}/pull-requests") {
            body = BitBucketPullRequest(
                title = title,
                description = description,
                fromRef = BitBucketBranchRef(
                    id = localBranch,
                    repository = BitBucketRepo(
                        slug = repo.repo,
                        project = BitBucketProject(key = repo.project),
                    )
                ),
                toRef = BitBucketBranchRef(
                    id = defaultBranch,
                    repository = BitBucketRepo(
                        slug = repo.repo,
                        project = BitBucketProject(key = repo.project),
                    )
                ),
                reviewers = reviewers,
            )
        }

        return BitBucketPullRequestWrapper(searchHost = repo.searchHostName, codeHost = repo.codeHostName, pr)
    }

    suspend fun updatePr(settings: BitBucketSettings, pullRequest: BitBucketPullRequestWrapper): PullRequest {
        val client: HttpClient = HttpClientProvider.getClient(pullRequest.searchHostName(), pullRequest.codeHostName(), settings)
        return updatePr(client, settings, pullRequest)
    }

    private suspend fun updatePr(client: HttpClient, settings: BitBucketSettings, pullRequest: BitBucketPullRequestWrapper): PullRequest {
        val pr: BitBucketPullRequest = client.put("${settings.baseUrl}/rest/api/1.0/projects/${pullRequest.project()}/repos/${pullRequest.repo()}/pull-requests/${pullRequest.bitbucketPR.id}") {
            body = pullRequest.bitbucketPR.copy(
                author = null,
            )
        }
        return BitBucketPullRequestWrapper(
            searchHost = pullRequest.searchHost,
            codeHost = pullRequest.codeHost,
            bitbucketPR = pr
        )
    }

    @Suppress("style.LoopWithTooManyJumpStatements")
    suspend fun getAllPrs(searchHostName: String, codeHostName: String, settings: BitBucketSettings): List<PullRequest> {
        val client: HttpClient = HttpClientProvider.getClient(searchHostName, codeHostName, settings)
        val collector = ArrayList<PullRequest>()
        var start = 0L
        while (true) {
            val response: BitBucketPage<BitBucketPullRequest> = try {
                client.get("${settings.baseUrl}/rest/api/1.0/dashboard/pull-requests?state=OPEN&role=AUTHOR&start=$start&limit=100")
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
            response.message?.let {
                println("Message received $it")
            }
            collector.addAll(
                response.values.orEmpty().map {
                    BitBucketPullRequestWrapper(searchHostName, codeHostName, it)
                }
            )
            if (response.isLastPage != false) {
                break
            } else {
                start += response.size ?: 0
            }
        }
        return collector
    }

    suspend fun closePr(settings: BitBucketSettings, pullRequest: BitBucketPullRequestWrapper): PullRequest {
        val client = HttpClientProvider.getClient(pullRequest.searchHostName(), pullRequest.codeHostName(), settings)
        try {
            val urlString = "${settings.baseUrl}/rest/api/1.0/projects/${pullRequest.project()}/repos/${pullRequest.repo()}/pull-requests/${pullRequest.bitbucketPR.id}/decline?version=${pullRequest.bitbucketPR.version}"
            client.post<Unit>(urlString) {
                body = emptyMap<String, String>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationsOperator.show(
                title = "Failed declining PR",
                body = "${e.message}",
                type = NotificationType.ERROR
            )
        }
        return pullRequest
    }

    suspend fun createFork(settings: BitBucketSettings, repo: SearchResult): String? {
        val client: HttpClient = HttpClientProvider.getClient(repo.searchHostName, repo.codeHostName, settings)
        val bbRepo: BitBucketRepo = try {
            // If repo already exists..
            client.get("${settings.baseUrl}/rest/api/1.0/users/~${settings.username!!}/repos/${settings.forkRepoPrefix}${repo.repo}")
        } catch (e: Exception) {
            // Repo does not exist - lets fork
            client.post("${settings.baseUrl}/rest/api/1.0/projects/${repo.project}/repos/${repo.repo}") {
                body = BitBucketForkRequest(
                    slug = "${settings.forkRepoPrefix}${repo.repo}",
                    project = BitBucketProjectRequest(key = "~${settings.username}")
                )
            }
        }
        return bbRepo.links?.clone?.firstOrNull { it.name == "ssh" }?.href
    }

    /**
     * Get all the repos prefixed with the fork-repo-prefix, that do not have open outgoing PRs connected to them
     */
    suspend fun getPrivateForkReposWithoutPRs(searchHostName: String, codeHostName: String, settings: BitBucketSettings): List<BitBucketRepo> {
        val client: HttpClient = HttpClientProvider.getClient(searchHostName, codeHostName, settings)
        var start = 0
        val collector = HashSet<BitBucketRepo>()
        while (true) {
            val page: BitBucketPage<BitBucketRepo> = client.get("${settings.baseUrl}/rest/api/1.0/users/~${settings.username!!}/repos?start=$start")
            page.values.orEmpty()
                .filter { it.slug.startsWith(settings.forkRepoPrefix!!) }
                .forEach { collector.add(it) }
            if (page.isLastPage != false) break
            start += page.size ?: 0
        }
        return collector.filter {
            val page: BitBucketPage<BitBucketPullRequest> = client.get("${settings.baseUrl}/rest/api/1.0/projects/${it.project?.key}/repos/${it.slug}/pull-requests?direction=OUTGOING&state=OPEN")
            page.size == 0
        }
    }

    /**
     * Schedule a repo for deletion, input should be a repo from getPrivateForkReposWithoutPRs
     * @see getPrivateForkReposWithoutPRs
     */
    suspend fun deletePrivateRepo(searchHostName: String, codeHostName: String, settings: BitBucketSettings, repo: String) {
        val client: HttpClient = HttpClientProvider.getClient(searchHostName, codeHostName, settings)
        client.delete<BitBucketMessage>("${settings.baseUrl}/rest/api/1.0/users/~${settings.username}/repos/${settings.forkRepoPrefix}$repo") {
            body = emptyMap<String, String>()
        }
    }

    /*
    pull requests

        dashboard:
        https://docs.atlassian.com/bitbucket-server/rest/7.10.0/bitbucket-rest.html#idp97

        per repo
        https://docs.atlassian.com/bitbucket-server/rest/7.10.0/bitbucket-rest.html#idp292
    */

    /*
    default reviewers
    https://docs.atlassian.com/bitbucket-server/rest/7.10.0/bitbucket-default-reviewers-rest.html
     */
}