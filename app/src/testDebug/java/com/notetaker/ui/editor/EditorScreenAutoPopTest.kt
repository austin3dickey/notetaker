package com.notetaker.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.notetaker.data.Note
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the Loaded → NotFound auto-pop that guards against a reopen-during-delete
 * race. Driven through synthetic state transitions rather than a full ViewModel so
 * the assertion stays on the UI-layer effect alone.
 */
@RunWith(RobolectricTestRunner::class)
class EditorScreenAutoPopTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val note = Note(id = 1L, title = "t", createdAt = 0L, updatedAt = 0L)
    private val loaded = EditorState.Loaded(note = note, unchecked = emptyList(), checked = emptyList())

    @Test
    fun loaded_then_not_found_pops_once() {
        var state: EditorState by mutableStateOf(EditorState.Loading)
        var pops = 0
        composeRule.setContent { AutoPopOnNoteRemoval(state = state, onPop = { pops++ }) }

        composeRule.runOnUiThread { state = loaded }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { state = EditorState.NotFound }
        composeRule.waitForIdle()

        assertThat(pops).isEqualTo(1)
    }

    @Test
    fun initial_not_found_does_not_pop() {
        var pops = 0
        composeRule.setContent {
            AutoPopOnNoteRemoval(state = EditorState.NotFound, onPop = { pops++ })
        }
        composeRule.waitForIdle()

        // NotFound from a bogus nav arg should render the message, not auto-pop.
        assertThat(pops).isEqualTo(0)
    }

    @Test
    fun loading_then_not_found_does_not_pop() {
        var state: EditorState by mutableStateOf(EditorState.Loading)
        var pops = 0
        composeRule.setContent { AutoPopOnNoteRemoval(state = state, onPop = { pops++ }) }

        composeRule.runOnUiThread { state = EditorState.NotFound }
        composeRule.waitForIdle()

        // Loading → NotFound without passing through Loaded also shouldn't auto-pop.
        assertThat(pops).isEqualTo(0)
    }
}
