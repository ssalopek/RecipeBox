package com.example.recipebox

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav(vm: RecipeViewModel) {
    val nav = rememberNavController()
    val shared by vm.sharedText.collectAsState()

    LaunchedEffect(shared) {
        if (shared != null) nav.navigate("import") { popUpTo("list") }
    }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            RecipeListScreen(
                vm = vm,
                onAdd = { nav.navigate("edit/0") },
                onOpen = { id -> nav.navigate("view/$id") }
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
            val text = remember { vm.sharedText.value.orEmpty() }
            LaunchedEffect(Unit) { vm.consumeShared() }

            ImportScreen(
                vm = vm,
                sharedText = text,
                onSaved = { nav.popBackStack() },
                onDuplicate = { id -> nav.navigate("view/$id") { popUpTo("list") } },
                onCancel = { nav.popBackStack() }
            )
        }
    }
}