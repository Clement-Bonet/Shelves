package com.katachilabs.shelves

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.katachilabs.shelves.ui.add.AddBookScreen
import com.katachilabs.shelves.ui.book.BookScreen
import com.katachilabs.shelves.ui.loans.LoansScreen
import com.katachilabs.shelves.ui.series.SeriesScreen
import com.katachilabs.shelves.ui.settings.SettingsScreen
import com.katachilabs.shelves.ui.shelf.ShelfScreen
import com.katachilabs.shelves.ui.shelves.ShelvesScreen
import com.katachilabs.shelves.ui.theme.ShelvesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShelvesTheme { ShelvesNavHost() }
        }
    }
}

/** -1 tient lieu de « pas d'étagère » dans les arguments de navigation. */
private const val NO_SHELF = -1L

@Composable
private fun ShelvesNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    fun openBook(id: Long) = nav.navigate("book/$id")
    fun openSeries(id: String, shelfId: Long?) = nav.navigate("series/${Uri.encode(id)}?shelfId=${shelfId ?: NO_SHELF}")

    NavHost(navController = nav, startDestination = "shelves") {
        composable("shelves") {
            ShelvesScreen(
                onOpenShelf = { nav.navigate("shelf/$it") },
                onOpenLoans = { nav.navigate("loans") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("shelf/{shelfId}", arguments = listOf(navArgument("shelfId") { type = NavType.LongType })) { entry ->
            val shelfId = entry.arguments!!.getLong("shelfId")
            ShelfScreen(
                shelfId = shelfId,
                onBack = back,
                onAddBook = { nav.navigate("add?shelfId=$shelfId") },
                onOpenBook = ::openBook,
                onOpenSeries = { openSeries(it, shelfId) },
            )
        }
        composable(
            "add?shelfId={shelfId}",
            arguments = listOf(navArgument("shelfId") { type = NavType.LongType; defaultValue = NO_SHELF }),
        ) { entry ->
            AddBookScreen(
                shelfId = entry.arguments!!.getLong("shelfId").takeIf { it != NO_SHELF },
                onBack = back,
                onSaved = { bookId ->
                    nav.navigate("book/$bookId") { popUpTo("add?shelfId={shelfId}") { inclusive = true } }
                },
            )
        }
        composable("book/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { entry ->
            BookScreen(
                bookId = entry.arguments!!.getLong("bookId"),
                onBack = back,
                onOpenSeries = { openSeries(it, null) },
            )
        }
        composable(
            "series/{seriesId}?shelfId={shelfId}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType },
                navArgument("shelfId") { type = NavType.LongType; defaultValue = NO_SHELF },
            ),
        ) { entry ->
            val args = entry.arguments!!
            SeriesScreen(
                seriesId = Uri.decode(args.getString("seriesId")!!),
                shelfId = args.getLong("shelfId").takeIf { it != NO_SHELF },
                onBack = back,
                onOpenBook = ::openBook,
                onMerged = { targetId ->
                    // La série fusionnée est vide : on affiche à sa place la série qui la reçoit.
                    nav.navigate("series/${Uri.encode(targetId)}?shelfId=${args.getLong("shelfId")}") {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                },
            )
        }
        composable("loans") { LoansScreen(onBack = back, onOpenBook = ::openBook) }
        composable("settings") { SettingsScreen(onBack = back) }
    }
}
