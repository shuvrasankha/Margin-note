package com.shuva.jotter.data

/** A folder and how deep it sits (0 = top level). */
data class TreeRow(val folder: FolderEntity, val depth: Int)

/**
 * The parent each folder is actually shown under. A parent that is missing, deleted, a smart folder or part
 * of a loop (two devices each moved a folder into the other) doesn't count: that folder shows at the top.
 */
fun effectiveParents(folders: List<FolderEntity>): Map<String, String?> {
    val byId = folders.filter { !it.deleted }.associateBy { it.id }
    fun valid(id: String): String? {
        val parent = byId[id]?.parentId?.let { byId[it] } ?: return null
        if (parent.isSmart || parent.id == id) return null
        // Walk up; if we come back to [id], this folder is in a loop. A loop further up isn't this folder's problem.
        val seen = mutableSetOf<String>()
        var cur: FolderEntity? = parent
        while (cur != null && seen.add(cur.id)) {
            if (cur.id == id) return null
            cur = cur.parentId?.let { byId[it] }
        }
        return parent.id
    }
    return byId.keys.associateWith(::valid)
}

/**
 * Folders in display order: the default folder first, then regular folders with each one's subfolders
 * under it, then smart folders (always top level). Within each group: by [FolderEntity.sortOrder], then A to Z.
 */
fun folderTree(folders: List<FolderEntity>): List<TreeRow> {
    val live = folders.filter { !it.deleted }
    val parents = effectiveParents(live)
    val children = live.groupBy { parents[it.id] }
    val order = compareByDescending<FolderEntity> { it.isDefault }.thenBy { it.sortOrder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
    val result = mutableListOf<TreeRow>()
    fun add(folder: FolderEntity, depth: Int) {
        result += TreeRow(folder, depth)
        children[folder.id].orEmpty().filter { !it.isSmart }.sortedWith(order).forEach { add(it, depth + 1) }
    }
    val roots = children[null].orEmpty()
    roots.filter { !it.isSmart }.sortedWith(order).forEach { add(it, 0) }
    roots.filter { it.isSmart }.sortedWith(order).forEach { add(it, 0) }
    return result
}

/**
 * Folders that are reordered together: smart folders, or regular folders under the same parent.
 * The default folder always stays first, so it isn't in any group.
 */
fun reorderGroup(folder: FolderEntity, parents: Map<String, String?>): String? =
    if (folder.isDefault) null else if (folder.isSmart) "smart" else "in:" + parents[folder.id]

/** [id] and every folder shown inside it, at any depth. */
fun subtreeIds(folders: List<FolderEntity>, id: String): Set<String> {
    val parents = effectiveParents(folders)
    val result = mutableSetOf(id)
    var grew = true
    while (grew) {
        grew = false
        parents.forEach { (child, parent) -> if (parent in result && result.add(child)) grew = true }
    }
    return result
}
