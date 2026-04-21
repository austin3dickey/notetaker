package com.notetaker.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
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
}
