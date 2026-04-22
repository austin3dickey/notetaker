package com.notetaker.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notetaker.data.ChecklistItem

/**
 * Compose entry point for a single note. Stateless against the ViewModel — all mutation
 * flows through the lambdas so the content composable can be previewed and tested in
 * isolation with any [EditorState].
 */
@Composable
fun EditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wasLoaded by viewModel.wasLoaded.collectAsStateWithLifecycle()
    AutoPopOnNoteRemoval(state = state, wasLoaded = wasLoaded, onPop = onBack)
    EditorScreenContent(
        state = state,
        onBack = onBack,
        onTitleChange = viewModel::setTitle,
        onItemTextChange = viewModel::updateItemText,
        onToggleItem = viewModel::toggleChecked,
        onDeleteItem = viewModel::deleteItem,
        onEnterOnItem = { afterPos, remainder -> viewModel.addItemAfter(afterPos, remainder) },
        onAppendItem = { viewModel.appendItem() },
        onDeleteNote = viewModel::deleteNote,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreenContent(
    state: EditorState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onItemTextChange: (ChecklistItem, String) -> Unit,
    onToggleItem: (ChecklistItem) -> Unit,
    onDeleteItem: (ChecklistItem) -> Unit,
    onEnterOnItem: (afterPosition: Int, remainder: String) -> Unit,
    onAppendItem: () -> Unit,
    onDeleteNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showMenu = state is EditorState.Loaded
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showMenu) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("editor-overflow"),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.testTag("menu-delete"),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            EditorState.Loading -> LoadingIndicator(padding)
            EditorState.NotFound -> NotFound(padding)
            is EditorState.Loaded -> LoadedEditor(
                state = state,
                padding = padding,
                onTitleChange = onTitleChange,
                onItemTextChange = onItemTextChange,
                onToggleItem = onToggleItem,
                onDeleteItem = onDeleteItem,
                onEnterOnItem = onEnterOnItem,
                onAppendItem = onAppendItem,
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteNoteDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteNote()
            },
        )
    }
}

/**
 * Pops the back stack when the note we were editing disappears. Covers both the
 * user-initiated delete (state flips Loaded → NotFound once Room commits) and the
 * race where the current VM saw the note but an app-scoped delete from a prior VM
 * lands. A NotFound that was there from the start (bogus nav arg, [wasLoaded]
 * never true) keeps rendering the "not found" message instead.
 *
 * [wasLoaded] is sourced from the ViewModel (not composable-local state) so it
 * survives configuration changes that recreate the composable mid-delete.
 *
 * Internal so Compose tests can drive it with synthetic inputs without standing
 * up a full ViewModel.
 */
@Composable
internal fun AutoPopOnNoteRemoval(state: EditorState, wasLoaded: Boolean, onPop: () -> Unit) {
    val currentOnPop by rememberUpdatedState(onPop)
    LaunchedEffect(state, wasLoaded) {
        if (state is EditorState.NotFound && wasLoaded) currentOnPop()
    }
}

@Composable
private fun DeleteNoteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete note?") },
        text = { Text("This note and its items will be permanently deleted.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-delete"),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LoadingIndicator(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun NotFound(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { Text("Note not found") }
}

@Composable
private fun LoadedEditor(
    state: EditorState.Loaded,
    padding: PaddingValues,
    onTitleChange: (String) -> Unit,
    onItemTextChange: (ChecklistItem, String) -> Unit,
    onToggleItem: (ChecklistItem) -> Unit,
    onDeleteItem: (ChecklistItem) -> Unit,
    onEnterOnItem: (afterPosition: Int, remainder: String) -> Unit,
    onAppendItem: () -> Unit,
) {
    // When we add a new item after position P, we expect it to appear at position P+1.
    // We stash that position and, once a row at that position mounts, hand it a
    // FocusRequester so the keyboard pops up on the fresh row without a user tap.
    var pendingFocusPosition by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "title") {
            TitleField(title = state.note.title, onTitleChange = onTitleChange)
        }

        items(items = state.unchecked, key = { "u-${it.id}" }) { item ->
            ChecklistRow(
                item = item,
                requestFocus = pendingFocusPosition == item.position,
                onFocusApplied = { pendingFocusPosition = null },
                onTextChange = { onItemTextChange(item, it) },
                onToggle = { onToggleItem(item) },
                onDelete = { onDeleteItem(item) },
                onEnter = { remainder ->
                    pendingFocusPosition = item.position + 1
                    onEnterOnItem(item.position, remainder)
                },
                splittingEnabled = true,
            )
        }

        item(key = "add-row") {
            TextButton(
                onClick = {
                    // Appending always lands at the end; no need to track focus position
                    // because the user initiated the action deliberately.
                    onAppendItem()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-item"),
            ) { Text("+ Add item") }
        }

        if (state.checked.isNotEmpty()) {
            item(key = "divider") {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
            }

            items(items = state.checked, key = { "c-${it.id}" }) { item ->
                ChecklistRow(
                    item = item,
                    requestFocus = false,
                    onFocusApplied = {},
                    onTextChange = { onItemTextChange(item, it) },
                    onToggle = { onToggleItem(item) },
                    onDelete = { onDeleteItem(item) },
                    // Checked rows don't split on Enter, so this never runs — but it's
                    // part of the row's shared API.
                    onEnter = {},
                    splittingEnabled = false,
                )
            }
        }
    }
}

/**
 * Invisible sentinel prefix. Soft keyboards on Android don't reliably deliver a key
 * event for a backspace pressed on an already-empty field, so we keep a zero-width
 * space at position 0 of each row's text. A backspace at the visual start of the
 * field deletes the ZWSP instead, which does fire [BasicTextField.onValueChange] —
 * and seeing the prefix gone is our signal that the user asked to delete the row.
 */
private const val ZWSP: String = "\u200B"

@Composable
private fun TitleField(title: String, onTitleChange: (String) -> Unit) {
    var tfv by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    // Sync with external updates — without this the field would display stale text
    // after a title change from another writer (or after undo/redo in M2) and the
    // next keystroke would clobber the newer value.
    LaunchedEffect(title) {
        if (tfv.text != title) tfv = TextFieldValue(title, TextRange(title.length))
    }
    BasicTextField(
        value = tfv,
        onValueChange = { new ->
            // Strip any newline a soft keyboard may inject (the title is always a
            // single line; Enter here should do nothing rather than split into items).
            val sanitized = if (new.text.contains('\n')) new.copy(text = new.text.replace("\n", "")) else new
            tfv = sanitized
            if (sanitized.text != title) onTitleChange(sanitized.text)
        },
        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.titleLarge),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalContentColor.current),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                if (tfv.text.isEmpty()) {
                    Text(
                        "Title",
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalContentColor.current.copy(alpha = 0.4f),
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note-title")
            .semantics { contentDescription = "Note title" },
    )
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    requestFocus: Boolean,
    onFocusApplied: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEnter: (remainder: String) -> Unit,
    splittingEnabled: Boolean,
) {
    var tfv by remember(item.id) {
        mutableStateOf(TextFieldValue(ZWSP + item.text, TextRange(ZWSP.length + item.text.length)))
    }
    // Sync with external updates so repository emissions for the same id don't get
    // overwritten by stale local state on the next keystroke.
    LaunchedEffect(item.id, item.text) {
        val external = ZWSP + item.text
        if (tfv.text.removePrefix(ZWSP) != item.text) {
            tfv = TextFieldValue(external, TextRange(external.length))
        }
    }

    var focused by remember(item.id) { mutableStateOf(false) }
    val focusRequester = remember(item.id) { FocusRequester() }

    LaunchedEffect(requestFocus, item.id) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusApplied()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("item-checkbox-${item.id}"),
        )

        val textColor =
            if (item.checked) LocalContentColor.current.copy(alpha = 0.45f)
            else LocalContentColor.current
        val style = LocalTextStyle.current.copy(
            color = textColor,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        )

        BasicTextField(
            value = tfv,
            onValueChange = { new ->
                handleItemEdit(
                    new = new,
                    previousLocalText = tfv.text.removePrefix(ZWSP),
                    currentItemText = item.text,
                    setLocal = { tfv = it },
                    onTextChange = onTextChange,
                    onEnter = onEnter,
                    onBackspaceOnEmpty = onDelete,
                    splittingEnabled = splittingEnabled,
                )
            },
            textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
            modifier = Modifier
                .weight(1f)
                .testTag("item-text-${item.id}")
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        )

        if (focused) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("item-delete-${item.id}"),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete item",
                    tint = textColor,
                )
            }
        } else {
            // Reserve symmetric space so rows don't jump horizontally when focus toggles.
            Spacer(Modifier.width(DELETE_ICON_SIZE))
        }
    }
}

/**
 * Routes a raw [TextFieldValue] emission from a checklist row into the right
 * ViewModel callback. Kept top-level (rather than a lambda inside the composable)
 * so the control flow is unit-testable and [ChecklistRow] stays focused on layout.
 *
 * [previousLocalText] is the field's visible text *before* this edit, sourced from
 * the local [TextFieldValue] rather than the model. That matters for the
 * clear-vs-delete decision: if the repository is lagging behind fast keystrokes,
 * the model's [currentItemText] can be empty while the user is staring at text
 * they just typed. Using the local state means a clear always behaves the way
 * the user sees the field.
 *
 * Outcomes:
 * - ZWSP sentinel gone + text empty + row was *visibly* blank → delete the row.
 * - ZWSP sentinel gone + text empty + row had visible text → user cleared it
 *   (Select All + Delete, cut, paste replacement); keep the row with empty text.
 * - ZWSP sentinel gone + text not empty → user edited at position 0; rebuild the
 *   sentinel and propagate the new text.
 * - Newline present + [splittingEnabled] → split at the newline, keep the prefix,
 *   hand the remainder to [onEnter]. For checked rows ([splittingEnabled] false)
 *   the suffix would be discarded by the no-op onEnter, so we strip the newline
 *   and keep the full text intact instead.
 * - Plain typing → propagate the new text.
 */
private fun handleItemEdit(
    new: TextFieldValue,
    previousLocalText: String,
    currentItemText: String,
    setLocal: (TextFieldValue) -> Unit,
    onTextChange: (String) -> Unit,
    onEnter: (remainder: String) -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    splittingEnabled: Boolean,
) {
    if (!new.text.startsWith(ZWSP)) {
        if (new.text.isEmpty()) {
            if (previousLocalText.isEmpty()) {
                onBackspaceOnEmpty()
            } else {
                // User cleared a non-empty row (Select All + Delete, cut, etc.).
                // Keep the row but with empty text — re-seat the sentinel so future
                // edits go through the normal ZWSP-prefixed path. Always propagate
                // the empty text so the VM catches up even if its view was stale.
                setLocal(TextFieldValue(ZWSP, TextRange(ZWSP.length)))
                onTextChange("")
            }
            return
        }
        val corrected = ZWSP + new.text
        setLocal(
            TextFieldValue(
                text = corrected,
                selection = TextRange(new.selection.start + 1, new.selection.end + 1),
            ),
        )
        if (new.text != currentItemText) onTextChange(new.text)
        return
    }

    if (new.text.contains('\n')) {
        val withoutPrefix = new.text.removePrefix(ZWSP)
        if (!splittingEnabled) {
            // Checked rows never split — strip the newline and keep the full text.
            val stripped = withoutPrefix.replace("\n", "")
            val keep = ZWSP + stripped
            setLocal(TextFieldValue(keep, TextRange(keep.length)))
            if (stripped != currentItemText) onTextChange(stripped)
            return
        }
        val newlineIdx = withoutPrefix.indexOf('\n')
        val before = withoutPrefix.substring(0, newlineIdx)
        val after = withoutPrefix.substring(newlineIdx + 1)
        val keep = ZWSP + before
        setLocal(TextFieldValue(keep, TextRange(keep.length)))
        if (before != currentItemText) onTextChange(before)
        onEnter(after)
        return
    }

    setLocal(new)
    val display = new.text.removePrefix(ZWSP)
    if (display != currentItemText) onTextChange(display)
}

private val DELETE_ICON_SIZE = 48.dp
