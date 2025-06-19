package com.secretsmasker.settings

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.secretsmasker.SecretsMaskerService
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.Configurable
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.CellEditorListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener

import javax.swing.event.ChangeEvent
import javax.swing.table.DefaultTableModel
import javax.swing.undo.*
import java.awt.event.KeyEvent
import kotlin.math.min
import java.util.EventObject
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
//import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager


class SecretsMaskerConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var patternsTable: JTable? = null
    private var tableModel: DefaultTableModel? = null
    private val undoManager = CompoundUndoManager()
    private var originalCellValue = ""

    private var hideOnlyValuesCheckBox: JCheckBox? = null
    private var invisibleHighlightCheckBox: JCheckBox? = null

    private var colorPreviewPanel: JPanel? = null
    private var colorModified: Boolean = false

    private var previewEditor: Editor? = null


    override fun getDisplayName(): String = "Secrets Masker"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(20, 20, 20, 20)
        }
        val currentSettings = SecretsMaskerSettings.getInstance()

        // Create main content panel
        val contentPanel = JPanel(BorderLayout(0, 20))

        // Top section - Settings
        val settingsPanel = createSettingsPanel(currentSettings)
        contentPanel.add(settingsPanel, BorderLayout.NORTH)

        // Middle section - Patterns table
        val patternsPanel = createPatternsPanel(currentSettings)
        contentPanel.add(patternsPanel, BorderLayout.CENTER)

        // Bottom section - Preview (smaller)
        val previewPanel = createPreviewPanel()
        previewPanel.preferredSize = Dimension(0, 150)
        contentPanel.add(previewPanel, BorderLayout.SOUTH)

        mainPanel!!.add(contentPanel, BorderLayout.CENTER)
        return mainPanel!!
    }

    private fun createSettingsPanel(currentSettings: SecretsMaskerSettings): JPanel {
        val settingsPanel = JPanel()
        settingsPanel.layout = BoxLayout(settingsPanel, BoxLayout.Y_AXIS)
        settingsPanel.border = EmptyBorder(0, 0, 0, 0)

        // Option 1: Hide only values checkbox
        hideOnlyValuesCheckBox = JCheckBox("Hide only values after patterns").apply {
            toolTipText = "Only hide the value after '=' or ':'"
            isSelected = currentSettings.hideOnlyValues
            addActionListener { updatePreview() }
            alignmentX = Component.LEFT_ALIGNMENT
            border = EmptyBorder(1, 0, 0, 0)
        }
        settingsPanel.add(hideOnlyValuesCheckBox!!)

        // Option 2: Invisible highlight checkbox
        invisibleHighlightCheckBox = JCheckBox("Invisible highlight").apply {
            toolTipText = "Make highlighted text blend with background"
            isSelected = currentSettings.invisibleHighlight
            addActionListener { updatePreview() }
            alignmentX = Component.LEFT_ALIGNMENT
            border = EmptyBorder(1, 0, 0, 0)
        }
        settingsPanel.add(invisibleHighlightCheckBox!!)

        // Option 3: Color picker
        val colorPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 5)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
        colorPanel.add(JLabel("Highlight Color: "))
        colorPreviewPanel = createColorPreviewPanel(currentSettings)
        colorPanel.add(colorPreviewPanel!!)
        settingsPanel.add(colorPanel)

        return settingsPanel
    }

    private fun createColorPreviewPanel(currentSettings: SecretsMaskerSettings): JPanel {
        val colorChooser = JColorChooser(Color(currentSettings.highlightColor))
        val swatchesPanel = colorChooser.chooserPanels.first()
        for (panel in colorChooser.chooserPanels) {
            if (panel != swatchesPanel) {
                colorChooser.removeChooserPanel(panel)
            }
        }

        return JPanel().apply {
            background = Color(currentSettings.highlightColor)
            preferredSize = Dimension(30, 25)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
            )

            addMouseListener(object : MouseListener {
                override fun mouseClicked(e: MouseEvent?) {
                    val dialog = JColorChooser.createDialog(
                        mainPanel,
                        "Select Highlight Color",
                        true,
                        colorChooser,
                        {
                            val selectedColor = colorChooser.color
                            background = selectedColor
                            currentSettings.highlightColor = selectedColor.rgb
                            repaint()
                            colorModified = true
                            updatePreview()
                        },
                        null
                    )
                    dialog.isVisible = true
                }

                override fun mouseEntered(e: MouseEvent?) {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }

                override fun mouseExited(e: MouseEvent?) {
                    cursor = Cursor.getDefaultCursor()
                }

                override fun mousePressed(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
            })
        }
    }

    private fun createPatternsPanel(currentSettings: SecretsMaskerSettings): JPanel {
        val patternsPanel = JPanel(BorderLayout(0, 0))
        patternsPanel.border = BorderFactory.createTitledBorder("Regular Expression Patterns")

        // Instructions for patterns
        val instructionsPanel = JPanel()
        instructionsPanel.layout = BoxLayout(instructionsPanel, BoxLayout.Y_AXIS)
        instructionsPanel.border = EmptyBorder(5, 10, 5, 10)

        val mainInstructions = JLabel(
            "<html>Configure patterns to match secrets. Each pattern is compiled as a regular expression.</html>"
        ).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 12f)
        }
        instructionsPanel.add(mainInstructions)

        val tableInstructions = JLabel(
            "<html><i>Double-click/start typing to edit, press Enter to save, or Backspace to remove rows.</i></html>"
        ).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            font = font.deriveFont(Font.ITALIC, 11f)
            foreground = Color.GRAY
            border = EmptyBorder(2, 0, 0, 0)
        }
        instructionsPanel.add(tableInstructions)

        patternsPanel.add(instructionsPanel, BorderLayout.NORTH)

        // Table setup
        tableModel = object : DefaultTableModel(arrayOf("Pattern"), 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = true
        }
        currentSettings.patterns.distinct().forEach { pattern ->
            tableModel!!.addRow(arrayOf(pattern))
        }

        patternsTable = object : JTable(tableModel) {
            override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
                if (!pressed) return super.processKeyBinding(ks, e, condition, pressed)

                if (e.isMetaDown && e.keyCode == KeyEvent.VK_Z) {
                    if (e.isShiftDown && undoManager.canRedo()) undoManager.redo()
                    else if (undoManager.canUndo()) undoManager.undo()
                    repaint()
                    updatePreview()
                    return true
                }

                if (e.isShiftDown && (e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN)) {
                    return super.processKeyBinding(ks, e, condition, pressed)
                }

                if ((e.keyCode == KeyEvent.VK_BACK_SPACE || e.keyCode == KeyEvent.VK_DELETE) && !isEditing) {
                    deleteSelectedRows()
                    return true
                }

                return super.processKeyBinding(ks, e, condition, pressed)
            }

            override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean {
                val result = super.editCellAt(row, column, e)
                if (result) {
                    SwingUtilities.invokeLater {
                        val editorComponent = getEditorComponent()
                        editorComponent?.requestFocusInWindow()
                    }
                }
                return result
            }
        }.apply {
            tableHeader.reorderingAllowed = false
            selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            rowHeight = 25
        }

        // Set up DefaultCellEditor with JTextField
        val textField = JTextField()
        val editor = DefaultCellEditor(textField)
        patternsTable!!.setDefaultEditor(String::class.java, editor)

        // Capture original value for undo
        patternsTable!!.addPropertyChangeListener { evt ->
            if (evt.propertyName == "tableCellEditor" && evt.newValue != null) {
                val row = patternsTable!!.editingRow
                val col = patternsTable!!.editingColumn
                if (row >= 0 && col >= 0) {
                    originalCellValue = patternsTable!!.getValueAt(row, col).toString()
                }
            }
        }

        // Handle editing stop events
        editor.addCellEditorListener(object : CellEditorListener {
            override fun editingStopped(e: ChangeEvent) {
                val row = patternsTable!!.editingRow
                val col = patternsTable!!.editingColumn
                if (row >= 0 && col >= 0) {
                    val newValue = patternsTable!!.getValueAt(row, col).toString().trim()
                    if (newValue.isNotEmpty() && tableModel!!.dataVector.any {
                            it[0].toString() == newValue && it != tableModel!!.dataVector[row]
                        }) {
                        JOptionPane.showMessageDialog(
                            mainPanel, "Pattern '$newValue' already exists.", "Duplicate Pattern", JOptionPane.WARNING_MESSAGE
                        )
                        tableModel!!.setValueAt(originalCellValue, row, col)
                        return
                    }
                    if (newValue.isEmpty()) deleteRow(row)
                    else if (newValue != originalCellValue) {
                        undoManager.addEdit(CellEditUndoableEdit(tableModel!!, row, col, originalCellValue, newValue))
                        updatePreview()
                    }
                }
            }

            override fun editingCanceled(e: ChangeEvent) {
                if (patternsTable!!.editingRow >= 0 && patternsTable!!.editingColumn >= 0) {
                    tableModel!!.setValueAt(originalCellValue, patternsTable!!.editingRow, patternsTable!!.editingColumn)
                }
            }
        })

        val scrollPane = JScrollPane(patternsTable).apply {
            preferredSize = Dimension(0, 150)
        }
        patternsPanel.add(scrollPane, BorderLayout.CENTER)

        // Buttons panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 5))
        buttonPanel.add(JButton("Add Pattern").apply {
            addActionListener { addRow("") }
        })
        buttonPanel.add(JButton("Remove Selected").apply {
            addActionListener { deleteSelectedRows() }
        })

        patternsPanel.add(buttonPanel, BorderLayout.SOUTH)
        return patternsPanel
    }

    private fun createPreviewPanel(): JPanel {
        val previewPanel = JPanel(BorderLayout())
        previewPanel.border = BorderFactory.createTitledBorder("Preview")

        // Sample text with various secret patterns (shorter for compact preview)
        val sampleText = """
# Configuration
SECRET_TOKEN=ghp_abcdefghij
export API_KEY="prod-key-value"
"api_key": "json-key-12345"
PASSWORD: admin123
    """.trimIndent()

        // Create editor
        val project = ProjectManager.getInstance().defaultProject
        val document = EditorFactory.getInstance().createDocument(sampleText)
        previewEditor = EditorFactory.getInstance().createEditor(document, project).apply {
            if (this is EditorEx) {
                val editorSettings = settings
                editorSettings.isLineNumbersShown = true
                editorSettings.isLineMarkerAreaShown = false
                editorSettings.isFoldingOutlineShown = false
                editorSettings.isRightMarginShown = false
                editorSettings.isVirtualSpace = false
                editorSettings.isCaretRowShown = false

                // Set file type for syntax highlighting
//                if (ApplicationInfo.getInstance().build.baselineVersion >= 232) {
//                    // Apply syntax highlighting
//                    val fileType = FileTypeManager.getInstance().getFileTypeByExtension("properties")
//                    val highlighter = EditorHighlighterFactory.getInstance()
//                        .createEditorHighlighter(project, fileType)
//                    setHighlighter(highlighter)
//                }
                val fileType = FileTypeManager.getInstance().getFileTypeByExtension("properties")

                createEditorHighlighter(project, fileType)?.let { highlighter ->
                    setHighlighter(highlighter)
                }
            }
        }

        previewPanel.add(previewEditor!!.component, BorderLayout.CENTER)

        // Initial preview update
        SwingUtilities.invokeLater { updatePreview() }

        return previewPanel
    }

    private fun createEditorHighlighter(project: com.intellij.openapi.project.Project, fileType: com.intellij.openapi.fileTypes.FileType): com.intellij.openapi.editor.highlighter.EditorHighlighter? {
        return try {
            val factoryClass = Class.forName("com.intellij.openapi.editor.highlighter.EditorHighlighterFactory")
            val getInstanceMethod = factoryClass.getMethod("getInstance")
            val factory = getInstanceMethod.invoke(null)

            val createHighlighterMethod = factoryClass.getMethod("createEditorHighlighter",
                com.intellij.openapi.project.Project::class.java,
                com.intellij.openapi.fileTypes.FileType::class.java)

            createHighlighterMethod.invoke(factory, project, fileType) as com.intellij.openapi.editor.highlighter.EditorHighlighter
        } catch (e: Exception) {
            println("Could not create editor highlighter via reflection: ${e.message}")
            null
        }
    }

    private fun updatePreview() {
        previewEditor?.let { editor ->
            ApplicationManager.getApplication().invokeLater {
                // Get current patterns from table
                val currentPatterns = (0 until tableModel!!.rowCount)
                    .map { tableModel!!.getValueAt(it, 0).toString().trim() }
                    .filter { it.isNotEmpty() }

                // Get current settings
                val hideOnlyValues = hideOnlyValuesCheckBox?.isSelected ?: false
                val invisibleHighlight = invisibleHighlightCheckBox?.isSelected ?: false
                val highlightColor = colorPreviewPanel?.background ?: Color.LIGHT_GRAY

                // Create temporary settings for preview
                val tempSettings = SecretsMaskerSettings().apply {
                    patterns.clear()
                    patterns.addAll(currentPatterns)
                    this.hideOnlyValues = hideOnlyValues
                    this.invisibleHighlight = invisibleHighlight
                    this.highlightColor = highlightColor.rgb
                }

                // Apply masking with temporary settings
                val maskerService = service<SecretsMaskerService>()
                try {
                    maskerService.maskSensitiveData(editor, tempSettings)
                } catch (e: Exception) {
                    println("Preview update failed: ${e.message}")
                }
            }
        }
    }

    private fun addRow(value: String) {
        val rowIndex = tableModel!!.rowCount
        tableModel!!.addRow(arrayOf(value))
        undoManager.addEdit(AddRowUndoableEdit(tableModel!!, rowIndex, value))
        if (value.isEmpty()) {
            patternsTable!!.editCellAt(rowIndex, 0)
            patternsTable!!.editorComponent?.requestFocus()
        }
        updatePreview()
    }

    private fun deleteRow(row: Int) {
        if (row < 0 || row >= tableModel!!.rowCount) return
        val value = tableModel!!.getValueAt(row, 0).toString()
        tableModel!!.removeRow(row)
        undoManager.addEdit(DeleteRowUndoableEdit(tableModel!!, row, value))
        updatePreview()
    }

    private fun deleteSelectedRows() {
        val selectedRows = patternsTable!!.selectedRows.sortedDescending()
        if (selectedRows.isNotEmpty()) {
            undoManager.startCompoundEdit()
            selectedRows.forEach { if (it < tableModel!!.rowCount) deleteRow(it) }
            undoManager.endCompoundEdit()
            updateTableSelection(if (selectedRows.last() > 0) selectedRows.last() - 1 else 0)
        }
        updatePreview()
    }

    private fun updateTableSelection(row: Int) {
        patternsTable!!.clearSelection()
        if (tableModel!!.rowCount > 0 && row >= 0) {
            val newIndex = min(row, tableModel!!.rowCount - 1)
            patternsTable!!.setRowSelectionInterval(newIndex, newIndex)
        }
        patternsTable!!.requestFocus()
        patternsTable!!.revalidate()
        patternsTable!!.repaint()
    }

    override fun isModified(): Boolean {
        val settings = SecretsMaskerSettings.getInstance()

        val currentPatterns = (0 until tableModel!!.rowCount).map { tableModel!!.getValueAt(it, 0).toString().trim() }.filter { it.isNotEmpty() }
        val hideValueSettingChanged = hideOnlyValuesCheckBox?.isSelected != settings.hideOnlyValues
        val invisibleChanged = invisibleHighlightCheckBox?.isSelected != settings.invisibleHighlight

        return hideValueSettingChanged ||
                invisibleChanged ||
                colorModified ||
                settings.patterns != currentPatterns
    }

    override fun apply() {
        val settings = SecretsMaskerSettings.getInstance()
        settings.patterns.clear()
        (0 until tableModel!!.rowCount).map {
            tableModel!!.getValueAt(it, 0).toString().trim() }.filter { it.isNotEmpty() }.forEach { settings.patterns.add(it) }

        // Save the settings.
        hideOnlyValuesCheckBox?.let {
            settings.hideOnlyValues = it.isSelected
        }
        invisibleHighlightCheckBox?.let {
            settings.invisibleHighlight = it.isSelected
        }
        colorPreviewPanel?.let {
            settings.highlightColor = it.background.rgb
        }

        // Use invokeLater to ensure UI thread compatibility without coroutines
        ApplicationManager.getApplication().invokeLater {
            refreshAllEditors()
        }
    }

    private fun refreshAllEditors() {
        val maskerService = service<SecretsMaskerService>()

        // Directly call the method, similar to ToggleMasking implementation
        val editors = EditorFactory.getInstance().allEditors
        for (editor in editors) {
            maskerService.maskSensitiveData(editor)
        }

    }

    // Don't forget to dispose the editor when the configurable is disposed
    override fun disposeUIResources() {
        previewEditor?.let { editor ->
            EditorFactory.getInstance().releaseEditor(editor)
            previewEditor = null
        }
    }

    override fun reset() {
        val settings = SecretsMaskerSettings.getInstance()
        tableModel!!.rowCount = 0
        SecretsMaskerSettings.getInstance().patterns.distinct().forEach { tableModel!!.addRow(arrayOf(it)) }

        // Reset the settings state
        hideOnlyValuesCheckBox?.isSelected = settings.hideOnlyValues
        invisibleHighlightCheckBox?.isSelected = settings.invisibleHighlight
        colorPreviewPanel?.background = Color(settings.highlightColor)
    }
}

// Supporting undo/redo classes (unchanged)
class CompoundUndoManager {
    private val undoManager = UndoManager()
    private var compoundEdit: CompoundEdit? = null

    fun undo() { if (undoManager.canUndo()) undoManager.undo() }
    fun redo() { if (undoManager.canRedo()) undoManager.redo() }
    fun addEdit(edit: UndoableEdit) { compoundEdit?.addEdit(edit) ?: undoManager.addEdit(edit) }
    fun startCompoundEdit() { compoundEdit = CompoundEdit() }
    fun endCompoundEdit() { compoundEdit?.end(); compoundEdit?.let { undoManager.addEdit(it) }; compoundEdit = null }
    fun canUndo(): Boolean = undoManager.canUndo()
    fun canRedo(): Boolean = undoManager.canRedo()
}

class AddRowUndoableEdit(private val model: DefaultTableModel, private val row: Int, private val value: String) : AbstractUndoableEdit() {
    override fun undo() { super.undo(); model.removeRow(row) }
    override fun redo() { super.redo(); model.insertRow(row, arrayOf(value)) }
}

class DeleteRowUndoableEdit(private val model: DefaultTableModel, private val row: Int, private val value: String) : AbstractUndoableEdit() {
    override fun undo() { super.undo(); model.insertRow(row, arrayOf(value)) }
    override fun redo() { super.redo(); model.removeRow(row) }
}

class CellEditUndoableEdit(
    private val model: DefaultTableModel,
    private val row: Int,
    private val column: Int,
    private val oldValue: String,
    private val newValue: String
) : AbstractUndoableEdit() {
    override fun undo() { super.undo(); model.setValueAt(oldValue, row, column) }
    override fun redo() { super.redo(); model.setValueAt(newValue, row, column) }
}