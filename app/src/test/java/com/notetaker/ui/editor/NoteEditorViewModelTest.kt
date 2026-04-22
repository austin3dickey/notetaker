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
        val vm = NoteEditorViewModel(
            noteId = 999L,
            repository = repository,
            externalScope = backgroundScope,
        )

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
        repository.setItemChecked(bread.id, checked = true)

        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

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

        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

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

        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

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
    fun `setTitle updates the title in state`() = runTest {
        val noteId = repository.createNote(title = "old")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            assertThat(awaitLoaded().note.title).isEqualTo("old")

            vm.setTitle("new")

            val updated = awaitLoadedMatching { it.note.title == "new" }
            assertThat(updated.note.title).isEqualTo("new")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wasLoaded flips true once the note loads and stays true after deletion`() = runTest {
        // Covers the rotate-during-delete case: the UI relies on wasLoaded to decide
        // whether a NotFound state should pop or render "not found". Because
        // wasLoaded lives on the VM, it must survive the state flow moving from
        // Loaded back to NotFound — without that, a recreated composable would see
        // wasLoaded=false on first collection and fail to pop.
        val noteId = repository.createNote(title = "x")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()
            assertThat(vm.wasLoaded.value).isTrue()

            vm.deleteNote()
            assertThat(awaitSettled()).isEqualTo(EditorState.NotFound)
            assertThat(vm.wasLoaded.value).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wasLoaded stays false when the note never exists`() = runTest {
        val vm = NoteEditorViewModel(
            noteId = 999L,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            assertThat(awaitSettled()).isEqualTo(EditorState.NotFound)
            assertThat(vm.wasLoaded.value).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNote removes the note and state transitions to NotFound`() = runTest {
        // Completion is observed through the state flow: the UI pops when it sees
        // Loaded → NotFound. Runs the delete on an external scope so the write
        // would still commit even if viewModelScope were cancelled.
        val noteId = repository.createNote(title = "gone")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.deleteNote()

            assertThat(awaitSettled()).isEqualTo(EditorState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.observeNote(noteId).first()).isNull()
    }

    @Test
    fun `canUndo and canRedo start false and flip on edits`() = runTest {
        val noteId = repository.createNote(title = "t")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()
            assertThat(vm.canUndo.value).isFalse()
            assertThat(vm.canRedo.value).isFalse()

            vm.setTitle("new")
            awaitLoadedMatching { it.note.title == "new" }
            assertThat(vm.canUndo.value).isTrue()
            assertThat(vm.canRedo.value).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo reverts a title change and redo reapplies it`() = runTest {
        val noteId = repository.createNote(title = "v1")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.setTitle("v2")
            awaitLoadedMatching { it.note.title == "v2" }

            vm.undo()
            val afterUndo = awaitLoadedMatching { it.note.title == "v1" }
            assertThat(afterUndo.note.title).isEqualTo("v1")
            assertThat(vm.canUndo.value).isFalse()
            assertThat(vm.canRedo.value).isTrue()

            vm.redo()
            val afterRedo = awaitLoadedMatching { it.note.title == "v2" }
            assertThat(afterRedo.note.title).isEqualTo("v2")
            assertThat(vm.canUndo.value).isTrue()
            assertThat(vm.canRedo.value).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo reverts appendItem`() = runTest {
        val noteId = repository.createNote()
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            val initial = awaitLoaded()
            assertThat(initial.unchecked).isEmpty()

            vm.appendItem("milk")
            awaitLoadedMatching { it.unchecked.any { item -> item.text == "milk" } }

            vm.undo()
            val afterUndo = awaitLoadedMatching { it.unchecked.isEmpty() }
            assertThat(afterUndo.unchecked).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo reverts a deleted item and brings it back with the same text and position`() =
        runTest {
            val noteId = repository.createNote()
            repository.appendItem(noteId, "a")
            repository.appendItem(noteId, "b")
            repository.appendItem(noteId, "c")

            val vm = NoteEditorViewModel(
                noteId = noteId,
                repository = repository,
                externalScope = backgroundScope,
            )

            vm.state.test {
                val initial = awaitLoaded()
                val b = initial.unchecked.single { it.text == "b" }

                vm.deleteItem(b)
                awaitLoadedMatching { it.unchecked.none { item -> item.text == "b" } }

                vm.undo()
                val afterUndo = awaitLoadedMatching { it.unchecked.size == 3 }
                assertThat(afterUndo.unchecked.map { it.text })
                    .containsExactly("a", "b", "c").inOrder()
                assertThat(afterUndo.unchecked.map { it.position })
                    .containsExactly(0, 1, 2).inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `undo reverts a toggleChecked and restores the unchecked position`() = runTest {
        val noteId = repository.createNote()
        repository.appendItem(noteId, "a")
        repository.appendItem(noteId, "b")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            val initial = awaitLoaded()
            val a = initial.unchecked.single { it.text == "a" }

            vm.toggleChecked(a)
            awaitLoadedMatching { it.checked.any { item -> item.text == "a" } }

            vm.undo()
            val afterUndo = awaitLoadedMatching { it.checked.isEmpty() }
            assertThat(afterUndo.unchecked.map { it.text }).containsExactly("a", "b").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consecutive text edits to the same item coalesce into one undo step`() = runTest {
        val noteId = repository.createNote()
        repository.appendItem(noteId, "")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            val initial = awaitLoaded()
            val item = initial.unchecked.single()

            // Simulate typing "hi" — two onValueChange calls, one per character. The
            // second edit must *not* create a second undo entry; undo should take the
            // row back to "" in a single step, not stair-step through "h".
            vm.updateItemText(item, "h")
            val afterFirst = awaitLoadedMatching { it.unchecked.single().text == "h" }
            vm.updateItemText(afterFirst.unchecked.single(), "hi")
            awaitLoadedMatching { it.unchecked.single().text == "hi" }

            vm.undo()
            val afterUndo = awaitLoadedMatching { it.unchecked.single().text == "" }
            assertThat(afterUndo.unchecked.single().text).isEqualTo("")
            assertThat(vm.canUndo.value).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `text edits to different items each create their own undo entries`() = runTest {
        val noteId = repository.createNote()
        repository.appendItem(noteId, "")
        repository.appendItem(noteId, "")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            val initial = awaitLoaded()
            val first = initial.unchecked[0]
            val second = initial.unchecked[1]

            vm.updateItemText(first, "a")
            val afterFirst = awaitLoadedMatching { it.unchecked[0].text == "a" }
            vm.updateItemText(afterFirst.unchecked[1], "b")
            awaitLoadedMatching { it.unchecked[1].text == "b" }

            vm.undo()
            val afterFirstUndo = awaitLoadedMatching { it.unchecked[1].text == "" }
            assertThat(afterFirstUndo.unchecked.map { it.text }).containsExactly("a", "").inOrder()

            vm.undo()
            val afterSecondUndo = awaitLoadedMatching { it.unchecked[0].text == "" }
            assertThat(afterSecondUndo.unchecked.map { it.text }).containsExactly("", "").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `structural edits do not coalesce with each other`() = runTest {
        val noteId = repository.createNote()
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.appendItem("a")
            awaitLoadedMatching { it.unchecked.size == 1 }
            vm.appendItem("b")
            awaitLoadedMatching { it.unchecked.size == 2 }

            vm.undo()
            val once = awaitLoadedMatching { it.unchecked.size == 1 }
            assertThat(once.unchecked.single().text).isEqualTo("a")

            vm.undo()
            val twice = awaitLoadedMatching { it.unchecked.isEmpty() }
            assertThat(twice.unchecked).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new edit after undo clears the redo stack`() = runTest {
        val noteId = repository.createNote(title = "v1")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.setTitle("v2")
            awaitLoadedMatching { it.note.title == "v2" }
            vm.undo()
            awaitLoadedMatching { it.note.title == "v1" }
            assertThat(vm.canRedo.value).isTrue()

            // A fresh edit branches history — the redo stack from before the undo
            // should be gone so "v2" is no longer reachable via redo.
            vm.setTitle("v3")
            awaitLoadedMatching { it.note.title == "v3" }
            assertThat(vm.canRedo.value).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo is a no-op when the stack is empty`() = runTest {
        val noteId = repository.createNote(title = "only")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()
            vm.undo()
            // No state change should be observable — canUndo stays false, title unchanged.
            assertThat(vm.canUndo.value).isFalse()
            assertThat(vm.state.value).isInstanceOf(EditorState.Loaded::class.java)
            assertThat((vm.state.value as EditorState.Loaded).note.title).isEqualTo("only")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo stack is bounded by MAX_DEPTH so old history drops off the bottom`() = runTest {
        val noteId = repository.createNote()
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            val overflow = NoteEditorViewModel.MAX_DEPTH + 5
            repeat(overflow) { index ->
                vm.appendItem("item-$index")
                awaitLoadedMatching { it.unchecked.size == index + 1 }
            }

            // Rewind as far as undo allows — should reach the oldest retained snapshot,
            // which is MAX_DEPTH deep. The first 5 appends fell off, so the earliest
            // recoverable state still has items 0..4 present.
            var remaining = NoteEditorViewModel.MAX_DEPTH
            while (remaining > 0 && vm.canUndo.value) {
                vm.undo()
                awaitItem()
                remaining--
            }

            assertThat(vm.canUndo.value).isFalse()
            val state = vm.state.value as EditorState.Loaded
            assertThat(state.unchecked.map { it.text })
                .containsExactly("item-0", "item-1", "item-2", "item-3", "item-4")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `splitItem records a single undo entry and one undo fully reverses the split`() =
        runTest {
            val noteId = repository.createNote()
            repository.appendItem(noteId, "helloworld")
            val vm = NoteEditorViewModel(
                noteId = noteId,
                repository = repository,
                externalScope = backgroundScope,
            )

            vm.state.test {
                val initial = awaitLoaded()
                val item = initial.unchecked.single()

                // Split mid-text — the truncation and the insert have to collapse into
                // one undo entry. Otherwise a user Enter in the middle of a word would
                // require two undos to reverse, which breaks the mental model.
                vm.splitItem(item, keepText = "hello", remainderText = "world")
                awaitLoadedMatching { it.unchecked.size == 2 && it.unchecked[0].text == "hello" }

                vm.undo()
                val afterUndo = awaitLoadedMatching { it.unchecked.size == 1 }
                assertThat(afterUndo.unchecked.single().text).isEqualTo("helloworld")
                assertThat(vm.canUndo.value).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `two rapid undos preserve intermediate history on the redo stack`() = runTest {
        // Drives the race the roborev finding called out: both undo() calls read the
        // stacks synchronously, but the repository restore is asynchronous, so the
        // second click would see a stale state.value without [lastAppliedSnapshot] and
        // duplicate the topmost redo entry — losing the middle state entirely.
        //
        // Uses structural appends (never coalesced) so we get three distinct undo
        // entries to rewind through.
        val noteId = repository.createNote()
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.appendItem("a")
            awaitLoadedMatching { it.unchecked.size == 1 }
            vm.appendItem("b")
            awaitLoadedMatching { it.unchecked.size == 2 }

            // Two undos back to back. We pop and push against the stacks synchronously,
            // so the second click sees an `undo` stack of size 1 even before the first
            // undo's restore has committed.
            vm.undo()
            vm.undo()

            // After draining emissions, state should be back to empty and redo should
            // have two distinct entries (one-item then two-item) — not two copies of
            // the post-append state.
            awaitLoadedMatching { it.unchecked.isEmpty() }
            assertThat(vm.canRedo.value).isTrue()

            vm.redo()
            val afterFirstRedo = awaitLoadedMatching { it.unchecked.size == 1 }
            assertThat(afterFirstRedo.unchecked.single().text).isEqualTo("a")

            vm.redo()
            val afterSecondRedo = awaitLoadedMatching { it.unchecked.size == 2 }
            assertThat(afterSecondRedo.unchecked.map { it.text }).containsExactly("a", "b").inOrder()
            assertThat(vm.canRedo.value).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle on a stale item reference after undo lands on the restored row`() = runTest {
        // Regression for the restore-race finding: the user holds a ChecklistItem
        // reference from before an undo, and the checkbox tap fires before Room emits
        // the restored state. Because the restore reinserts items with their
        // snapshotted ids, and the toggle goes through a column-targeted UPDATE keyed
        // on that id, the tap lands on the restored row rather than on a dead id — and
        // it doesn't clobber the row's (restored) text.
        val noteId = repository.createNote()
        repository.appendItem(noteId, "fresh")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            val initial = awaitLoaded()
            val item = initial.unchecked.single()

            // Build up history: edit the text so undo has something to restore.
            vm.updateItemText(item, "edited")
            awaitLoadedMatching { it.unchecked.single().text == "edited" }

            // The UI still has the pre-restore ChecklistItem reference [item]. Undo,
            // then immediately toggle on that stale reference.
            vm.undo()
            vm.toggleChecked(item)

            // Final state must reflect both: the undo restored the original text, and
            // the toggle flipped `checked` without clobbering that text.
            val finalState = awaitLoadedMatching { it.checked.any { it.text == "fresh" } }
            assertThat(finalState.checked.map { it.text }).containsExactly("fresh")
            assertThat(finalState.unchecked).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateItemText on a stale reference after undo targets id not stale fields`() =
        runTest {
            // Companion to the toggle race test, for the text-edit path. Verifies two
            // things at once: the update lands on the restored row (id is preserved
            // across restore), and the column-targeted UPDATE doesn't clobber the
            // restored checked-state with the stale reference's value.
            val noteId = repository.createNote()
            repository.appendItem(noteId, "original")
            val vm = NoteEditorViewModel(
                noteId = noteId,
                repository = repository,
                externalScope = backgroundScope,
            )

            vm.state.test {
                val initial = awaitLoaded()
                val staleRef = initial.unchecked.single()

                // Check it, then toggle off via the repo (without going through the
                // VM) so staleRef captures checked=false but the DB will soon have
                // checked=true via the upcoming undo restore.
                vm.toggleChecked(staleRef)
                val checked = awaitLoadedMatching { it.checked.isNotEmpty() }
                val checkedRef = checked.checked.single()

                vm.toggleChecked(checkedRef)
                awaitLoadedMatching { it.checked.isEmpty() }

                // Now undo the uncheck — brings us back to checked=true. Before Room
                // emits, type on the original (stale) unchecked reference.
                vm.undo()
                vm.updateItemText(staleRef, "edited")

                val final = awaitLoadedMatching { it.checked.any { it.text == "edited" } }
                assertThat(final.checked.single().text).isEqualTo("edited")
                assertThat(final.unchecked).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteItem on a stale reference after undo removes the restored row by id`() =
        runTest {
            val noteId = repository.createNote()
            repository.appendItem(noteId, "keep-me")
            val vm = NoteEditorViewModel(
                noteId = noteId,
                repository = repository,
                externalScope = backgroundScope,
            )

            vm.state.test {
                val initial = awaitLoaded()
                val staleRef = initial.unchecked.single()

                // Text change so there's something to undo.
                vm.updateItemText(staleRef, "edited")
                awaitLoadedMatching { it.unchecked.single().text == "edited" }

                // Undo + delete the stale reference in quick succession. The restore
                // reinserts with the same id, so the delete's by-id path still finds
                // and removes it.
                vm.undo()
                vm.deleteItem(staleRef)

                awaitLoadedMatching { it.unchecked.isEmpty() }
                assertThat(repository.observeItems(noteId).first()).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `undo restores title color and item list from the snapshot`() = runTest {
        val noteId = repository.createNote(title = "orig", color = NoteColor.NONE)
        repository.appendItem(noteId, "a")
        val vm = NoteEditorViewModel(
            noteId = noteId,
            repository = repository,
            externalScope = backgroundScope,
        )

        vm.state.test {
            awaitLoaded()

            vm.setColor(NoteColor.BLUE)
            awaitLoadedMatching { it.note.color == NoteColor.BLUE }

            vm.undo()
            val afterUndo = awaitLoadedMatching { it.note.color == NoteColor.NONE }
            assertThat(afterUndo.note.title).isEqualTo("orig")
            assertThat(afterUndo.unchecked.map { it.text }).containsExactly("a")
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
