package com.github.jensim.megamanipulator.settings.passwords

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.settings.SerializationHolder
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationType
import com.intellij.remoteServer.util.CloudConfigurationUtil.createCredentialAttributes
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.NotThreadSafe
import javax.swing.JOptionPane
import javax.swing.JOptionPane.OK_CANCEL_OPTION
import javax.swing.JOptionPane.OK_OPTION
import javax.swing.JOptionPane.QUESTION_MESSAGE

@NotThreadSafe
class IntelliJPasswordsOperator(
    private val notificationsOperator: NotificationsOperator,
) : PasswordsOperator {

    companion object {
        private const val service = "mega-manipulator"
    }

    private val serviceUsername: String by lazy { System.getProperty("user.name") ?: service }
    private val passwordSetMap: MutableMap<String, Boolean> = ConcurrentHashMap()

    private fun usernameToKey(username: String, baseUrl: String) = "${username}___$baseUrl"

    @SuppressWarnings(value = ["ComplexCondition"])
    override fun promptForPassword(username: String?, baseUrl: String): String {
        val usernameField = JBTextField(30)
        val passwordField = JBPasswordField().apply { columns = 30 }
        val content = panel {
            row {
                label("Please provide credentials for $baseUrl")
            }
            row {
                when (username) {
                    null -> {
                        label("Username:")
                        component(usernameField)
                    }
                    "token" -> label("TOKEN login method")
                    else -> label("Username: $username")
                }
            }
            row {
                label("Password:")
                component(passwordField)
            }
        }
        val ans = JOptionPane.showConfirmDialog(null, content, "Password please", OK_CANCEL_OPTION, QUESTION_MESSAGE, null)
        return if (ans != OK_OPTION) {
            ""
        } else if (username == null && usernameField.text.isNullOrEmpty()) {
            notificationsOperator.show(
                title = "Username not set",
                body = "Username was not entered",
                type = NotificationType.WARNING
            )
            ""
        } else if (passwordField.password.concatToString().isNullOrEmpty()) {
            notificationsOperator.show(
                title = "Password not set",
                body = "Password was not entered",
                type = NotificationType.WARNING
            )
            ""
        } else {
            val resolvedUsername = username ?: usernameField.text
            val password = passwordField.password.concatToString().trim()

            setPassword(resolvedUsername, password, baseUrl)
            password
        }
    }

    override fun isPasswordSet(username: String, baseUrl: String): Boolean = usernameToKey(username, baseUrl).let { userKey ->
        passwordSetMap.computeIfAbsent(userKey) { getPassword(username, baseUrl) != null }
    }

    fun deletePasswords(username: String, baseUrl: String) {
        val unambiguousUsername = "${serviceUsername}___${username}___$baseUrl"
        val credentialAttributes: CredentialAttributes? = createCredentialAttributes(service, unambiguousUsername)
        if (credentialAttributes == null) {
            notificationsOperator.show(
                title = "Failed deleting password",
                body = "Could not create CredentialAttributes",
                type = NotificationType.WARNING
            )
        } else {
            PasswordSafe.instance.set(credentialAttributes, null)
        }
    }

    override fun getPassword(username: String, baseUrl: String): String? {
        return getPassword(usernameToKey(username, baseUrl))
    }

    private fun getPassword(usernameKey: String): String? {
        val credentialAttributes: CredentialAttributes? = createCredentialAttributes(service, serviceUsername)
        return if (credentialAttributes == null) {
            notificationsOperator.show("Failed setting password", "Could not create CredentialAttributes", NotificationType.WARNING)
            null
        } else {
            PasswordSafe.instance.getPassword(credentialAttributes)?.let { passMapStr ->
                val passMap: Map<String, String> = SerializationHolder.readableJson.decodeFromString(passMapStr)
                passMap[usernameKey]
            }
        }
    }

    private fun setPassword(username: String, password: String, baseUrl: String) {
        setPassword(usernameToKey(username, baseUrl), password)
    }

    private fun setPassword(usernameKey: String, password: String) {
        val credentialAttributes: CredentialAttributes? = createCredentialAttributes(service, serviceUsername)
        if (credentialAttributes == null) {
            notificationsOperator.show("Failed setting password", "Could not create CredentialAttributes", NotificationType.WARNING)
        } else {
            val preexisting: String? = PasswordSafe.instance.getPassword(credentialAttributes)
            if (preexisting == null) {
                val passwordsMap = mapOf(usernameKey to password)
                val passMapStr = SerializationHolder.readableJson.encodeToString(passwordsMap)
                val credentials = Credentials(serviceUsername, passMapStr)
                PasswordSafe.instance.set(credentialAttributes, credentials)
            } else {
                PasswordSafe.instance.getPassword(credentialAttributes)?.let { passMapStr: String ->
                    val passMap: MutableMap<String, String> = SerializationHolder.readableJson.decodeFromString(passMapStr)
                    passMap[usernameKey] = password
                    val passMapStrMod = SerializationHolder.readableJson.encodeToString(passMap)
                    val credentials = Credentials(serviceUsername, passMapStrMod)
                    PasswordSafe.instance.set(credentialAttributes, credentials)
                }
            }
        }
        passwordSetMap.clear()
    }
}
