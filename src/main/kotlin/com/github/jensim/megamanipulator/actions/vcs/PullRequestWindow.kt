package com.github.jensim.megamanipulator.actions.vcs

import com.github.jensim.megamanipulator.settings.SerializationHolder
import com.github.jensim.megamanipulator.settings.SettingsFileOperator
import com.github.jensim.megamanipulator.toolswindow.ToolWindowTab
import com.github.jensim.megamanipulator.ui.CodeHostSelector
import com.github.jensim.megamanipulator.ui.GeneralListCellRenderer.addCellRenderer
import com.github.jensim.megamanipulator.ui.UiProtector
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import kotlinx.serialization.encodeToString
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PullRequestWindow(
    private val prRouter: PrRouter,
    private val uiProtector: UiProtector,
    private val pullRequestActionsMenu: PullRequestActionsMenu,
    settingsFileOperator: SettingsFileOperator,
) : ToolWindowTab {

    private val log = LoggerFactory.getLogger(javaClass)

    override val index: Int = 4

    private val search = JBTextField(50)
    private val pullRequests: MutableList<PullRequestWrapper> = mutableListOf()
    private val codeHostSelect = CodeHostSelector(settingsFileOperator)
    private val prList = JBList<PullRequestWrapper>()
    private val prScroll = JBScrollPane(prList)
    private val peekArea = JBTextArea()
    private val peekScroll = JBScrollPane(peekArea)
    private val menuOpenButton = JButton("Actions")
    private val split = JBSplitter(false, 0.5f).apply {
        firstComponent = prScroll
        secondComponent = peekScroll
        preferredSize = Dimension(4000, 1000)
    }
    override val content: JComponent = panel {
        row {
            component(codeHostSelect)
            button("Fetch author PRs") {
                fetchAuthoredPRs()
            }
            button("Fetch assigned PRs") {
                fetchAssignedPRs()
            }
            component(search)
            right {
                component(menuOpenButton)
            }
        }
        row {
            component(split)
        }
    }

    init {
        pullRequestActionsMenu.prProvider = { prList.selectedValuesList }
        pullRequestActionsMenu.focusComponent = menuOpenButton
        menuOpenButton.isEnabled = false
        menuOpenButton.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                val selected = codeHostSelect.selectedItem
                pullRequestActionsMenu.codeHostName = selected?.codeHostName
                pullRequestActionsMenu.searchHostName = selected?.searchHostName
                pullRequestActionsMenu.show(menuOpenButton, e.x, e.y)
            }

            override fun mousePressed(e: MouseEvent?) = Unit
            override fun mouseReleased(e: MouseEvent?) = Unit
            override fun mouseEntered(e: MouseEvent?) = Unit
            override fun mouseExited(e: MouseEvent?) = Unit
        })

        search.minimumSize = Dimension(200, 30)
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateFilteredPrs()
            }
            override fun removeUpdate(e: DocumentEvent?) {
                updateFilteredPrs()
            }
            override fun changedUpdate(e: DocumentEvent?) {
                updateFilteredPrs()
            }
        })

        prList.addCellRenderer { "${it.project()}/${it.baseRepo()} ${it.title()}" }
        prList.addListSelectionListener {
            menuOpenButton.isEnabled = false
            val selected: List<PullRequestWrapper> = prList.selectedValuesList
            if (selected.isNotEmpty()) {
                if (selected.size == 1) {
                    peekArea.text = SerializationHolder.readableJson.encodeToString(selected.first())
                }
                menuOpenButton.isEnabled = true
            } else {
                peekArea.text = ""
            }
        }
    }

    override fun refresh() {
        peekArea.text = ""
        prList.setListData(emptyArray())
        codeHostSelect.load()
    }

    private fun fetchAssignedPRs() {
        prList.setListData(emptyArray())
        (codeHostSelect.selectedItem)?.let { selected ->
            search.text = ""
            val prs: List<PullRequestWrapper>? = uiProtector.uiProtectedOperation("Fetching PRs") {
                prRouter.getAllReviewPrs(selected.searchHostName, selected.codeHostName)
            }
            setPrs(prs)
        } ?: log.warn("No codeHost selected")
    }
    private fun fetchAuthoredPRs() {
        prList.setListData(emptyArray())
        (codeHostSelect.selectedItem)?.let { selected ->
            search.text = ""
            val prs: List<PullRequestWrapper>? = uiProtector.uiProtectedOperation("Fetching PRs") {
                prRouter.getAllAuthorPrs(selected.searchHostName, selected.codeHostName)
            }
            setPrs(prs)
        } ?: log.warn("No codeHost selected")
    }

    private fun setPrs(prs: List<PullRequestWrapper>?) {
        log.info("Setting prs, new count is ${prs?.size}")
        pullRequests.clear()
        prs?.let { pullRequests.addAll(it) }
        val filtered = updateFilteredPrs()
        if (filtered.isNotEmpty()) {
            prList.setSelectedValue(filtered.first(), true)
        }
        content.validate()
        content.repaint()
    }

    private fun updateFilteredPrs(): List<PullRequestWrapper> {
        val filtered = pullRequests.filter {
            search.text.isBlank() || FuzzySearch.tokenSetRatio(it.raw, search.text) == 100
        }.sortedBy { "${it.project()}/${it.baseRepo()} ${it.title()}" }
        prList.setListData(filtered.toTypedArray())
        return filtered
    }
}
