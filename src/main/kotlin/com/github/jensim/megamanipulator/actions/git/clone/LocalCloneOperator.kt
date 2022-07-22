package com.github.jensim.megamanipulator.actions.git.clone

import com.github.jensim.megamanipulator.actions.ProcessOperator
import com.github.jensim.megamanipulator.actions.apply.ApplyOutput
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.project.lazyService
import com.github.jensim.megamanipulator.settings.types.codehost.CodeHostSettings
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.OnErrorAction.SKIP

class LocalCloneOperator @NonInjectable constructor(
    private val project: Project,
    processOperator: ProcessOperator?,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    constructor(project: Project) : this(
        project = project,
        processOperator = null,
    )

    private val processOperator: ProcessOperator by lazyService(project, processOperator)

    /**
     * If the repo is configured for local copy over remote clone, try to find the repo locally,
     * and create a clone from that.
     */
    suspend fun copyIf(settings: CodeHostSettings, repo: SearchResult, defaultBranch: String, branch: String): Result {
        val localRepoFile = File(project.basePath!!, repo.asPathString())
        if (File(localRepoFile, ".git").exists()) {
            return Result(listOf("Copy" to ApplyOutput.dummy(std = "Repo exists already", dir = repo.asPathString())), true)
        }

        val keepRoot = settings.keepLocalRepos?.path ?: return Result(emptyList(), false)
        val keepLocalRepo = "$keepRoot/${repo.project}/${repo.repo}"
        val keepLocalRepoFile = File(keepLocalRepo, ".git")

        val history = mutableListOf<Action>()

        if (keepLocalRepoFile.exists()) {
            unshallowAndFetch(keepLocalRepoFile.parentFile, history)
            return try {
                keepLocalRepoFile.copyRecursively(localRepoFile) { file, exception ->
                    val message = "File copy failed: '${file.path}', due to ${exception.message}"
                    history.add("File copy fail" to ApplyOutput.dummy(std = message, dir = repo.asPathString()))
                    logger.warn(message)
                    SKIP
                }
                checkout(defaultBranch, branch, keepLocalRepoFile, history)
                history.add("Restore saved repo" to ApplyOutput(repo.asPathString(), "Done", 0))

                Result(history, true)
            } catch (e: Exception) {
                val message = "Failed copying files from keep location due to ${e.message}"
                logger.warn(message, e)
                history.add("Restore saved repo" to ApplyOutput(repo.asPathString(), "$message, more info+stacktrace in logs", 0))
                Result(history, false)
            }
        } else {
            return Result(history, false)
        }
    }

    private suspend fun checkout(defaultBranch: String, branch: String, repo: File, history: MutableList<Action>) {
        /*
         * TODO:
         *  * Delete local branch if exists..
         *  * Handle PR clone, where branch should exist
         */
        processOperator.runCommandAsync(repo.parentFile, listOf("git", "checkout", defaultBranch)).await().let {
            history.add("git checkout default branch '$defaultBranch'" to it)
        }
        if (defaultBranch != branch && history.last().second.exitCode == 0) {
            val p3 = processOperator.runCommandAsync(repo, listOf("git", "checkout", branch)).await()
            history.add("Switch branch" to p3)
            if (p3.exitCode != 0) {
                val p4 = processOperator.runCommandAsync(repo, listOf("git", "checkout", "-b", branch)).await()
                history.add("Create branch" to p4)
            }
        }
    }

    data class Result(
        val actions: List<Action>,
        val success: Boolean,
    )

    /**
     * Save a copy of a clone after it has been pulled into the configured location
     */
    suspend fun saveCopy(settings: CodeHostSettings, repo: SearchResult, defaultBranch: String): Result {
        val localRepoFile = File(project.basePath!!, repo.asPathString() + ".git")
        if (!localRepoFile.exists()) {
            return Result(listOf("Save Copy" to ApplyOutput.dummy(std = "Repo doesn't exist", dir = repo.asPathString())), false)
        }

        val keepRoot = settings.keepLocalRepos?.path ?: return Result(emptyList(), false)
        val keepLocalRepo = "$keepRoot/${repo.project}/${repo.repo}"
        val keepLocalRepoFile = File(keepLocalRepo, ".git")
        if (keepLocalRepoFile.exists()) {
            return Result(listOf("Save copy" to ApplyOutput.dummy(std = "Copy saved already, wont update", dir = repo.asPathString(), exitCode = 0)), true)
        }

        val history = mutableListOf<Action>()

        localRepoFile.copyRecursively(keepLocalRepoFile) { file, exception ->
            val message = "Save copy failed: '${file.path}', due to ${exception.message}"
            history.add("File copy fail" to ApplyOutput.dummy(std = message, dir = repo.asPathString()))
            logger.warn(message)
            SKIP
        }
        File(keepLocalRepoFile, "info/sparse-checkout").delete().let { deletedSparse ->
            history.add("Delete sparse checkout settings" to ApplyOutput(repo.asPathString(), "Deleted: $deletedSparse", 0))
        }
        processOperator.runCommandAsync(keepLocalRepoFile.parentFile, listOf("git", "config", "core.sparseCheckout", "false")).await().let {
            history.add("disable sparseCheckout for local save" to it)
        }

        unshallowAndFetch(keepLocalRepoFile.parentFile, history)
        processOperator.runCommandAsync(keepLocalRepoFile.parentFile, listOf("git", "checkout", defaultBranch)).await().let {
            history.add("git checkout $defaultBranch" to it)
        }

        return Result(history, true)
    }

    private suspend fun unshallowAndFetch(dir: File, history: MutableList<Action>) {
        processOperator.runCommandAsync(dir, listOf("git", "fetch", "--unshallow")).await().let {
            history.add("git fetch --unshallow" to it)
        }
        processOperator.runCommandAsync(dir, listOf("git", "fetch", "--all")).await().let {
            history.add("git fetch --all" to it)
        }
    }
}
