package com.notetaker.ui.editor

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
class NoteEditorViewModelTest {
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
    fun `state emits NotFound when note does not exist`() = runTest {
        val vm = NoteEditorViewModel(noteId = 999L, repository = repository)

        vm.state.test {
            assertThat(awaitSettled()).isEqualTo(EditorState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state emits Loaded with note and split checklist sections`() = runTest {
        val noteId = repository.createNote(title = "groceries")
        repository.appendItem(noteId, "milk")
        repository.appendItem(noteId, "bread")
        val bread = repository.observeItems(noteId).first().single { it.text == "bread" }
        repository.setItemChecked(bread, checked = true)

        val vm = NoteEditorViewModel(noteId = noteId, repository = repository)

        vm.state.test {
            val loaded = awaitLoaded()
            assertThat(loaded.note.title).isEqualTo("groceries")
            assertThat(loaded.unchecked.map { it.text }).containsExactly("milk")
            assertThat(loaded.checked.map { it.text }).containsExactly("bread")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleChecked moves an item between sections without reordering`() = runTest {
        val noteId = repository.createNote()
        repository.appendItem(noteId, "a")
        repository.appendItem(noteId, "b")
        repository.appendItem(noteId, "c")

        val vm = NoteEditorViewModel(noteId = noteId, repository = repository)

        vm.state.test {
            val initial = awaitLoaded()
            val b = initial.unchecked.single { it.text == "b" }

            vm.toggleChecked(b)
            val afterCheck = awaitLoadedMatching { it.checked.any { item -> item.text == "b" } }
            assertThat(afterCheck.unchecked.map { it.text }).containsExactly("a", "c").inOrder()
            assertThat(afterCheck.checked.map { it.text }).containsExactly("b")

            vm.toggleChecked(afterCheck.checked.single())
            val afterUncheck = awaitLoadedMatching { it.checked.isEmpty() }
            assertThat(afterUncheck.unchecked.map { it.text })
                .containsExactly("a", "b", "c")
                .inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addItemAfter inserts a new row between existing ones`() = runTest {
        val noteId = repository.createNote()
        repository.appendItem(noteId, "a")
        repository.appendItem(noteId, "c")

        val vm = NoteEditorViewModel(noteId = noteId, repository = repository)

        vm.state.test {
            val initial = awaitLoaded()
            val a = initial.unchecked.single { it.text == "a" }

            vm.addItemAfter(afterPosition = a.position, text = "b")

            val loaded = awaitLoadedMatching { it.unchecked.size == 3 }
            assertThat(loaded.unchecked.map { it.text }).containsExactly("a", "b", "c").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setColor updates the note color in state`() = runTest {
        val noteId = repository.createNote(title = "t")
        val vm = NoteEditorViewModel(noteId = noteId, repository = repository)

        vm.state.test {
            assertThat(awaitLoaded().note.color).isEqualTo(NoteColor.NONE)

            vm.setColor(NoteColor.YELLOW)

            val updated = awaitLoadedMatching { it.note.color == NoteColor.YELLOW }
            assertThat(updated.note.color).isEqualTo(NoteColor.YELLOW)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTitle updates the title in state`() = runTest {
        val noteId = repository.createNote(title = "old")
        val vm = NoteEditorViewModel(noteId = noteId, repository = repository)

        vm.state.test {
            assertThat(awaitLoaded().note.title).isEqualTo("old")

            vm.setTitle("new")

            val updated = awaitLoadedMatching { it.note.title == "new" }
            assertThat(updated.note.title).isEqualTo("new")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<EditorState>.awaitSettled(): EditorState {
        var next = awaitItem()
        while (next is EditorState.Loading) next = awaitItem()
        return next
    }

    private suspend fun ReceiveTurbine<EditorState>.awaitLoaded(): EditorState.Loaded =
        awaitSettled() as EditorState.Loaded

    /**
     * Room Flows can emit transient intermediate states (e.g. when an update fires multiple
     * invalidation pulses). Keep reading until we see a Loaded state that matches [predicate].
     */
    private suspend fun ReceiveTurbine<EditorState>.awaitLoadedMatching(
        predicate: (EditorState.Loaded) -> Boolean,
    ): EditorState.Loaded {
        while (true) {
            val next = awaitItem()
            if (next is EditorState.Loaded && predicate(next)) return next
        }
    }
}
