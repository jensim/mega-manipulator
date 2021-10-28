package com.github.jensim.megamanipulator.ui

import com.github.jensim.megamanipulator.settings.types.CodeHostSettings
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import javax.swing.InputVerifier
import javax.swing.JButton
import javax.swing.JComponent

object PullRequestLoaderDialogGenerator {

    private val stateSelector = ComboBox<String>()
    private val roleSelector = ComboBox<String>()

    private val limitField = JBTextField("100")
    private var lastUsedType: CodeHostSettings.CodeHostSettingsType? = null
    private val btnYes = JButton("Load")
    private val btnNo = JButton("Cancel")
    private const val MAGIC_NULL = "*"

    init {
        limitField.inputVerifier = object : InputVerifier() {
            override fun verify(input: JComponent?): Boolean = try {
                limitField.text.toInt()
                btnYes.isEnabled = true
                true
            } catch (e: NumberFormatException) {
                btnYes.isEnabled = false
                false
            }
        }
    }

    fun generateDialog(focus: JComponent, type: CodeHostSettings.CodeHostSettingsType, onYes: (state: String?, role: String?, limit: Int) -> Unit) {
        try {

            if (type != lastUsedType) {
                stateSelector.removeAllItems()
                type.prStates.forEach { stateSelector.addItem(it ?: MAGIC_NULL) }
                roleSelector.removeAllItems()
                type.prRoles.forEach { roleSelector.addItem(it ?: MAGIC_NULL) }
                lastUsedType = type
            }

            val content = panel {
                row { component(panel(title = "Pull request state") { row { component(stateSelector) } }) }
                row { component(panel(title = "Pull request role") { row { component(roleSelector) } }) }
                row { component(panel(title = "Limit") { row { component(limitField) } }) }
                row { cell { component(btnYes); component(btnNo) } }
            }
            val popupFactory: JBPopupFactory = JBPopupFactory.getInstance()
            val popup = popupFactory.createDialogBalloonBuilder(content, "Load pull requests")
                .createBalloon()
            btnYes.addActionListener {
                val text = limitField.text
                try {
                    clearListeners()
                    val limit = text.toInt()
                    onYes(stateSelector.item.toNullable(), roleSelector.item.toNullable(), limit)
                } catch (e: NumberFormatException) {
                    println("Not a valid number '$text'")
                }
                popup.hide()
            }
            btnNo.addActionListener {
                clearListeners()
                popup.hide()
            }
            val location: RelativePoint = popupFactory.guessBestPopupLocation(focus)

            popup.show(location, Balloon.Position.above)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearListeners() {
        btnYes.actionListeners.forEach {
            btnYes.removeActionListener(it)
        }
        btnNo.actionListeners.forEach {
            btnNo.removeActionListener(it)
        }
    }

    private fun String.toNullable(): String? = when (this) {
        MAGIC_NULL -> null
        else -> this
    }
}