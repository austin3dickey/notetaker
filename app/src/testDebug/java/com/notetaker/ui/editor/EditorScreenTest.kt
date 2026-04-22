package com.notetaker.ui.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.notetaker.data.ChecklistItem
import com.notetaker.data.Note
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val note = Note(id = 1L, title = "lunch", createdAt = 0L, updatedAt = 0L)

    private val noTextEditAction = SemanticsMatcher("has no SetText action") { node ->
        SemanticsActions.SetText !in node.config
    }

    private fun item(
        id: Long,
        text: String,
        checked: Boolean = false,
        position: Int = id.toInt(),
    ) = ChecklistItem(
        id = id, noteId = 1L, text = text, checked = checked, position = position,
    )

    private fun stubContent(
        state: EditorState,
        onBack: () -> Unit = {},
        onTitleChange: (String) -> Unit = {},
        onItemTextChange: (ChecklistItem, String) -> Unit = { _, _ -> },
        onToggleItem: (ChecklistItem) -> Unit = {},
        onDeleteItem: (ChecklistItem) -> Unit = {},
        onEnterOnItem: (Int, String) -> Unit = { _, _ -> },
        onAppendItem: () -> Unit = {},
        onToggleArchived: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = onBack,
                onTitleChange = onTitleChange,
                onItemTextChange = onItemTextChange,
                onToggleItem = onToggleItem,
                onDeleteItem = onDeleteItem,
                onEnterOnItem = onEnterOnItem,
                onAppendItem = onAppendItem,
                onToggleArchived = onToggleArchived,
            )
        }
    }

    @Test
    fun shows_loading_state() {
        stubContent(EditorState.Loading)
        // Progress indicator has no text; assert the title bar mounted.
        composeRule.onNodeWithText("Note").assertIsDisplayed()
    }

    @Test
    fun shows_not_found_state() {
        stubContent(EditorState.NotFound)
        composeRule.onNodeWithText("Note not found").assertIsDisplayed()
    }

    @Test
    fun loaded_state_renders_title_and_items() {
        stubContent(
            EditorState.Loaded(
                note = note,
                unchecked = listOf(item(1L, "sandwich"), item(2L, "apple")),
                checked = listOf(item(3L, "coffee", checked = true)),
            ),
        )

        // substring = true because each row's text field renders a zero-width sentinel
        // prefix before the visible text (see ZWSP in EditorScreen.kt).
        composeRule.onNodeWithText("sandwich", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("apple", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("coffee", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapping_checkbox_invokes_toggle() {
        val milk = item(1L, "milk")
        var toggled: ChecklistItem? = null

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList()),
            onToggleItem = { toggled = it },
        )

        composeRule.onNodeWithTag("item-checkbox-1").performClick()

        assertThat(toggled).isEqualTo(milk)
    }

    @Test
    fun add_item_button_invokes_onAppendItem() {
        var appends = 0

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList()),
            onAppendItem = { appends++ },
        )

        composeRule.onNodeWithTag("add-item").performClick()

        assertThat(appends).isEqualTo(1)
    }

    @Test
    fun back_button_invokes_onBack() {
        var backs = 0

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList()),
            onBack = { backs++ },
        )

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun typing_in_item_propagates_text_change() {
        val milk = item(1L, "")
        val edits = mutableListOf<Pair<Long, String>>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList()),
            onItemTextChange = { item, text -> edits += item.id to text },
        )

        composeRule.onNodeWithTag("item-text-1").performTextInput("milk")

        assertThat(edits.last()).isEqualTo(1L to "milk")
    }

    @Test
    fun entering_newline_splits_item_and_carries_remainder() {
        val milk = item(1L, "milk")
        val enters = mutableListOf<Pair<Int, String>>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList()),
            onEnterOnItem = { afterPos, remainder -> enters += afterPos to remainder },
        )

        // Injecting a newline simulates Enter on both hardware and soft keyboards
        // — soft IMEs send `\n` via the InputConnection rather than a KeyEvent, so
        // the onValueChange-based detection is what makes this path reliable.
        composeRule.onNodeWithTag("item-text-1").performTextInput("\n")

        assertThat(enters).containsExactly(milk.position to "")
    }

    @Test
    fun backspace_on_empty_row_deletes_it() {
        val blank = item(1L, "")
        var deleted: ChecklistItem? = null

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(blank), checked = emptyList()),
            onDeleteItem = { deleted = it },
        )

        // Replacing the field value with an empty string is what the backspace-on-empty
        // path produces: the user deletes the zero-width sentinel prefix and the field
        // reports an empty value — the handler treats that as "delete this row".
        composeRule.onNodeWithTag("item-text-1").performTextReplacement("")

        assertThat(deleted).isEqualTo(blank)
    }

    @Test
    fun clearing_row_with_stale_model_uses_local_state_and_keeps_row() {
        // The stub's state is fixed — item.text stays "" for the whole test. The user
        // types "milk" (advancing only local state) and then clears. The clear-vs-delete
        // decision must look at the *local* field state (which shows "milk") rather
        // than the stale model text (still ""), otherwise a repo emission that lags
        // behind fast typing would cause a clear to delete the whole row.
        val blank = item(1L, "")
        var deleted: ChecklistItem? = null
        val edits = mutableListOf<Pair<Long, String>>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(blank), checked = emptyList()),
            onDeleteItem = { deleted = it },
            onItemTextChange = { item, text -> edits += item.id to text },
        )

        composeRule.onNodeWithTag("item-text-1").performTextInput("milk")
        composeRule.onNodeWithTag("item-text-1").performTextReplacement("")

        assertThat(deleted).isNull()
        // The final edit emits "" so the VM can catch up with the local state.
        assertThat(edits.last()).isEqualTo(1L to "")
    }

    @Test
    fun clearing_non_empty_row_keeps_the_row_with_empty_text() {
        val milk = item(1L, "milk")
        var deleted: ChecklistItem? = null
        val edits = mutableListOf<Pair<Long, String>>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList()),
            onDeleteItem = { deleted = it },
            onItemTextChange = { item, text -> edits += item.id to text },
        )

        // Select-All + Delete (or cut/replace) blanks the whole field on a row that
        // had visible text. That should clear the item, not delete the row.
        composeRule.onNodeWithTag("item-text-1").performTextReplacement("")

        assertThat(deleted).isNull()
        assertThat(edits.last()).isEqualTo(1L to "")
    }

    @Test
    fun overflow_menu_on_active_note_triggers_archive() {
        val toggles = mutableListOf<Boolean>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList()),
            onToggleArchived = { toggles += it },
        )

        composeRule.onNodeWithTag("editor-overflow").performClick()
        composeRule.onNodeWithText("Archive").performClick()

        assertThat(toggles).containsExactly(true)
    }

    @Test
    fun overflow_menu_on_archived_note_triggers_unarchive() {
        val archived = note.copy(archived = true)
        val toggles = mutableListOf<Boolean>()

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = emptyList(), checked = emptyList()),
            onToggleArchived = { toggles += it },
        )

        composeRule.onNodeWithTag("editor-overflow").performClick()
        composeRule.onNodeWithText("Unarchive").performClick()

        assertThat(toggles).containsExactly(false)
    }

    @Test
    fun archived_editor_hides_add_item_button() {
        val archived = note.copy(archived = true)

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = emptyList(), checked = emptyList()),
        )

        composeRule.onNodeWithTag("add-item").assertDoesNotExist()
    }

    @Test
    fun archived_editor_disables_checkboxes_and_suppresses_toggle() {
        val archived = note.copy(archived = true)
        val milk = item(1L, "milk")
        var toggled: ChecklistItem? = null

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = listOf(milk), checked = emptyList()),
            onToggleItem = { toggled = it },
        )

        composeRule.onNodeWithTag("item-checkbox-1").assertIsNotEnabled()
        composeRule.onNodeWithTag("item-checkbox-1").performClick()

        // Click on a disabled Checkbox is a no-op; the callback must not fire.
        assertThat(toggled).isNull()
    }

    @Test
    fun archived_title_field_exposes_no_text_edit_action() {
        val archived = note.copy(title = "lunch", archived = true)

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = emptyList(), checked = emptyList()),
        )

        // BasicTextField(readOnly = true) removes the SetText semantic action, which
        // is the mechanism both the IME and accessibility/automation services use to
        // mutate text. Asserting its absence proves the field truly can't be edited
        // rather than that our callback happens to no-op.
        composeRule.onNodeWithTag("note-title").assert(noTextEditAction)
    }

    @Test
    fun archived_item_field_exposes_no_text_edit_action() {
        val archived = note.copy(archived = true)
        val milk = item(1L, "milk")

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = listOf(milk), checked = emptyList()),
        )

        composeRule.onNodeWithTag("item-text-1").assert(noTextEditAction)
    }

    @Test
    fun archived_row_never_shows_delete_button_even_when_focused() {
        val archived = note.copy(archived = true)
        val milk = item(1L, "milk")

        stubContent(
            state = EditorState.Loaded(note = archived, unchecked = listOf(milk), checked = emptyList()),
        )

        // Tapping a read-only field can still move focus to it (so the user can position
        // the caret for copy/paste). The delete affordance, however, must stay hidden on
        // archived rows regardless of focus.
        composeRule.onNodeWithTag("item-text-1").performClick()
        composeRule.onNodeWithTag("item-delete-1").assertDoesNotExist()
    }

    @Test
    fun active_editor_keeps_checkboxes_enabled() {
        val milk = item(1L, "milk")

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList()),
        )

        composeRule.onNodeWithTag("item-checkbox-1").assertIsEnabled()
    }

    @Test
    fun entering_newline_in_checked_row_strips_newline_and_preserves_text() {
        val coffee = item(1L, "coffee", checked = true)
        val enters = mutableListOf<Pair<Int, String>>()
        val edits = mutableListOf<Pair<Long, String>>()

        stubContent(
            state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = listOf(coffee)),
            onEnterOnItem = { afterPos, remainder -> enters += afterPos to remainder },
            onItemTextChange = { item, text -> edits += item.id to text },
        )

        // Append a newline and some suffix as if the user pressed Enter in the middle
        // of editing. Checked rows must not split — the suffix after the cursor would
        // be discarded by the no-op onEnter. Instead, the newline is stripped and the
        // full text is preserved.
        composeRule.onNodeWithTag("item-text-1").performTextInput("\nblack")

        assertThat(enters).isEmpty()
        assertThat(edits.last()).isEqualTo(1L to "coffeeblack")
    }
}
