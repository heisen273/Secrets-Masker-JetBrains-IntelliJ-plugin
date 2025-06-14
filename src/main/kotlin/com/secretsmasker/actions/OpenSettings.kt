package com.secretsmasker.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class OpenSettings : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Secrets Masker"
        )
    }
}