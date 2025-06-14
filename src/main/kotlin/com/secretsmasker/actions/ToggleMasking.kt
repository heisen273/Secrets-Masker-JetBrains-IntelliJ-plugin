package com.secretsmasker.actions

import com.secretsmasker.SecretsMaskerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.ui.Messages

class ToggleMasking : AnAction() {
    private val logger = Logger.getInstance(ToggleMasking::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        logger.warn("Toggle masking action triggered")
        val project = e.project ?: return

        // Get the service and toggle masking
        val maskerService = service<SecretsMaskerService>()
        val isEnabled = maskerService.toggleMasking()

        // Show notification to user about current state
        val message = if (isEnabled) "Secrets masking enabled" else "Secrets masking disabled"
        Messages.showInfoMessage(project, message, "Masking Toggle")

        // Re-apply masking to all open editors
        refreshAllEditors(maskerService)
    }

    private fun refreshAllEditors(maskerService: SecretsMaskerService) {
        // Get all open editors and refresh them
        val editors = EditorFactory.getInstance().allEditors
        for (editor in editors) {
            maskerService.maskSensitiveData(editor)
        }
    }
}