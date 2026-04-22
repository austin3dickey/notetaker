package com.notetaker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {
    private lateinit var db: NotetakerDatabase
    private lateinit var repository: NoteRepository
    private var clockMs = START_TIME

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
            clock = { clockMs },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createNote stamps createdAt and updatedAt with the clock`() = runTest {
        val id = repository.createNote(title = "todo")

        val note = repository.observeNote(id).first()!!
        assertThat(note.createdAt).isEqualTo(START_TIME)
        assertThat(note.updatedAt).isEqualTo(START_TIME)
        assertThat(note.title).isEqualTo("todo")
    }

    @Test
    fun `appendItem assigns nextPosition and bumps updatedAt`() = runTest {
        val id = repository.createNote()

        clockMs = START_TIME + 10
        repository.appendItem(id, "first")
        clockMs = START_TIME + 20
        repository.appendItem(id, "second")

        val items = repository.observeItems(id).first()
        assertThat(items.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(repository.observeNote(id).first()!!.updatedAt).isEqualTo(START_TIME + 20)
    }

    @Test
    fun `addItemAfter shifts later items to make room`() = runTest {
        val id = repository.createNote()
        repository.appendItem(id, "a")
        repository.appendItem(id, "b")
        repository.appendItem(id, "c")

        repository.addItemAfter(id, afterPosition = 0, text = "a-plus")

        val items = repository.observeItems(id).first()
        assertThat(items.map { it.text }).containsExactly("a", "a-plus", "b", "c").inOrder()
        assertThat(items.map { it.position }).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `addItemAfter handles a long run of items without violating the unique index`() = runTest {
        val id = repository.createNote()
        repeat(6) { repository.appendItem(id, "item$it") }

        // Each insert in the middle must shift all later items without a transient
        // (noteId, position) collision.
        repository.addItemAfter(id, afterPosition = 2, text = "X")
        repository.addItemAfter(id, afterPosition = 0, text = "Y")

        val items = repository.observeItems(id).first()
        assertThat(items.map { it.text })
            .containsExactly("item0", "Y", "item1", "item2", "X", "item3", "item4", "item5")
            .inOrder()
        assertThat(items.map { it.position }).isInOrder()
        assertThat(items.map { it.position }.toSet().size).isEqualTo(items.size)
    }

    @Test
    fun `setItemChecked flips state without moving position`() = runTest {
        val id = repository.createNote()
        repository.appendItem(id, "a")
        repository.appendItem(id, "b")
        repository.appendItem(id, "c")

        val middle = repository.observeItems(id).first()[1]
        repository.setItemChecked(middle, checked = true)

        val after = repository.observeItems(id).first()
        val b = after.single { it.text == "b" }
        assertThat(b.checked).isTrue()
        assertThat(b.position).isEqualTo(1)
        assertThat(after.map { it.text }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `updateNoteTitle changes title and updatedAt but keeps createdAt`() = runTest {
        val id = repository.createNote(title = "v1")

        clockMs = START_TIME + 500
        repository.updateNoteTitle(id, "v2")

        val note = repository.observeNote(id).first()!!
        assertThat(note.title).isEqualTo("v2")
        assertThat(note.createdAt).isEqualTo(START_TIME)
        assertThat(note.updatedAt).isEqualTo(START_TIME + 500)
    }

    @Test
    fun `updateItemText bumps note updatedAt`() = runTest {
        val id = repository.createNote()
        repository.appendItem(id, "before")

        clockMs = START_TIME + 100
        val item = repository.observeItems(id).first().single()
        repository.updateItemText(item, "after")

        assertThat(repository.observeItems(id).first().single().text).isEqualTo("after")
        assertThat(repository.observeNote(id).first()!!.updatedAt).isEqualTo(START_TIME + 100)
    }

    @Test
    fun `deleteNote removes note and cascades to items`() = runTest {
        val id = repository.createNote(title = "x")
        repository.appendItem(id, "a")
        repository.appendItem(id, "b")

        repository.deleteNote(id)

        assertThat(repository.observeNote(id).first()).isNull()
        assertThat(repository.observeItems(id).first()).isEmpty()
    }

    @Test
    fun `setNoteArchived flips the archived flag on the note`() = runTest {
        val id = repository.createNote(title = "x")
        assertThat(repository.observeNote(id).first()!!.archived).isFalse()

        repository.setNoteArchived(id, archived = true)

        assertThat(repository.observeNote(id).first()!!.archived).isTrue()

        repository.setNoteArchived(id, archived = false)

        assertThat(repository.observeNote(id).first()!!.archived).isFalse()
    }

    private companion object {
        const val START_TIME = 1_000L
    }
}
