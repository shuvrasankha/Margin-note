package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class VoiceCommandTest {
    // A Wednesday afternoon.
    private val now = LocalDateTime.of(2026, 11, 18, 14, 10)
    private fun parse(s: String) = VoiceCommands.parse(s, now)
    private fun remind(what: String, at: LocalDateTime, repeat: Repeat = Repeat.NEVER) = VoiceCommand.Remind(what, at, repeat)

    @Test fun reminderTomorrowAtFiveEitherOrder() {
        val want = remind("Call Sam", LocalDateTime.of(2026, 11, 19, 17, 0))
        assertEquals(want, parse("Remind me to call Sam tomorrow at 5"))
        assertEquals(want, parse("remind me tomorrow at 5 to call Sam"))
        assertEquals(want, parse("Remind me to call Sam tomorrow at 5 p.m."))
        assertEquals(want, parse("remind me to call Sam tomorrow at 5:00 PM"))
        assertEquals(want, parse("Remind me to call Sam tomorrow at 17:00"))
    }

    @Test fun keepsWordsThatAreNotTimes() {
        assertEquals(remind("Meet Sam at the station", LocalDateTime.of(2026, 11, 18, 18, 0)), parse("Remind me to meet Sam at the station at 6"))
    }

    @Test fun bareHourIsTheNextToCome() {
        assertEquals(LocalDateTime.of(2026, 11, 18, 20, 0), (parse("remind me to lock up at 8") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 19, 9, 30), (parse("remind me to stretch at 9:30 am") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 18, 14, 30), (parse("remind me at 2:30 pm to leave") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 19, 12, 0), (parse("remind me at noon to eat") as VoiceCommand.Remind).at)
    }

    @Test fun relativeTimes() {
        assertEquals(remind("Check the oven", LocalDateTime.of(2026, 11, 18, 14, 30)), parse("Remind me in 20 minutes to check the oven"))
        assertEquals(LocalDateTime.of(2026, 11, 18, 16, 10), (parse("remind me to stand up in two hours") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 18, 14, 40), (parse("remind me in half an hour to call") as VoiceCommand.Remind).at)
    }

    @Test fun partsOfTheDay() {
        assertEquals(LocalDateTime.of(2026, 11, 18, 20, 0), (parse("remind me tonight to take the bins out") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 19, 9, 0), (parse("remind me tomorrow morning to email Jo") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 18, 19, 0), (parse("remind me at 7 in the evening to cook") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 11, 19, 9, 0), (parse("remind me tomorrow to pay rent") as VoiceCommand.Remind).at)
    }

    @Test fun daysAndDates() {
        assertEquals(remind("Book the dentist", LocalDateTime.of(2026, 11, 20, 9, 0)), parse("remind me on Friday to book the dentist"))
        assertEquals(LocalDateTime.of(2026, 11, 25, 15, 0), (parse("remind me next Wednesday at 3 to call") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2026, 12, 3, 9, 0), (parse("remind me on December 3rd to renew the car") as VoiceCommand.Remind).at)
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), (parse("remind me on the 5th of January to file taxes") as VoiceCommand.Remind).at)
    }

    @Test fun repeats() {
        assertEquals(remind("Take vitamins", LocalDateTime.of(2026, 11, 19, 8, 0), Repeat.DAILY), parse("Remind me every day at 8 am to take vitamins"))
        assertEquals(remind("Water the plants", LocalDateTime.of(2026, 11, 23, 9, 0), Repeat.WEEKLY), parse("remind me every Monday to water the plants"))
        assertEquals(Repeat.MONTHLY, (parse("remind me to pay the bill monthly") as VoiceCommand.Remind).repeat)
    }

    @Test fun setAReminderFor() {
        assertEquals(remind("Call mom", LocalDateTime.of(2026, 11, 18, 18, 0)), parse("Set a reminder for 6 pm to call mom"))
        assertEquals(remind("Pay rent", LocalDateTime.of(2026, 11, 19, 9, 0)), parse("Hey Google, can you please set a reminder for tomorrow to pay rent"))
    }

    @Test fun noTimeIsInAnHour() {
        assertEquals(remind("Call mum", LocalDateTime.of(2026, 11, 18, 15, 10)), parse("remind me to call mum"))
    }

    @Test fun shoppingList() {
        val want = VoiceCommand.AddToList("Shopping list", listOf("Milk", "Eggs", "Bread"))
        assertEquals(want, parse("Add milk, eggs and bread to my shopping list"))
        assertEquals(want, parse("add milk, eggs, and bread to the shopping list."))
        assertEquals(want, parse("Ok Google, add milk, eggs & bread to my shopping list"))
        assertEquals(want, parse("add to my shopping list milk, eggs and bread"))
        assertEquals(VoiceCommand.AddToList("Shopping list", listOf("Batteries")), parse("I need to buy batteries"))
        assertEquals(VoiceCommand.AddToList("To-do list", listOf("Fix the tap")), parse("put fix the tap on my to do list"))
        assertEquals(VoiceCommand.AddToList("Grocery list", listOf("Apples")), parse("add apples to grocery list"))
    }

    @Test fun saidIntoAListWidget() {
        assertEquals(VoiceCommand.AddToList("Groceries", listOf("Milk", "Eggs")), VoiceCommands.parse("milk and eggs", now, "Groceries"))
        assertEquals(VoiceCommand.AddToList("Groceries", listOf("Tomatoes")), VoiceCommands.parse("Add tomatoes.", now, "Groceries"))
        // Other commands still work from the widget.
        assertEquals(VoiceCommand.AddToList("To-do list", listOf("Call Jo")), VoiceCommands.parse("add call Jo to my to do list", now, "Groceries"))
        assertEquals(LocalDateTime.of(2026, 11, 18, 20, 0), (VoiceCommands.parse("remind me to lock up at 8", now, "Groceries") as VoiceCommand.Remind).at)
    }

    @Test fun listKeysMatch() {
        assertEquals(VoiceCommands.listKey("Shopping list"), VoiceCommands.listKey("my shopping"))
        assertEquals("todo", VoiceCommands.listKey("To-do"))
    }

    @Test fun notes() {
        assertEquals(VoiceCommand.Note("The wifi code is on the fridge"), parse("Take a note the wifi code is on the fridge"))
        assertEquals(VoiceCommand.Note("Buy a bigger desk someday?"), parse("note to self: buy a bigger desk someday?").let { it })
        assertEquals(VoiceCommand.Note("Ideas for the trip"), parse("ideas for the trip"))
        assertEquals(null, parse("  "))
        assertEquals(VoiceCommand.Note("Gym opens at 6 a.m. o'clock sharp"), parse("gym opens at 6 a.m. o'clock sharp"))
    }

    private fun note(id: String, title: String, tasks: Int = 0, cipher: String? = null, updated: Long = 0) = NoteEntity(
        id = id, folderId = DEFAULT_FOLDER_ID, title = title, searchText = title, createdAt = 0, updatedAt = updated, cipher = cipher,
        blocks = listOf(Block(type = BlockType.TITLE, text = title)) + List(tasks) { Block(type = BlockType.CHECK, text = "t$it") },
    )

    @Test fun findsTheListByName() {
        val notes = listOf(
            note("a", "Shopping notes"), note("b", "Shopping", tasks = 2), note("c", "shopping list", updated = 5),
            note("login-x", "Shopping list", tasks = 1), note("d", "Shopping list", tasks = 1, cipher = "sealed"),
        )
        assertEquals("b", VoiceActions.findList(notes, "Shopping list")?.id)
        assertEquals(null, VoiceActions.findList(notes, "To-do list"))
    }
}
