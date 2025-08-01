package com.richardluo.globalIconPack.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pinnedScrollBehaviorWithPager(
  pagerState: PagerState,
  topAppBarState: TopAppBarState = rememberTopAppBarState(),
) =
  remember(topAppBarState) {
      object : TopAppBarScrollBehavior {
        override val state = topAppBarState
        override val isPinned: Boolean = true
        override val snapAnimationSpec: AnimationSpec<Float>? = null
        override val flingAnimationSpec: DecayAnimationSpec<Float>? = null
        private val pageContentOffsets =
          arrayOf(state.contentOffset, state.contentOffset, state.contentOffset)

        fun onPageChange() {
          state.contentOffset = pageContentOffsets[pagerState.currentPage]
        }

        override val nestedScrollConnection =
          object : NestedScrollConnection {
            override fun onPostScroll(
              consumed: Offset,
              available: Offset,
              source: NestedScrollSource,
            ): Offset {
              pageContentOffsets[pagerState.currentPage] += consumed.y
              state.contentOffset = pageContentOffsets[pagerState.currentPage]
              return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
              if (available.y > 0f) {
                // Reset the total content offset to zero when scrolling all the way down.
                // This will eliminate some float precision inaccuracies.
                pageContentOffsets[pagerState.currentPage] = 0f
                state.contentOffset = 0f
              }
              return super.onPostFling(consumed, available)
            }
          }
      }
    }
    .also { LaunchedEffect(pagerState.currentPage) { it.onPageChange() } }
