package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
  compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedContentScope = compositionLocalOf<AnimatedContentScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedNavHost(
  navController: NavHostController,
  startDestination: String,
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.TopStart,
  route: String? = null,
  builder: NavGraphBuilder.() -> Unit,
) {
  SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        contentAlignment = contentAlignment,
        route = route,
        enterTransition = { slideInto() },
        exitTransition = { slideOut { it / 4 } },
        popEnterTransition = { slideInto(SlideDirection.End) { it / 4 } },
        popExitTransition = { slideOut(SlideDirection.End) },
        builder = builder,
      )
    }
  }
}

private fun AnimatedContentTransitionScope<*>.slideInto(
  towards: SlideDirection = SlideDirection.Start,
  initialOffset: (offsetForFullSlide: Int) -> Int = { it },
) =
  slideIntoContainer(
    towards,
    spring(
      stiffness = Spring.StiffnessMediumLow,
      visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
    initialOffset,
  )

private fun AnimatedContentTransitionScope<*>.slideOut(
  towards: SlideDirection = SlideDirection.Start,
  targetOffset: (fullWidth: Int) -> Int = { it },
) =
  slideOutOfContainer(
    towards,
    spring(
      stiffness = Spring.StiffnessMediumLow,
      visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
    targetOffset,
  )

fun NavGraphBuilder.navPage(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
  composable(route, arguments) {
    CompositionLocalProvider(LocalAnimatedContentScope provides this) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .shadow(
              elevation = 16.dp,
              shape = MaterialTheme.shapes.large,
              clip = transition.isRunning,
            )
      ) {
        content(it)
      }
    }
  }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBounds(key: Any) =
  with(LocalSharedTransitionScope.current) {
    val animatedContentScope = LocalAnimatedContentScope.current
    return@with if (this != null && animatedContentScope != null)
      sharedBounds(rememberSharedContentState(key), animatedContentScope)
    else this@sharedBounds
  }
