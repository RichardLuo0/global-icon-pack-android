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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.richardluo.globalIconPack.utils.asType
import com.richardluo.globalIconPack.utils.mapSaver

typealias RouteArgs = MutableMap<String, Any>

@Composable
fun rememberRouteArgs(saverMap: Map<String, Saver<*, out Any>> = emptyMap()): RouteArgs =
  rememberSaveable(
    saver =
      remember {
        mapSaver(
          save = {
            buildMap {
              it.forEach { (key, value) ->
                val saver = saverMap[key].asType<Saver<Any, *>>()
                val newValue = if (saver == null) value else with(saver) { save(value) }
                newValue?.let { set(key, it) }
              }
            }
          },
          restore = {
            mutableMapOf<String, Any>().apply {
              it.forEach { (key, value) ->
                val saver = saverMap[key].asType<Saver<Any, Any>>()
                val oldValue = if (saver == null) value else saver.restore(value)
                oldValue?.let { set(key, it) }
              }
            }
          },
        )
      }
  ) {
    mutableMapOf()
  }

class NavControllerWithArgs(val navController: NavHostController, val routeArgs: RouteArgs) {

  fun navigate(route: String, arg: Any) {
    routeArgs[route] = arg
    navController.navigate(route)
  }

  fun getArg(route: String) = routeArgs[route]

  fun popBackStack() = navController.popBackStack()
}

val LocalNavControllerWithArgs = compositionLocalOf<NavControllerWithArgs?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedContentScope = compositionLocalOf<AnimatedContentScope?> { null }

open class NavPage(
  val route: String,
  val arguments: List<NamedNavArgument> = emptyList(),
  val argSaver: Saver<*, out Any>? = null,
  val content: @Composable (AnimatedContentScope.() -> Unit),
)

inline fun <reified Arg> navPage(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  argSaver: Saver<Arg, out Any>? = null,
  crossinline content: @Composable (AnimatedContentScope.(Arg) -> Unit),
) =
  NavPage(route, arguments, argSaver) {
    val arg = LocalNavControllerWithArgs.current?.getArg(route)
    if (arg !is Arg) throw Exception("Incorrect route arg type! Route: $route")
    content(arg)
  }

fun navPage(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  content: @Composable (AnimatedContentScope.() -> Unit),
) = NavPage(route, arguments, content = content)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedNavHost(
  modifier: Modifier = Modifier,
  startDestination: String,
  pages: Array<NavPage>,
  navController: NavHostController = rememberNavController(),
  routeArgs: RouteArgs =
    rememberRouteArgs(
      buildMap { pages.forEach { if (it.argSaver != null) set(it.route, it.argSaver) } }
    ),
  contentAlignment: Alignment = Alignment.TopStart,
  route: String? = null,
) {
  SharedTransitionLayout {
    CompositionLocalProvider(
      LocalNavControllerWithArgs provides
        remember(navController, routeArgs) { NavControllerWithArgs(navController, routeArgs) },
      LocalSharedTransitionScope provides this,
    ) {
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
      ) {
        pages.forEach { page ->
          composable(page.route, page.arguments) {
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
                with(page) { content() }
              }
            }
          }
        }
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
    val animatedContentScope = LocalAnimatedContentScope.current
    return@with if (this != null && animatedContentScope != null)
      sharedBounds(rememberSharedContentState(key), animatedContentScope)
    else this@sharedBounds
  }
