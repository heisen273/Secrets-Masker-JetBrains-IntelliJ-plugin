package com.secretsmasker

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.components.service

class SecretsMaskerListener : EditorFactoryListener {
    private val logger = Logger.getInstance(SecretsMaskerListener::class.java)

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        logger.warn("Editor created: ${editor.document.textLength} chars")
        val document = editor.document
        val maskerService = service<SecretsMaskerService>()

        // Initial masking
        maskerService.maskSensitiveData(editor)

        // Listen for document changes
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                logger.warn("Document changed: ${event.document.textLength} chars")
                maskerService.maskSensitiveData(editor)
            }
        })
    }
}