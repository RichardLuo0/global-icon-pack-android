package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import kotlin.reflect.KClass

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalBackStack = compositionLocalOf<NavBackStack<NavKey>?> { null }

class AnimatedEntryProviderScope<T : NavKey>(val provider: EntryProviderScope<T>) {

  @Composable
  fun <K> AnimatedContent(key: K, content: @Composable ((K) -> Unit)) {
    val transition = LocalNavAnimatedContentScope.current.transition
    Box(
      modifier =
        Modifier.fillMaxSize()
          .shadow(
            elevation = 16.dp,
            shape = MaterialTheme.shapes.large,
            clip = transition.isRunning,
          )
    ) {
      content(key)
    }
  }

  fun <K : T> entry(key: K, content: @Composable ((K) -> Unit)) {
    provider.addEntryProvider(key) { AnimatedContent(it, content) }
  }

  fun <K : T> entry(clazz: KClass<K>, content: @Composable ((K) -> Unit)) {
    provider.addEntryProvider(clazz) { AnimatedContent(it, content) }
  }

  inline fun <reified K : T> entry(noinline content: @Composable ((K) -> Unit)) {
    provider.addEntryProvider(K::class) { AnimatedContent(it, content) }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedNavHost(vararg init: NavKey, builder: AnimatedEntryProviderScope<NavKey>.() -> Unit) {
  SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
      val backStack = rememberNavBackStack(*init)
      CompositionLocalProvider(LocalBackStack provides backStack) {
        NavDisplay(
          backStack = backStack,
          onBack = { backStack.removeLastOrNull() },
          entryDecorators =
            listOf(
              rememberSaveableStateHolderNavEntryDecorator(),
              rememberViewModelStoreNavEntryDecorator(),
            ),
          transitionSpec = { slideInto() togetherWith slideOut { it / 4 } },
          popTransitionSpec = {
            slideInto(SlideDirection.End) { it / 4 } togetherWith
              slideOut(SlideDirection.End) { it }
          },
          predictivePopTransitionSpec = { edge ->
            val direction =
              if (edge == NavigationEvent.EDGE_RIGHT) SlideDirection.Start else SlideDirection.End
            slideInto(direction) { it / 4 } togetherWith slideOut(direction) { it }
          },
          entryProvider = entryProvider { AnimatedEntryProviderScope(this).builder() },
        )
      }
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

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBounds(key: Any) =
  with(LocalSharedTransitionScope.current) {
    if (this == null) return@with this@sharedBounds
    val animatedContentScope = LocalNavAnimatedContentScope.current
    return@with sharedBounds(rememberSharedContentState(key), animatedContentScope)
  }
