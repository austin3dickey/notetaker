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
 * Exercises the state-based auto-pop that navigates away after a note is removed.
 * Driven through synthetic inputs instead of a full ViewModel so the assertion
 * stays on the UI-layer effect alone.
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
        var wasLoaded by mutableStateOf(false)
        var pops = 0
        composeRule.setContent {
            AutoPopOnNoteRemoval(state = state, wasLoaded = wasLoaded, onPop = { pops++ })
        }

        composeRule.runOnUiThread {
            state = loaded
            wasLoaded = true
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { state = EditorState.NotFound }
        composeRule.waitForIdle()

        assertThat(pops).isEqualTo(1)
    }

    @Test
    fun initial_not_found_does_not_pop() {
        var pops = 0
        composeRule.setContent {
            AutoPopOnNoteRemoval(
                state = EditorState.NotFound,
                wasLoaded = false,
                onPop = { pops++ },
            )
        }
        composeRule.waitForIdle()

        // NotFound from a bogus nav arg should render the message, not auto-pop.
        assertThat(pops).isEqualTo(0)
    }

    @Test
    fun not_found_with_wasLoaded_true_pops_even_on_initial_composition() {
        // Simulates the user confirming delete, rotating, and the recreated
        // composable attaching *after* Room committed — the new composition
        // sees NotFound as its first state. Because wasLoaded comes from the
        // ViewModel (which survived the rotation), we still auto-pop rather
        // than stranding the user on the "not found" screen.
        var pops = 0
        composeRule.setContent {
            AutoPopOnNoteRemoval(
                state = EditorState.NotFound,
                wasLoaded = true,
                onPop = { pops++ },
            )
        }
        composeRule.waitForIdle()

        assertThat(pops).isEqualTo(1)
    }
}
