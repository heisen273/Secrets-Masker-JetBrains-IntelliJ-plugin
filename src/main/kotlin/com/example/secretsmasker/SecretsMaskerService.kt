package com.example.secretsmasker

import com.example.secretsmasker.settings.SecretsMaskerSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.util.regex.Pattern
import java.util.regex.Matcher


@Service
class SecretsMaskerService {
    private val logger = Logger.getInstance(SecretsMaskerService::class.java)

    var isMaskingEnabled: Boolean = true
        private set

    init {
        logger.warn("SecretsMaskerService initialized")
    }

    fun toggleMasking(): Boolean {
        isMaskingEnabled = !isMaskingEnabled
        logger.warn("Masking toggled to: $isMaskingEnabled")
        return isMaskingEnabled
    }

    fun maskSensitiveData(editor: Editor) {
        logger.warn("Attempting to mask sensitive data in editor: ${editor.document.textLength} chars")

        val highlighter = editor.markupModel
        highlighter.removeAllHighlighters()

        if (!isMaskingEnabled) {
            logger.warn("Masking is disabled - cleared highlights only")
            return
        }

        val document = editor.document
        val text = document.text
        var totalMatchCount = 0

        // Get settings
        val settings = SecretsMaskerSettings.getInstance()

        // Apply each pattern
        for (patternStr in settings.patterns) {
            try {
                val pattern = Pattern.compile(patternStr)
                val matcher = pattern.matcher(text)

                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()

                    // Calculate the highlight range based on the setting
                    val (highlightStart, highlightEnd) = if (settings.hideOnlyValues) {
                        calculateValuePortion(text, pattern, matcher)
                    } else {
                        Pair(start, end)
                    }

                    val matchedText = text.substring(highlightStart, highlightEnd)
                    logger.warn("Found sensitive data: '$matchedText' at [$highlightStart, $highlightEnd]")

                    val attributes = TextAttributes().apply {
                        backgroundColor = Color(180, 180, 180)
                        foregroundColor = Color(180, 180, 180)
                    }

                    highlighter.addRangeHighlighter(
                        highlightStart, highlightEnd, HighlighterLayer.LAST,
                        attributes, HighlighterTargetArea.EXACT_RANGE
                    )
                    totalMatchCount++
                }
            } catch (e: Exception) {
                logger.error("Invalid regex pattern: $patternStr", e)
            }
        }

        logger.warn("Applied $totalMatchCount highlighters")
    }

    fun calculateValuePortion(text: String, pattern: Pattern, matcher: Matcher): Pair<Int, Int> {
        val fullStart = matcher.start()
        val fullEnd = matcher.end()

        // For patterns like "API_KEY.*", try to find where the value part starts
        // Look for common separators like =, :, etc.
        val matchedText = text.substring(fullStart, fullEnd)

        // Find the first separator character after the key part
        val separators = arrayOf("= ", "=", ": ", ":", "-> ", "->", " ")
        for (separator in separators) {
            val sepIndex = matchedText.indexOf(separator)
            if (sepIndex > 0) {
                // Calculate where the value starts after the separator
                val valueStart = fullStart + sepIndex + separator.length
                // If there's an identifiable value part, return that range
                if (valueStart < fullEnd) {
                    return Pair(valueStart, fullEnd)
                }
            }
        }

        // If no clear separator is found, fallback to the full match.
        return Pair(fullStart, fullEnd)
    }
}