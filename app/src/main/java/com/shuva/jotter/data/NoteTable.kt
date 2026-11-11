package com.shuva.jotter.data

import kotlinx.serialization.Serializable

/** A table in a note: [rows] of cell text, the first row is the header. Every row has the same number of cells. */
@Serializable
data class NoteTable(val rows: List<List<String>>) {
    val rowCount get() = rows.size
    val columnCount get() = rows.firstOrNull()?.size ?: 0

    /** Rows padded or cut to one width, at least one cell; cells kept on one line. */
    fun normalized(): NoteTable {
        val width = rows.maxOfOrNull { it.size }?.coerceIn(1, MAX_TABLE_COLUMNS) ?: 1
        val clean = rows.ifEmpty { listOf(emptyList()) }.take(MAX_TABLE_ROWS)
            .map { r -> List(width) { c -> r.getOrNull(c).orEmpty().replace('\n', ' ') } }
        return NoteTable(clean)
    }

    fun withCell(row: Int, column: Int, text: String) =
        NoteTable(rows.mapIndexed { r, cells -> if (r != row) cells else cells.mapIndexed { c, t -> if (c == column) text.replace('\n', ' ') else t } })

    fun addRow(at: Int = rowCount) = if (rowCount >= MAX_TABLE_ROWS) this else
        NoteTable(rows.toMutableList().apply { add(at.coerceIn(0, size), List(columnCount) { "" }) })

    fun addColumn(at: Int = columnCount) = if (columnCount >= MAX_TABLE_COLUMNS) this else
        NoteTable(rows.map { it.toMutableList().apply { add(at.coerceIn(0, size), "") } })

    fun removeRow(at: Int) = if (rowCount <= 1 || at !in rows.indices) this else NoteTable(rows.filterIndexed { i, _ -> i != at })

    fun removeColumn(at: Int) = if (columnCount <= 1 || at !in 0 until columnCount) this else
        NoteTable(rows.map { r -> r.filterIndexed { i, _ -> i != at } })

    /** For search, previews and plain-text sharing: one row per line, cells split by " | ". */
    val plainText get() = rows.filter { r -> r.any { it.isNotBlank() } }.joinToString("\n") { it.joinToString(" | ") { c -> c.trim() } }

    /** As a Markdown table (pipes in cells escaped). */
    fun markdown(): String {
        fun row(r: List<String>) = r.joinToString(" | ", "| ", " |") { it.trim().replace("|", "\\|") }
        val head = rows.firstOrNull() ?: return ""
        return (listOf(row(head), row(List(head.size) { "---" })) + rows.drop(1).map(::row)).joinToString("\n")
    }

    companion object {
        /** A new table: a header row and two empty rows, [columns] wide. */
        fun blank(columns: Int = 3, rows: Int = 3) = NoteTable(List(rows) { List(columns) { "" } })
    }
}

const val MAX_TABLE_ROWS = 50
const val MAX_TABLE_COLUMNS = 8
