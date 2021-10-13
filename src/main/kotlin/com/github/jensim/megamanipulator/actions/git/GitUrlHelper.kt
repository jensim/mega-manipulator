package com.github.jensim.megamanipulator.actions.git

import com.github.jensim.megamanipulator.actions.vcs.RepoWrapper
import com.github.jensim.megamanipulator.project.lazyService
import com.github.jensim.megamanipulator.settings.passwords.PasswordsOperator
import com.github.jensim.megamanipulator.settings.types.CloneType
import com.github.jensim.megamanipulator.settings.types.CodeHostSettings
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable

class GitUrlHelper @NonInjectable constructor(
    project: Project,
    passwordsOperator: PasswordsOperator?,
) {
    constructor(project: Project) : this(project, null)

    private val passwordsOperator: PasswordsOperator by lazyService(project, passwordsOperator)

    fun buildCloneUrl(codeSettings: CodeHostSettings, vcsRepo: RepoWrapper): String {
        val cloneUrl = vcsRepo.getCloneUrl(codeSettings.cloneType)!!
        return buildCloneUrl(codeSettings, cloneUrl)
    }

    fun buildCloneUrl(codeSettings: CodeHostSettings, cloneUrl: String): String {
        return when (codeSettings.cloneType) {
            CloneType.SSH -> cloneUrl
            CloneType.HTTPS -> {
                val password = passwordsOperator.aggressivePercentEncoding(
                    passwordsOperator.getPassword(
                        codeSettings.username!!,
                        codeSettings.baseUrl
                    )!!
                )
                val username = passwordsOperator.aggressivePercentEncoding(codeSettings.username!!)
                "${cloneUrl.substringBefore("://")}://$username:$password@${cloneUrl.substringAfter("://")}"
            }
        }
    }
}
