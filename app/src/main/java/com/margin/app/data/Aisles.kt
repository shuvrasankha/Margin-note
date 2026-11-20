package com.margin.app.data

/** Where in a shop an item is, in the order a typical shop is walked. */
enum class Aisle(val label: String) {
    PRODUCE("Fruit & veg"),
    BAKERY("Bakery"),
    MEAT("Meat & fish"),
    DAIRY("Dairy & eggs"),
    PANTRY("Pantry"),
    BREAKFAST("Breakfast"),
    SNACKS("Snacks & sweets"),
    DRINKS("Drinks"),
    FROZEN("Frozen"),
    HOUSEHOLD("Household"),
    PERSONAL("Personal care"),
    BABY("Baby"),
    PETS("Pets"),
    OTHER("Other"),
}

private val WORDS: Map<String, Aisle> = buildMap {
    fun add(aisle: Aisle, words: String) = words.split(',').forEach { put(it.trim(), aisle) }
    add(Aisle.PRODUCE, "apple, banana, orange, lemon, lime, grape, pear, peach, plum, mango, pineapple, melon, watermelon, berry, strawberry, " +
        "blueberry, raspberry, cherry, kiwi, avocado, tomato, potato, onion, garlic, ginger, carrot, cucumber, lettuce, salad, spinach, kale, " +
        "cabbage, broccoli, cauliflower, pepper, chilli, chili, courgette, zucchini, aubergine, eggplant, mushroom, celery, leek, corn, pea, bean sprout, " +
        "herb, coriander, cilantro, parsley, basil, mint, fruit, veg, vegetable, beetroot, radish, pumpkin, squash, sweet potato, spring onion, okra, coconut")
    add(Aisle.BAKERY, "bread, loaf, baguette, roll, bun, bagel, croissant, muffin, cake, pastry, tortilla, wrap, pita, naan, sourdough, doughnut, donut")
    add(Aisle.MEAT, "chicken, beef, pork, lamb, mince, steak, sausage, bacon, ham, turkey, salami, fish, salmon, tuna steak, cod, prawn, shrimp, " +
        "meat, mutton, fillet, chop, burger patty")
    add(Aisle.DAIRY, "milk, egg, butter, cheese, yogurt, yoghurt, cream, sour cream, cream cheese, paneer, curd, ghee, margarine, feta, mozzarella, " +
        "parmesan, cheddar, oat milk, almond milk, soy milk")
    add(Aisle.PANTRY, "rice, pasta, spaghetti, noodle, flour, sugar, salt, oil, olive oil, vinegar, sauce, ketchup, mayo, mayonnaise, mustard, " +
        "soy sauce, spice, cumin, turmeric, stock, broth, lentil, dal, chickpea, bean, tin, can, tuna, tomato paste, honey, jam, peanut butter, " +
        "baking powder, yeast, oats, nut, almond, cashew, raisin, soup, curry paste, masala")
    add(Aisle.BREAKFAST, "cereal, muesli, granola, cornflakes, porridge, pancake mix, syrup, coffee, tea, cocoa")
    add(Aisle.SNACKS, "chips, crisps, biscuit, cookie, cracker, chocolate, candy, sweets, popcorn, snack, ice cream cone, gum")
    add(Aisle.DRINKS, "water, juice, soda, cola, lemonade, beer, wine, sparkling water, energy drink, squash drink, kombucha")
    add(Aisle.FROZEN, "frozen, ice cream, ice, pizza, fish fingers, frozen peas, chips frozen")
    add(Aisle.HOUSEHOLD, "toilet paper, toilet roll, kitchen roll, paper towel, tissue, detergent, washing up liquid, dish soap, soap powder, " +
        "laundry, bleach, cleaner, sponge, bin bag, trash bag, foil, cling film, battery, batteries, light bulb, bulb, candle, matches, napkin")
    add(Aisle.PERSONAL, "shampoo, conditioner, soap, toothpaste, toothbrush, deodorant, razor, lotion, sunscreen, plaster, painkiller, " +
        "paracetamol, vitamin, floss, cotton, tampon, pad, makeup, body wash, hand wash")
    add(Aisle.BABY, "nappy, nappies, diaper, wipes, baby food, formula")
    add(Aisle.PETS, "cat food, dog food, cat litter, litter, pet food, dog treat")
}

private fun singular(w: String) = when {
    w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
    w.length > 3 && (w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
    w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
    else -> w
}

/** An item's words, without quantities and units: "2 kg Potatoes" → "potato". */
fun itemKey(text: String): String = text.lowercase()
    .replace(Regex("""[^\p{L}\s]"""), " ")
    .split(Regex("\\s+")).filter { it.isNotEmpty() && it !in UNITS }
    .joinToString(" ") { singular(it) }

private val UNITS = setOf("x", "kg", "g", "gram", "grams", "lb", "lbs", "l", "ml", "litre", "liter", "pack", "packs", "bag", "bags",
    "bottle", "bottles", "box", "boxes", "dozen", "of", "a", "some", "pcs", "large", "small", "big")

/**
 * Where [text] is likely found: the user's own choice for it ([mine], by [itemKey]) first, then the longest
 * phrase of its words in the built-in list ("ice cream" over "cream", "olive oil" over "oil"), else [Aisle.OTHER].
 */
fun aisleOf(text: String, mine: Map<String, Aisle> = emptyMap()): Aisle {
    val key = itemKey(text)
    mine[key]?.let { return it }
    val words = key.split(' ').filter { it.isNotEmpty() }
    for (len in words.size downTo 1) for (start in 0..words.size - len) {
        WORDS[words.subList(start, start + len).joinToString(" ")]?.let { return it }
    }
    return Aisle.OTHER
}

private val AISLE_LABELS = Aisle.entries.map { it.label.lowercase() }.toSet() + "done"

/**
 * A list's tasks grouped under aisle headings in shop order, tasks still to do first; ticked ones go last under
 * "Done". Headings from an earlier sort are replaced; other lines stay, those before the first task above it.
 */
fun byAisle(blocks: List<Block>, mine: Map<String, Aisle> = emptyMap()): List<Block> {
    val isTask = { b: Block -> b.type == BlockType.CHECK && !b.isObject }
    val isOldHeading = { b: Block -> b.type == BlockType.SUBHEADING && b.text.trim().lowercase() in AISLE_LABELS }
    val first = blocks.indexOfFirst(isTask)
    if (first < 0) return blocks
    val head = blocks.take(first).filterIndexed { i, b -> i == 0 || !isOldHeading(b) }
    val rest = blocks.drop(first)
    val tasks = rest.filter(isTask)
    val others = rest.filter { !isTask(it) && !isOldHeading(it) }
    val out = head.toMutableList()
    fun heading(text: String) = Block(type = BlockType.SUBHEADING, text = text)
    tasks.filter { !it.checked }.groupBy { aisleOf(it.text, mine) }.toSortedMap().forEach { (aisle, items) ->
        out += heading(aisle.label)
        out += items.map { it.copy(indent = 0) }
    }
    val done = tasks.filter { it.checked }
    if (done.isNotEmpty()) { out += heading("Done"); out += done.map { it.copy(indent = 0) } }
    // Don't leave a blank line between the title and the first heading.
    return out + others.dropWhile { it.type == BlockType.BODY && it.text.isBlank() && !it.isObject }.let { if (it.isEmpty() && out.last().type != BlockType.BODY) listOf(Block()) else it }
}

/** The aisles the user picked for items the built-in list gets wrong or doesn't know, on this device. */
@javax.inject.Singleton
class AisleChoices @javax.inject.Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context) {
    private val prefs = context.getSharedPreferences("aisle_choices", android.content.Context.MODE_PRIVATE)

    fun all(): Map<String, Aisle> = prefs.all.mapNotNull { (k, v) -> runCatching { Aisle.valueOf(v as String) }.getOrNull()?.let { k to it } }.toMap()

    /** From now on [text] (and the same item written differently: "2 Apples", "apple") goes in [aisle]. */
    fun set(text: String, aisle: Aisle) {
        val key = itemKey(text).ifEmpty { return }
        prefs.edit().putString(key, aisle.name).apply()
    }
}
