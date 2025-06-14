package com.secretsmasker.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Color

@State(
    name = "SecretsMaskerSettings",
    storages = [Storage("SecretsMaskerSettingsV2.xml")]
)
class SecretsMaskerSettings : PersistentStateComponent<SecretsMaskerSettings> {

    var patterns: MutableList<String> = mutableListOf("API_KEY.*", "SECRET.*", "PASSWORD.*")
    var hideOnlyValues: Boolean = false
    var invisibleHighlight: Boolean = false
    var highlightColor: Int = Color(180, 180, 180).rgb


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