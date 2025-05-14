package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

@Composable
fun AnimatedNavHost(
  navController: NavHostController,
  startDestination: String,
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.TopStart,
  route: String? = null,
  builder: NavGraphBuilder.() -> Unit,
) {
  NavHost(
    navController = navController,
    startDestination = startDestination,
    modifier = modifier,
    contentAlignment = contentAlignment,
    route = route,
    enterTransition = { slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn() },
    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) },
    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) },
    popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut() },
    builder = builder,
  )
}
