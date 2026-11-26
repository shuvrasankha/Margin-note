package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.Aisle
import com.margin.app.data.aisleOf
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/**
 * Shows where a shopping list's items will go, aisle by aisle, before sorting it. Tapping an item picks its
 * aisle; that choice is remembered for the item from then on ([onChoose]).
 */
@Composable
fun AisleSortDialog(items: List<String>, mine: Map<String, Aisle>, onChoose: (String, Aisle) -> Unit, onSort: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var choices by remember { mutableStateOf(mine) }
    var fixing by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            val item = fixing
            if (item == null) {
                Text(stringResource(R.string.aisle_sort_sort_by_aisle), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
                Text(
                    stringResource(R.string.aisle_sort_items_still_to_get_grouped),
                    Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted,
                )
                items.groupBy { aisleOf(it, choices) }.toSortedMap().forEach { (aisle, inAisle) ->
                    SectionLabel(aisle.label)
                    inAisle.forEach { text ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { fixing = text }
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .semantics { contentDescription = context.getString(R.string.aisle_item_desc, text, aisle.label) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text, Modifier.weight(1f), style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.common_change), style = JType.small, color = jc.accent)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(stringResource(R.string.common_cancel), onDismiss, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.aisle_sort_sort_list), onSort, Modifier.weight(1f))
                }
            } else {
                Text(stringResource(R.string.aisle_for_item, item), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink, maxLines = 2)
                Text(stringResource(R.string.aisle_sort_remembered_for_this_item_on), Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
                val now = aisleOf(item, choices)
                Aisle.entries.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) {
                            onChoose(item, a)
                            choices = choices + (com.margin.app.data.itemKey(item) to a)
                            fixing = null
                        }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.label, Modifier.weight(1f), style = JType.item, color = if (a == now) jc.accent else jc.ink)
                        if (a == now) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)) { TextAction(stringResource(R.string.common_back), { fixing = null }) }
            }
        }
    }
}

/** A shopping cart. */
const val ICON_AISLE = "M3 4h2l2.4 10.2a1 1 0 0 0 1 .8h8.8a1 1 0 0 0 1-.8L20 7H6M9 19.5a.5.5 0 1 0 0 1a.5.5 0 0 0 0-1M17 19.5a.5.5 0 1 0 0 1a.5.5 0 0 0 0-1"
