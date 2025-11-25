package com.secretsmasker.settings

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Color

@State(
    name = "SecretsMaskerSettings",
    storages = [Storage("SecretsMaskerSettingsV2.xml")]
)
class SecretsMaskerSettings : PersistentStateComponent<SecretsMaskerSettings> {

    var enabled: Boolean = true

    var patterns: MutableList<String> = mutableListOf("API_KEY.*", "SECRET.*", "PASSWORD.*")
    var hideOnlyValues: Boolean = false
    var invisibleHighlight: Boolean = false
    var highlightColor: Int = Color(180, 180, 180).rgb
    var warnBeforeDisabling: Boolean = true


    override fun getState(): SecretsMaskerSettings = this

    override fun loadState(state: SecretsMaskerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): SecretsMaskerSettings {
            return service<SecretsMaskerSettings>()
        }
    }

    /**
     * Checks if the IDE's antialiasing type is set to `SUBPIXEL`.
     * Normally happens by default on Windows(maybe there's some pattern - like machines with LCD screens).
     * `GREYSCALE` anti-aliasing is required for proper functioning of the masking feature.
     *
     * @return `true` if SUBPIXEL antialiasing is enabled, `false` otherwise.
     */
    fun isSubpixelAAEnabled(): Boolean {
        return UISettings.getInstance().ideAAType.name.equals("SUBPIXEL", ignoreCase = true)
    }
}