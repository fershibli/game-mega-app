package io.github.fershibli.megagames.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.ui.theme.LocalPalette

/** A block of a page: a heading, a paragraph, a list item or a table. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val marker: String, val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
}

/**
 * The little markdown the game pages use: headings, paragraphs, bulleted and numbered lists,
 * tables, **bold** and [links](...) (shown as their text). Nothing else is needed for them.
 */
object Markdown {
    private val numbered = Regex("""^(\d+)\.\s+(.*)$""")

    fun parse(page: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val paragraph = StringBuilder()
        val table = ArrayList<List<String>>()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
            if (table.isNotEmpty()) blocks += MdBlock.Table(table.toList())
            table.clear()
        }
        for (raw in page.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("|") -> {
                    if (paragraph.isNotBlank()) { blocks += MdBlock.Paragraph(paragraph.toString().trim()); paragraph.clear() }
                    val cells = line.trim('|').split('|').map { it.trim() }
                    if (cells.any { c -> c.isNotEmpty() && !c.all { it == '-' || it == ':' } }) table += cells
                }
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += MdBlock.Heading(level, line.drop(level).trim())
                }
                line.startsWith("- ") || line.startsWith("* ") -> { flush(); blocks += MdBlock.Item("•", line.drop(2).trim()) }
                numbered.matches(line) -> {
                    flush()
                    val (n, text) = numbered.find(line)!!.destructured
                    blocks += MdBlock.Item("$n.", text)
                }
                else -> { if (table.isNotEmpty()) flush(); paragraph.append(' ').append(line) }
            }
        }
        flush()
        return blocks
    }

    /** **bold** becomes bold, [text](link) becomes text, `code` loses its backticks. */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        val plain = text.replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1").replace("`", "")
        var bold = false
        for ((i, part) in plain.split("**").withIndex()) {
            if (i > 0) bold = !bold
            if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        }
    }
}

/** A page of [Markdown], in big readable text; the page's own title (# ...) is left to the screen. */
@Composable
fun MarkdownView(page: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in Markdown.parse(page)) {
            when (block) {
                is MdBlock.Heading -> if (block.level > 1) Text(
                    Markdown.inline(block.text), Modifier.padding(top = 8.dp),
                    style = if (block.level == 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = p.primary,
                )
                is MdBlock.Paragraph -> Text(Markdown.inline(block.text), style = MaterialTheme.typography.bodyLarge, color = p.text)
                is MdBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(block.marker, Modifier.width(28.dp), style = MaterialTheme.typography.bodyLarge, color = p.primary)
                    Text(Markdown.inline(block.text), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = p.text)
                }
                is MdBlock.Table -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                    block.rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth().background(if (r % 2 == 0) p.surface else p.background).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            row.forEach { cell ->
                                Text(
                                    Markdown.inline(if (r == 0) "**$cell**" else cell), Modifier.weight(1f).padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = p.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
