package com.keavors.gallery.data

/**
 * What was just changed.
 *
 * Carried alongside every edit for one reason: a slider dragged across the
 * screen reports a hundred values, and an undo that steps back through all
 * hundred of them is not an undo. Consecutive changes of the same thing collapse
 * into one step; a different thing, or a button rather than a slider, starts a
 * new one.
 */
data class EditStep(val kind: Kind, val detail: String = "") {

    enum class Kind { TURN, FLIP, CROP, STRAIGHTEN, ADJUST, FILTER, MARK, RESET }

    /**
     * True for the things that are dragged rather than pressed.
     *
     * Turning twice is two steps and has to be, because both turns are
     * deliberate. Dragging a slider twice is one continuous act that happens to
     * have been reported twice.
     */
    val continuous: Boolean
        get() = kind == Kind.CROP || kind == Kind.STRAIGHTEN ||
            kind == Kind.ADJUST || kind == Kind.FILTER
}

/**
 * Everywhere the picture has been, so that any of it can be taken back.
 *
 * The edits are a small immutable value, so history is nothing more than a list
 * of them — no snapshots of pixels, no memory to speak of, and no way for an
 * undo to disagree with what it undoes.
 */
data class EditHistory(
    val present: EditOps = EditOps.None,
    val past: List<EditOps> = emptyList(),
    val future: List<EditOps> = emptyList(),
    val lastStep: EditStep? = null,
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    val canRedo: Boolean get() = future.isNotEmpty()

    /**
     * The edits after this change.
     *
     * A change that changes nothing is not a step, and a change that continues
     * the one before it replaces it rather than joining it.
     */
    fun with(next: EditOps, step: EditStep): EditHistory {
        if (next == present) return this

        val continues = step.continuous && step == lastStep
        return EditHistory(
            present = next,
            past = if (continues) past else (past + present).takeLast(DEPTH),
            // Going somewhere new is what makes the way forward no longer exist.
            future = emptyList(),
            lastStep = step,
        )
    }

    fun undone(): EditHistory {
        if (past.isEmpty()) return this
        return EditHistory(
            present = past.last(),
            past = past.dropLast(1),
            future = listOf(present) + future,
            // Whatever comes next is a new step, even if it is more of the same
            // slider: undoing is a full stop.
            lastStep = null,
        )
    }

    fun redone(): EditHistory {
        if (future.isEmpty()) return this
        return EditHistory(
            present = future.first(),
            past = past + present,
            future = future.drop(1),
            lastStep = null,
        )
    }

    companion object {
        /**
         * How far back it goes.
         *
         * Each step is a handful of numbers, so this is about what anyone can
         * hold in their head rather than about memory.
         */
        const val DEPTH = 50
    }
}
