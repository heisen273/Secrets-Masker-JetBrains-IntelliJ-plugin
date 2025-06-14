package com.secretsmasker.settings

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

    override fun getDisplayName(): String = "Secrets Masker"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout(10, 10)).apply {
            border = EmptyBorder(10, 10, 10, 10)
        }
        val currentSettings = SecretsMaskerSettings.getInstance()

        // Create a vertical panel for all settings
        val settingsPanel = JPanel()
        settingsPanel.layout = BoxLayout(settingsPanel, BoxLayout.Y_AXIS)
        settingsPanel.border = EmptyBorder(0, 0, 15, 0)

        // Instructions
        val instructionLabel = JLabel(
            "<html><b>Configure patterns to match secrets</b><br>" +
                    "Each pattern is compiled as a regular expression.<br>" +
                    "Double-click to edit, press Enter to save, or Backspace to remove rows.</html>"
        ).apply {
            border = EmptyBorder(0, 0, 10, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        settingsPanel.add(instructionLabel)

        // Pattern options panel
        val checkboxOptions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS) // <-- vertical layout
            alignmentX = Component.LEFT_ALIGNMENT
            border = EmptyBorder(0, 0, 10, 0)
        }

        hideOnlyValuesCheckBox = JCheckBox("Hide only values after patterns").apply {
            toolTipText = "Only hide the value after '=' or ':'"
            isSelected = currentSettings.hideOnlyValues
        }
        checkboxOptions.add(hideOnlyValuesCheckBox!!)

        invisibleHighlightCheckBox = JCheckBox("Invisible highlight").apply {
            toolTipText = "Make highlighted text blend with background"
            isSelected = currentSettings.invisibleHighlight
        }
        checkboxOptions.add(invisibleHighlightCheckBox!!)


        settingsPanel.add(checkboxOptions)

        // Appearance settings panel
        val appearancePanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Color picker
        appearancePanel.add(JLabel("Highlight Color:"))

        val colorChooser = JColorChooser(Color(currentSettings.highlightColor))
        // Remove all chooser panels except Swatches
        val swatchesPanel = colorChooser.chooserPanels.first()
        for (panel in colorChooser.chooserPanels) {
            if (panel != swatchesPanel) {
                colorChooser.removeChooserPanel(panel)
            }
        }
        colorPreviewPanel = JPanel().apply {
            background = Color(currentSettings.highlightColor)
            preferredSize = Dimension(20, 20)
            border = BorderFactory.createLineBorder(Color.GRAY)

            addMouseListener(object : MouseListener {

                override fun mouseClicked(e: MouseEvent?) {
                    // Create and show dialog manually
                    val dialog = JColorChooser.createDialog(
                        mainPanel,
                        "Select Highlight Color",
                        true,
                        colorChooser,
                        {
                            val selectedColor = colorChooser.color
                            colorPreviewPanel!!.background = selectedColor
                            currentSettings.highlightColor = selectedColor.rgb
                            colorPreviewPanel!!.repaint()
                            colorModified = true
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
        
        appearancePanel.add(colorPreviewPanel)

        settingsPanel.add(appearancePanel)

        mainPanel!!.add(settingsPanel, BorderLayout.NORTH)

        // Table setup
        tableModel = object : DefaultTableModel(arrayOf("Regular Expression Patterns"), 0) {
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

            // Ensure editor gains focus when editing starts
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
            columnModel.getColumn(0).preferredWidth = 400
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
            preferredSize = Dimension(450, 150)
            minimumSize = Dimension(450, 100)
        }
        mainPanel!!.add(scrollPane, BorderLayout.CENTER)

        // Buttons at bottom
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            border = EmptyBorder(10, 0, 0, 0)
        }
        buttonPanel.add(JButton("Add Pattern").apply {
            addActionListener { addRow("") }
        })
        buttonPanel.add(JButton("Remove Pattern").apply {
            addActionListener { deleteSelectedRows() }
        })
        mainPanel!!.add(buttonPanel, BorderLayout.SOUTH)

        return mainPanel!!
    }

    private fun addRow(value: String) {
        val rowIndex = tableModel!!.rowCount
        tableModel!!.addRow(arrayOf(value))
        undoManager.addEdit(AddRowUndoableEdit(tableModel!!, rowIndex, value))
        if (value.isEmpty()) {
            patternsTable!!.editCellAt(rowIndex, 0)
            patternsTable!!.editorComponent?.requestFocus()
        }
    }

    private fun deleteRow(row: Int) {
        if (row < 0 || row >= tableModel!!.rowCount) return
        val value = tableModel!!.getValueAt(row, 0).toString()
        tableModel!!.removeRow(row)
        undoManager.addEdit(DeleteRowUndoableEdit(tableModel!!, row, value))
    }

    private fun deleteSelectedRows() {
        val selectedRows = patternsTable!!.selectedRows.sortedDescending()
        if (selectedRows.isNotEmpty()) {
            undoManager.startCompoundEdit()
            selectedRows.forEach { if (it < tableModel!!.rowCount) deleteRow(it) }
            undoManager.endCompoundEdit()
            updateTableSelection(if (selectedRows.last() > 0) selectedRows.last() - 1 else 0)
        }
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