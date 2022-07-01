@file:SuppressWarnings("ConstructorParameterNaming")

package com.github.jensim.megamanipulator.actions.vcs.githubcom

data class GithubComRepo(
    // https://api.github.com/users/jensim/repos?page=1
    val id: Long, // 1296269,
    val name: String, // "Hello-World",
    val full_name: String, // "octocat/Hello-World",
    val owner: GithubComUser,
    val private: Boolean,
    val description: String? = null,
    val ssh_url: String, // git@github.com:spring-projects/spring-security.git,
    val clone_url: String, // https://github.com/spring-projects/spring-security.git,
    val fork: Boolean,
    val default_branch: String,
    val html_url: String,
    val forks_url: String,
    val parent: GithubComRepo? = null,
    val license: GithubComLicence? = null,
    val open_issues_count: Long,
)

data class GithubPullRequestRequest(
    val title: String,
    val body: String,
    val draft: Boolean,
    val maintainer_can_modify: Boolean,
    val head: String, // " to "$headProject/$headRepo:$localBranch",
    val base: String, // " to ghrepo.default_branch,
)

data class GithubComUser(
    val login: String, // "octocat",
    val id: Long, // 1296269,
    val type: String, // Organization, User
)

data class GithubComLicence(
    val key: String? = null, // ": "apache-2.0",
    val name: String? = null, // ": "Apache License 2.0",
    val spdx_id: String? = null, // ": "Apache-2.0",
    val url: String? = null, // ": "https://api.github.com/licenses/apache-2.0",
    val node_id: String? = null, // ": "MDc6TGljZW5zZTI="
)

data class GithubComPullRequest(
    val id: Long,
    // https://api.github.com/repos/jensim/jensim.github.io/pulls/3
    val url: String,
    val html_url: String,
    val comments_url: String,
    val user: GithubComUser,
    val body: String?,
    val state: String,
    val title: String,
    /** from */
    val head: GithubComRef? = null,
    /** to */
    val base: GithubComRef? = null,
)

data class GithubComRef(
    /** branch */
    val ref: String? = null,
    val repo: GithubComRepo? = null,
)

data class GithubComSearchResult<T>(
    val total_count: Long,
    val incomplete_results: Boolean,
    val items: List<T>,
)

data class GithubComIssue(
    val id: Long,
    val node_id: String,
    val pull_request: GithubComPullRequestLinks? = null,
)

data class GithubComPullRequestLinks(
    val url: String? = null,
)
