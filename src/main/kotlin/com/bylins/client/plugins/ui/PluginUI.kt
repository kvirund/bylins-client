package com.bylins.client.plugins.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Universal UI primitives for plugin tabs.
 * Plugins build their UI using these nodes, and the client renders them.
 */
sealed class PluginUINode {
    // Containers
    data class Column(
        val children: List<PluginUINode>,
        val spacing: Int = 4
    ) : PluginUINode()

    data class Row(
        val children: List<PluginUINode>,
        val spacing: Int = 4
    ) : PluginUINode()

    data class Box(
        val child: PluginUINode,
        val padding: Int = 8
    ) : PluginUINode()

    data class Scrollable(
        val child: PluginUINode,
        val maxHeight: Int? = null
    ) : PluginUINode()

    // Text primitives
    data class Text(
        val text: String,
        val style: TextStyle = TextStyle.BODY
    ) : PluginUINode()

    // Interactive primitives
    data class Button(
        val text: String,
        val onClick: () -> Unit,
        val enabled: Boolean = true
    ) : PluginUINode()

    data class TextField(
        val value: String,
        val onValueChange: (String) -> Unit,
        val label: String? = null,
        val placeholder: String? = null
    ) : PluginUINode()

    data class Checkbox(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val label: String
    ) : PluginUINode()

    data class Slider(
        val value: Float,
        val onValueChange: (Float) -> Unit,
        val range: ClosedFloatingPointRange<Float> = 0f..1f,
        val label: String? = null
    ) : PluginUINode()

    data class ProgressBar(
        val progress: Float,
        val label: String? = null
    ) : PluginUINode()

    data class Dropdown(
        val selectedIndex: Int,
        val options: List<String>,
        val onSelect: (Int) -> Unit,
        val label: String? = null
    ) : PluginUINode()

    // Layout helpers
    data class Divider(val thickness: Int = 1) : PluginUINode()
    data class Spacer(val height: Int) : PluginUINode()

    // Empty node
    object Empty : PluginUINode()

    /**
     * Text styles for Text nodes
     */
    enum class TextStyle {
        TITLE,      // Large, bold
        SUBTITLE,   // Medium, semi-bold
        BODY,       // Normal text
        CAPTION,    // Small, muted
        MONOSPACE   // Fixed-width font
    }
}

/**
 * Interface for a plugin tab.
 * Plugins create tabs through PluginAPI.createTab() and update the content StateFlow.
 */
interface PluginTab {
    /** Unique tab identifier */
    val id: String

    /** Tab display title */
    val title: String

    /** Tab content - plugin updates this to change UI */
    val content: MutableStateFlow<PluginUINode>

    /** Close the tab */
    fun close()
}

/**
 * Internal implementation of PluginTab
 */
internal class PluginTabImpl(
    override val id: String,
    override val title: String,
    private val onClose: (String) -> Unit
) : PluginTab {
    override val content: MutableStateFlow<PluginUINode> = MutableStateFlow(PluginUINode.Empty)

    override fun close() {
        onClose(id)
    }
}
