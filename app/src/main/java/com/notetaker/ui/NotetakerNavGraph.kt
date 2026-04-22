package com.notetaker.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notetaker.data.NoteRepository
import com.notetaker.ui.editor.EditorScreen
import com.notetaker.ui.editor.NoteEditorViewModel
import com.notetaker.ui.overview.NoteOverviewViewModel
import com.notetaker.ui.overview.OverviewScreen
import kotlinx.coroutines.CoroutineScope

/**
 * String-route navigation. We deliberately avoid the type-safe serialization variant so
 * we don't pull in kotlinx.serialization for a two-screen graph.
 */
internal object Routes {
    const val OVERVIEW: String = "overview"
    const val EDITOR_PATTERN: String = "editor/{noteId}"

    fun editor(noteId: Long): String = "editor/$noteId"
}

@Composable
fun NotetakerNavGraph(repository: NoteRepository, applicationScope: CoroutineScope) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) {
            val vm: NoteOverviewViewModel =
                viewModel(factory = NoteOverviewViewModel.factory(repository))
            OverviewScreen(
                viewModel = vm,
                onOpenNote = { noteId ->
                    navController.navigate(Routes.editor(noteId))
                },
            )
        }

        composable(
            route = Routes.EDITOR_PATTERN,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val noteId = requireNotNull(backStackEntry.arguments?.getLong("noteId")) {
                "noteId argument missing from editor route"
            }
            val vm: NoteEditorViewModel =
                viewModel(
                    factory = NoteEditorViewModel.factory(noteId, repository, applicationScope),
                )
            EditorScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
