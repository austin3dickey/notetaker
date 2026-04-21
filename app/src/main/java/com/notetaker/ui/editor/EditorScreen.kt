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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
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
    EditorScreenContent(
        state = state,
        onBack = onBack,
        onTitleChange = viewModel::setTitle,
        onItemTextChange = viewModel::updateItemText,
        onToggleItem = viewModel::toggleChecked,
        onDeleteItem = viewModel::deleteItem,
        onEnterOnItem = { afterPos -> viewModel.addItemAfter(afterPos) },
        onAppendItem = { viewModel.appendItem() },
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
    onEnterOnItem: (afterPosition: Int) -> Unit,
    onAppendItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    onEnterOnItem: (afterPosition: Int) -> Unit,
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
                onEnter = {
                    pendingFocusPosition = item.position + 1
                    onEnterOnItem(item.position)
                },
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
                    onEnter = { /* Enter on a checked item is a no-op */ },
                )
            }
        }
    }
}

@Composable
private fun TitleField(title: String, onTitleChange: (String) -> Unit) {
    var tfv by remember {
        mutableStateOf(TextFieldValue(title, TextRange(title.length)))
    }
    BasicTextField(
        value = tfv,
        onValueChange = {
            tfv = it
            onTitleChange(it.text)
        },
        singleLine = true,
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
    onEnter: () -> Unit,
) {
    // Local TextFieldValue so the cursor doesn't jump on every Flow-driven recomposition.
    // Keyed by item.id so a different row (after delete/reorder) gets a fresh state.
    var tfv by remember(item.id) {
        mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length)))
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
            onValueChange = {
                tfv = it
                onTextChange(it.text)
            },
            textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEnter() }),
            modifier = Modifier
                .weight(1f)
                .testTag("item-text-${item.id}")
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onEnter()
                            true
                        }
                        Key.Backspace -> if (tfv.text.isEmpty()) {
                            onDelete()
                            true
                        } else false
                        else -> false
                    }
                },
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

private val DELETE_ICON_SIZE = 48.dp
