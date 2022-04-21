package eu.dcotta.confis.plugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import eu.dcotta.confis.render.renderMarkdown
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import kotlin.script.experimental.api.ResultWithDiagnostics.Failure
import kotlin.script.experimental.api.ResultWithDiagnostics.Success

class ConfisEditor(
    val editor: TextEditor,
    val confisFile: VirtualFile,
    val preview: MarkdownPreviewFileEditor,
    val mdInMem: LightVirtualFile,
    val project: Project,
) :
    TextEditorWithPreview(editor, preview, "ConfisEditor", SHOW_EDITOR_AND_PREVIEW, true) {

    val PARENT_SPLIT_EDITOR_KEY: Key<ConfisEditor> = Key.create("parentSplit")

    val scriptDocument = FileDocumentManager.getInstance().getDocument(confisFile)
    val logger = logger<ConfisEditor>()

    init {
        editor.putUserData(PARENT_SPLIT_EDITOR_KEY, this)
        preview.putUserData(PARENT_SPLIT_EDITOR_KEY, this)

        preview.setMainEditor(editor.editor)

        // preview.set
        // see https://github.com/JetBrains/intellij-community/blob/master/plugins/markdown/core/src/org/intellij/plugins/markdown/ui/preview/MarkdownEditorWithPreview.java
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/4629796215698-How-to-create-a-SplitEditorToolbar-in-Intellij-IDEA-plugin-
    }

    private val alarm = Alarm(POOLED_THREAD, this)
    private val uiAlarm = Alarm(SWING_THREAD, this)
    val docFactory = FileDocumentManager.getInstance()

    private val host = ConfisHost()

    private fun documentToMarkdown(event: DocumentEvent): String {
        val source =
            // VirtualFileScriptSource(confisFile)
            ConfisSourceCode(confisFile.url, confisFile.name, event.document.text)

        return when (val res = host.eval(source)) {
            is Success -> res.value.renderMarkdown()
            is Failure -> res.reportsAsMarkdown()
        }
    }

    private val scriptListener = DocumentListenerImpl(
        beforeDocChange = {
            alarm.cancelAllRequests()
            uiAlarm.cancelAllRequests()
        },
        afterDocChange = { event ->
            alarm.request(delayMillis = 100) {
                val md = documentToMarkdown(event)
                logger.warn("Setting markdown $md")
                uiAlarm.request {
                    WriteAction.run<Exception> {
                        docFactory.getDocument(mdInMem)?.setText(md)
                    }
                    logger.debug("Set in-mem markdown: ${mdInMem.content}")
                    preview.selectNotify()
                }
            }
        }
    )

    init {
        scriptDocument?.addDocumentListener(scriptListener)
    }

    override fun dispose() {
        alarm.cancelAllRequests()
        scriptDocument?.removeDocumentListener(scriptListener)
    }

    private fun Failure.reportsAsMarkdown(): String =
        reports.joinToString(separator = "\n\n", prefix = "```\nErrors where found:\n", postfix = "\n```") {
            it.render()
        }
}

data class DocumentListenerImpl(
    val beforeDocChange: (DocumentEvent) -> Unit,
    val afterDocChange: (DocumentEvent) -> Unit,
) : DocumentListener {
    override fun beforeDocumentChange(event: DocumentEvent) = beforeDocChange(event)
    override fun documentChanged(event: DocumentEvent) = afterDocChange(event)
}