package com.shuva.jotter.data

/** Saved templates are notes with this id prefix, kept in the Notes folder but listed nowhere. */
const val TEMPLATE_PREFIX = "template-"

fun isTemplate(note: NoteEntity) = isTemplate(note.id)
fun isTemplate(id: String) = id.startsWith(TEMPLATE_PREFIX)

/** A folder's [FolderEntity.template] value: "builtin:NAME" for a built-in template, else the saved template's note id. */
fun templateKey(t: NoteTemplate) = "builtin:" + t.name

fun builtInTemplate(key: String) = NoteTemplate.entries.firstOrNull { templateKey(it) == key }

/** Ready-made skeletons from "Add to note": headings with empty lines, lists and tables to fill in. */
enum class NoteTemplate(val label: String) {
    LAB("Lab note"), MEETING("Meeting"), LECTURE("Lecture"), DAILY("Daily plan"), RECIPE("Recipe"), TRIP("Trip");

    fun blocks(): List<Block> = buildList {
        fun heading(name: String) = add(Block(type = BlockType.HEADING, text = name))
        fun section(name: String, type: BlockType = BlockType.BODY, lines: Int = 1) { heading(name); repeat(lines) { add(Block(type = type)) } }
        fun table(vararg header: String, rows: Int = 3) =
            add(Block(text = ATTACHMENT_CHAR.toString(), table = NoteTable(listOf(header.toList()) + List(rows) { List(header.size) { "" } })))
        when (this@NoteTemplate) {
            LAB -> {
                section("Aim"); section("Materials"); section("Method")
                heading("Observations"); table("Trial", "Reading", "Notes")
                section("Result"); section("Conclusion")
            }
            MEETING -> {
                section("Attendees", BlockType.BULLET); section("Agenda", BlockType.NUMBERED)
                section("Notes"); section("Action items", BlockType.CHECK)
            }
            LECTURE -> {
                section("Topic"); section("Key points", BlockType.BULLET)
                section("Questions", BlockType.BULLET); section("Summary")
            }
            DAILY -> {
                section("Top 3", BlockType.CHECK, lines = 3)
                heading("Schedule"); table("Time", "Task", rows = 4)
                section("Notes")
            }
            RECIPE -> {
                section("Serves"); section("Ingredients", BlockType.BULLET)
                section("Steps", BlockType.NUMBERED); section("Notes")
            }
            TRIP -> {
                section("Dates")
                heading("Itinerary"); table("Day", "Place", "Plan")
                section("Bookings", BlockType.BULLET); section("Packing", BlockType.CHECK)
            }
        }
    }
}
