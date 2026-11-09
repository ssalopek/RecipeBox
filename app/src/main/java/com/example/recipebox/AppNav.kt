package com.example.recipebox

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav(vm: RecipeViewModel) {
    val nav = rememberNavController()
    val shared by vm.sharedText.collectAsState()

    // A new share always lands on a fresh import screen on top of the list.
    LaunchedEffect(shared) {
        if (shared != null) {
            nav.navigate("import") { popUpTo("list") }
            vm.consumeShared()
        }
    }

    // If the app was restarted by Android while the import screen was open, the draft is gone.
    // Don't show an empty import screen, go back to the list.
    val backStackEntry by nav.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        if (backStackEntry?.destination?.route == "import" && vm.importDraft == null) {
            nav.popBackStack()
        }
    }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            RecipeListScreen(
                vm = vm,
                onAdd = { nav.navigate("edit/0") },
                onOpen = { id -> nav.navigate("view/$id") },
                onEdit = { id -> nav.navigate("edit/$id") }
            )
        }
        composable(
            route = "view/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            RecipeViewScreen(
                vm = vm,
                id = id,
                onEdit = { nav.navigate("edit/$id") },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            RecipeEditScreen(
                vm = vm,
                id = entry.arguments?.getLong("id") ?: 0L,
                onDone = { nav.popBackStack() },
                onDeleted = { nav.popBackStack("list", inclusive = false) }
            )
        }
        composable("import") {
            ImportScreen(
                vm = vm,
                onSaved = {
                    nav.popBackStack()
                    vm.finishImport()
                },
                onDuplicate = { id ->
                    nav.navigate("view/$id") { popUpTo("list") }
                    vm.finishImport()
                },
                onCancel = {
                    nav.popBackStack()
                    vm.finishImport()
                }
            )
        }
    }
}
