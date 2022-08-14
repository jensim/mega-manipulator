package com.github.jensim.megamanipulator.test

import com.github.jensim.megamanipulator.settings.passwords.PasswordsOperator
import com.github.jensim.megamanipulator.settings.types.AuthMethod
import javax.swing.JComponent

typealias Password = String
typealias Username = String
typealias BaseUrl = String
typealias Login = Pair<Username, BaseUrl>

/**
 * Usage:
 * <code>
 *     private val passwordsOperator: PasswordsOperator = TestPasswordOperator(
 *         mapOf("username" to "https://example" to "password")
 *     )
 * </code>
 */
class TestPasswordOperator(private val passwordsMap: Map<Login, Password>) : PasswordsOperator {

    override fun isPasswordSet(username: String, baseUrl: String): Boolean = passwordsMap.containsKey(username to baseUrl)
    override fun getPassword(username: String, baseUrl: String): String? = passwordsMap[username to baseUrl]
    override fun promptForPassword(focusComponent: JComponent, authMethod: AuthMethod, username: String, baseUrl: String, callback: () -> Unit): Unit = TODO("not implemented")
}
