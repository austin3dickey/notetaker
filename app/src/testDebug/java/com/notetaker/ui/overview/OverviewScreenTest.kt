package com.notetaker.ui.overview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_state_shows_prompt() {
        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(emptyList()),
                onOpenNote = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithText("No notes yet. Tap + to create one.").assertIsDisplayed()
    }

    @Test
    fun renders_note_titles_and_previews() {
        val summaries = listOf(
            NoteSummary(id = 1L, title = "groceries", previewLines = listOf("milk", "eggs")),
            NoteSummary(id = 2L, title = "", previewLines = emptyList()),
        )

        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(summaries),
                onOpenNote = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithText("groceries").assertIsDisplayed()
        composeRule.onNodeWithText("• milk").assertIsDisplayed()
        composeRule.onNodeWithText("• eggs").assertIsDisplayed()
        composeRule.onNodeWithText("Untitled").assertIsDisplayed()
    }

    @Test
    fun tapping_note_card_invokes_onOpenNote() {
        var opened: Long? = null
        val summaries = listOf(
            NoteSummary(id = 42L, title = "todo", previewLines = emptyList()),
        )

        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(summaries),
                onOpenNote = { opened = it },
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithTag("note-card-42").performClick()

        assertThat(opened).isEqualTo(42L)
    }

    @Test
    fun archived_section_renders_below_active_when_present() {
        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(
                    notes = listOf(NoteSummary(id = 1L, title = "active", previewLines = emptyList())),
                    archived = listOf(NoteSummary(id = 2L, title = "old", previewLines = emptyList())),
                ),
                onOpenNote = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithText("active").assertIsDisplayed()
        composeRule.onNodeWithTag("archived-header").assertIsDisplayed()
        composeRule.onNodeWithText("old").assertIsDisplayed()
    }

    @Test
    fun archived_header_hidden_when_no_archived_notes() {
        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(
                    notes = listOf(NoteSummary(id = 1L, title = "active", previewLines = emptyList())),
                    archived = emptyList(),
                ),
                onOpenNote = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithTag("archived-header").assertDoesNotExist()
    }

    @Test
    fun empty_prompt_suppressed_when_only_archived_notes_exist() {
        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(
                    notes = emptyList(),
                    archived = listOf(NoteSummary(id = 2L, title = "old", previewLines = emptyList())),
                ),
                onOpenNote = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithText("No notes yet. Tap + to create one.").assertDoesNotExist()
        composeRule.onNodeWithTag("archived-header").assertIsDisplayed()
        composeRule.onNodeWithText("old").assertIsDisplayed()
    }

    @Test
    fun tapping_archived_card_invokes_onOpenNote() {
        var opened: Long? = null

        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(
                    notes = emptyList(),
                    archived = listOf(NoteSummary(id = 42L, title = "old", previewLines = emptyList())),
                ),
                onOpenNote = { opened = it },
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithTag("note-card-42").performClick()

        assertThat(opened).isEqualTo(42L)
    }

    @Test
    fun tapping_fab_invokes_onCreateNote() {
        var created = 0

        composeRule.setContent {
            OverviewScreenContent(
                state = OverviewState.Loaded(emptyList()),
                onOpenNote = {},
                onCreateNote = { created++ },
            )
        }

        composeRule.onNodeWithContentDescription("New note").performClick()

        assertThat(created).isEqualTo(1)
    }
}
