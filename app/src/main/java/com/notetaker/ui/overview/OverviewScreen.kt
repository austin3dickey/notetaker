package com.notetaker.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun OverviewScreen(
    viewModel: NoteOverviewViewModel,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    OverviewScreenContent(
        state = state,
        onOpenNote = onOpenNote,
        onCreateNote = {
            scope.launch {
                val id = viewModel.createNote()
                onOpenNote(id)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewScreenContent(
    state: OverviewState,
    onOpenNote: (Long) -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("notetaker") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNote,
                modifier = Modifier.testTag("new-note-fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        when (state) {
            OverviewState.Loading -> Loading(padding)
            is OverviewState.Loaded ->
                if (state.notes.isEmpty() && state.archived.isEmpty()) Empty(padding)
                else NoteList(
                    active = state.notes,
                    archived = state.archived,
                    padding = padding,
                    onOpenNote = onOpenNote,
                )
        }
    }
}

@Composable
private fun Loading(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun Empty(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No notes yet. Tap + to create one.",
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun NoteList(
    active: List<NoteSummary>,
    archived: List<NoteSummary>,
    padding: PaddingValues,
    onOpenNote: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("note-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = active, key = { "a-${it.id}" }) { summary ->
            NoteCard(summary = summary, onClick = { onOpenNote(summary.id) })
        }

        if (archived.isNotEmpty()) {
            item(key = "archived-header") { ArchivedHeader() }
            items(items = archived, key = { "x-${it.id}" }) { summary ->
                NoteCard(summary = summary, onClick = { onOpenNote(summary.id) })
            }
        }
    }
}

@Composable
private fun ArchivedHeader() {
    Text(
        text = "Archived",
        style = MaterialTheme.typography.titleSmall,
        color = LocalContentColor.current.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archived-header"),
    )
}

@Composable
private fun NoteCard(summary: NoteSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("note-card-${summary.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = summary.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (summary.title.isBlank()) LocalContentColor.current.copy(alpha = 0.5f)
                    else LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary.previewLines.forEach { line ->
                Text(
                    text = "• ${line.ifBlank { " " }}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
