package com.secretsmasker

import com.intellij.openapi.actionSystem.DefaultActionGroup

class SecretsMaskerActionGroup : DefaultActionGroup() {
    // No need to override getChildren() since DefaultActionGroup handles children
    // registered in plugin.xml automatically
}