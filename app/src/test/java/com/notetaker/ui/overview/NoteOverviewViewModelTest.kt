package com.notetaker.ui.overview

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.notetaker.data.NoteColor
import com.notetaker.data.NoteRepository
import com.notetaker.data.NotetakerDatabase
import com.notetaker.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteOverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: NotetakerDatabase
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotetakerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(
            db = db,
            noteDao = db.noteDao(),
            itemDao = db.itemDao(),
            clock = { 0L },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `state is Loaded with empty list when no notes exist`() = runTest {
        val vm = NoteOverviewViewModel(repository)

        vm.state.test {
            val loaded = awaitLoaded()
            assertThat(loaded.notes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state surfaces active notes with unchecked previews`() = runTest {
        val groceriesId = repository.createNote(title = "groceries")
        repository.appendItem(groceriesId, "milk")
        repository.appendItem(groceriesId, "eggs")
        repository.appendItem(groceriesId, "bread")

        val vm = NoteOverviewViewModel(repository)

        vm.state.test {
            val loaded = awaitLoadedMatching { it.notes.isNotEmpty() }
            val groceries = loaded.notes.single { it.id == groceriesId }
            assertThat(groceries.title).isEqualTo("groceries")
            assertThat(groceries.previewLines).containsExactly("milk", "eggs").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preview skips checked items`() = runTest {
        val noteId = repository.createNote(title = "n")
        repository.appendItem(noteId, "a")
        repository.appendItem(noteId, "b")
        val a = repository.observeItems(noteId).first().single { it.text == "a" }
        repository.setItemChecked(a, checked = true)

        val vm = NoteOverviewViewModel(repository)

        vm.state.test {
            val loaded = awaitLoadedMatching { it.notes.any { n -> n.previewLines.isNotEmpty() } }
            val only = loaded.notes.single()
            assertThat(only.previewLines).containsExactly("b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archived notes do not appear in overview`() = runTest {
        val active = repository.createNote(title = "active")
        val archived = repository.createNote(title = "archived")
        repository.setNoteArchived(archived, archived = true)

        val vm = NoteOverviewViewModel(repository)

        vm.state.test {
            val loaded = awaitLoadedMatching { it.notes.isNotEmpty() }
            assertThat(loaded.notes.map { it.id }).containsExactly(active)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summary carries the note color for card rendering`() = runTest {
        val plain = repository.createNote(title = "plain")
        val tinted = repository.createNote(title = "tinted", color = NoteColor.BLUE)

        val vm = NoteOverviewViewModel(repository)

        vm.state.test {
            val loaded = awaitLoadedMatching { it.notes.size == 2 }
            assertThat(loaded.notes.single { it.id == plain }.color).isEqualTo(NoteColor.NONE)
            assertThat(loaded.notes.single { it.id == tinted }.color).isEqualTo(NoteColor.BLUE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createNote returns a new id`() = runTest {
        val vm = NoteOverviewViewModel(repository)

        val id = vm.createNote()

        assertThat(repository.observeNote(id).first()).isNotNull()
    }

    private suspend fun ReceiveTurbine<OverviewState>.awaitSettled(): OverviewState {
        var next = awaitItem()
        while (next is OverviewState.Loading) next = awaitItem()
        return next
    }

    private suspend fun ReceiveTurbine<OverviewState>.awaitLoaded(): OverviewState.Loaded =
        awaitSettled() as OverviewState.Loaded

    private suspend fun ReceiveTurbine<OverviewState>.awaitLoadedMatching(
        predicate: (OverviewState.Loaded) -> Boolean,
    ): OverviewState.Loaded {
        while (true) {
            val next = awaitItem()
            if (next is OverviewState.Loaded && predicate(next)) return next
        }
    }
}
