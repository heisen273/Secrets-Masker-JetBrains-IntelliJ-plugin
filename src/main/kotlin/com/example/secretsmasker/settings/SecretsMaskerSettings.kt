package com.example.secretsmasker.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "SecretsMaskerSettings",
    storages = [Storage("SecretsMaskerSettings.xml")]
)
class SecretsMaskerSettings : PersistentStateComponent<SecretsMaskerSettings> {
    var patterns: MutableList<String> = mutableListOf("API_KEY.*", "SECRET.*", "PASSWORD.*")
    var hideOnlyValues: Boolean = false

    override fun getState(): SecretsMaskerSettings = this

    override fun loadState(state: SecretsMaskerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): SecretsMaskerSettings {
            return service<SecretsMaskerSettings>()
        }
    }
}