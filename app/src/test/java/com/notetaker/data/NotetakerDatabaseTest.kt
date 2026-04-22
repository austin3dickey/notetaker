package com.notetaker.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotetakerDatabaseTest {
    private lateinit var db: NotetakerDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var itemDao: ChecklistItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotetakerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = db.noteDao()
        itemDao = db.itemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and observe notes`() = runTest {
        val id = noteDao.insert(note(title = "groceries"))
        val notes = noteDao.observeAll().first()
        assertThat(notes).hasSize(1)
        assertThat(notes.single().id).isEqualTo(id)
        assertThat(notes.single().title).isEqualTo("groceries")
    }

    @Test
    fun `observeAll returns active and archived notes together`() = runTest {
        val activeId = noteDao.insert(note(title = "active"))
        val archivedId = noteDao.insert(note(title = "archived", archived = true))

        val all = noteDao.observeAll().first()

        assertThat(all.map { it.id }).containsExactly(activeId, archivedId)
        assertThat(all.single { !it.archived }.title).isEqualTo("active")
        assertThat(all.single { it.archived }.title).isEqualTo("archived")
    }

    @Test
    fun `notes are ordered by updatedAt desc`() = runTest {
        val older = noteDao.insert(note(title = "older", updatedAt = 100L))
        val newer = noteDao.insert(note(title = "newer", updatedAt = 200L))

        val notes = noteDao.observeAll().first()

        assertThat(notes.map { it.id }).containsExactly(newer, older).inOrder()
    }

    @Test
    fun `checklist items are ordered by position, not insertion order`() = runTest {
        val noteId = noteDao.insert(note())

        itemDao.insert(ChecklistItem(noteId = noteId, text = "b", position = 1))
        itemDao.insert(ChecklistItem(noteId = noteId, text = "a", position = 0))
        itemDao.insert(ChecklistItem(noteId = noteId, text = "c", position = 2))

        val items = itemDao.observeByNote(noteId).first()

        assertThat(items.map { it.text }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `nextPosition starts at zero and grows with items`() = runTest {
        val noteId = noteDao.insert(note())

        assertThat(itemDao.nextPosition(noteId)).isEqualTo(0)

        itemDao.insert(ChecklistItem(noteId = noteId, text = "x", position = 0))
        assertThat(itemDao.nextPosition(noteId)).isEqualTo(1)

        itemDao.insert(ChecklistItem(noteId = noteId, text = "y", position = 4))
        assertThat(itemDao.nextPosition(noteId)).isEqualTo(5)
    }

    @Test
    fun `position is preserved when item is checked then unchecked`() = runTest {
        val noteId = noteDao.insert(note())
        val itemId = itemDao.insert(ChecklistItem(noteId = noteId, text = "milk", position = 2))

        val original = itemDao.findById(itemId)!!
        itemDao.update(original.copy(checked = true))
        itemDao.update(original.copy(checked = false))

        val restored = itemDao.findById(itemId)!!
        assertThat(restored.position).isEqualTo(2)
        assertThat(restored.checked).isFalse()
    }

    @Test
    fun `deleting note cascades to checklist items`() = runTest {
        val noteId = noteDao.insert(note())
        itemDao.insert(ChecklistItem(noteId = noteId, text = "a", position = 0))
        itemDao.insert(ChecklistItem(noteId = noteId, text = "b", position = 1))

        val target = noteDao.observeById(noteId).first()!!
        noteDao.delete(target)

        assertThat(itemDao.observeByNote(noteId).first()).isEmpty()
    }

    @Test
    fun `color type converter round-trips all variants`() = runTest {
        NoteColor.entries.forEach { color ->
            val id = noteDao.insert(note(title = color.name, color = color))
            val persisted = noteDao.observeById(id).first()!!
            assertThat(persisted.color).isEqualTo(color)
        }
    }

    @Test
    fun `unique index rejects duplicate (noteId, position)`() = runTest {
        val noteId = noteDao.insert(note())
        itemDao.insert(ChecklistItem(noteId = noteId, text = "first", position = 0))

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                itemDao.insert(ChecklistItem(noteId = noteId, text = "second", position = 0))
            }
        }
    }

    @Test
    fun `same (noteId, position) across different notes is allowed`() = runTest {
        val aId = noteDao.insert(note(title = "a"))
        val bId = noteDao.insert(note(title = "b"))

        itemDao.insert(ChecklistItem(noteId = aId, text = "a0", position = 0))
        itemDao.insert(ChecklistItem(noteId = bId, text = "b0", position = 0))

        assertThat(itemDao.observeByNote(aId).first()).hasSize(1)
        assertThat(itemDao.observeByNote(bId).first()).hasSize(1)
    }

    @Test
    fun `observeAll tiebreaks equal updatedAt by id DESC`() = runTest {
        val older = noteDao.insert(note(title = "older", updatedAt = 100L))
        val newerA = noteDao.insert(note(title = "newerA", updatedAt = 200L))
        val newerB = noteDao.insert(note(title = "newerB", updatedAt = 200L))

        val notes = noteDao.observeAll().first()

        // Same timestamp -> higher id wins; then older note trails.
        assertThat(notes.map { it.id }).containsExactly(newerB, newerA, older).inOrder()
    }

    @Test
    fun `observeById emits updates when note changes`() = runTest {
        val noteId = noteDao.insert(note(title = "v1"))

        noteDao.observeById(noteId).test {
            assertThat(awaitItem()?.title).isEqualTo("v1")

            noteDao.update(noteDao.observeById(noteId).first()!!.copy(title = "v2"))
            assertThat(awaitItem()?.title).isEqualTo("v2")

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun note(
        title: String = "untitled",
        color: NoteColor = NoteColor.NONE,
        archived: Boolean = false,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = Note(
        title = title,
        color = color,
        archived = archived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
