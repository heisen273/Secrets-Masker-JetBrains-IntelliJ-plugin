package com.secretsmasker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.secretsmasker.SecretsMaskerService
import com.secretsmasker.settings.SecretsMaskerSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.ui.Messages

class ToggleMasking : ToggleAction() {
    private val logger = Logger.getInstance(ToggleMasking::class.java)
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean {
        // Return current masking state from service
        val maskerService = service<SecretsMaskerService>()
        return maskerService.isMaskingEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        logger.warn("Toggle masking action triggered: $state")
        val project = e.project

        // Get the service
        val maskerService = service<SecretsMaskerService>()

        if (maskerService.isMaskingEnabled == state) {
            // No change needed
            return
        }

        // Get fresh settings from getInstance
        val settings = SecretsMaskerSettings.getInstance()

        // Show warning dialog if trying to disable and warning is enabled
        if (!state && settings.warnBeforeDisabling) {
            val result = Messages.showYesNoDialog(
                project,
                "Are you sure you want to disable secrets masking?\n\nThis will reveal all sensitive information in your editor.",
                "Disable Secrets Masking",
                "Yes",
                "Cancel",
                Messages.getWarningIcon()
            )

            // If user clicked Cancel (NO), don't change the state
            if (result != Messages.YES) {
                return
            }
        }

        // Toggle the masking state
        maskerService.toggleMasking()

        // Show notification to user about current state
//        if (project != null) {
//            val message = if (state) "Secrets masking enabled" else "Secrets masking disabled"
//            Messages.showInfoMessage(project, message, "Masking Toggle")
//        }

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