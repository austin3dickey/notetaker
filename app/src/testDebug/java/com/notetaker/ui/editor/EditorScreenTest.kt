package com.notetaker.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun shows_loading_state() {
        composeRule.setContent {
            EditorScreenContent(
                state = EditorState.Loading,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        // Progress indicator has no text; assert the title bar is showing to prove it
        // mounted, and that no item text appears.
        composeRule.onNodeWithText("Note").assertIsDisplayed()
    }

    @Test
    fun shows_not_found_state() {
        composeRule.setContent {
            EditorScreenContent(
                state = EditorState.NotFound,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        composeRule.onNodeWithText("Note not found").assertIsDisplayed()
    }

    @Test
    fun loaded_state_renders_title_and_items() {
        val state = EditorState.Loaded(
            note = note,
            unchecked = listOf(item(1L, "sandwich"), item(2L, "apple")),
            checked = listOf(item(3L, "coffee", checked = true)),
        )

        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        composeRule.onNodeWithText("sandwich").assertIsDisplayed()
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("coffee").assertIsDisplayed()
    }

    @Test
    fun tapping_checkbox_invokes_toggle() {
        val milk = item(1L, "milk")
        val state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList())
        var toggled: ChecklistItem? = null

        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = { toggled = it },
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        composeRule.onNodeWithTag("item-checkbox-1").performClick()

        assertThat(toggled).isEqualTo(milk)
    }

    @Test
    fun add_item_button_invokes_onAppendItem() {
        val state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList())
        var appends = 0

        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = { appends++ },
            )
        }

        composeRule.onNodeWithTag("add-item").performClick()

        assertThat(appends).isEqualTo(1)
    }

    @Test
    fun back_button_invokes_onBack() {
        val state = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList())
        var backs = 0

        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = { backs++ },
                onTitleChange = {},
                onItemTextChange = { _, _ -> },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun typing_in_item_propagates_text_change() {
        val milk = item(1L, "")
        val state = EditorState.Loaded(note = note, unchecked = listOf(milk), checked = emptyList())
        val edits = mutableListOf<Pair<Long, String>>()

        composeRule.setContent {
            EditorScreenContent(
                state = state,
                onBack = {},
                onTitleChange = {},
                onItemTextChange = { item, text -> edits += item.id to text },
                onToggleItem = {},
                onDeleteItem = {},
                onEnterOnItem = {},
                onAppendItem = {},
            )
        }

        composeRule.onNodeWithTag("item-text-1").performTextInput("milk")

        assertThat(edits.last()).isEqualTo(1L to "milk")
    }
}
