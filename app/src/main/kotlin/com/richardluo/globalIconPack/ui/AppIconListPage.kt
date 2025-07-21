package com.richardluo.globalIconPack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconChooserSheet
import com.richardluo.globalIconPack.ui.components.LazyImage
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.TwoLineText
import com.richardluo.globalIconPack.ui.components.sharedBounds
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.model.VariantIcon
import com.richardluo.globalIconPack.ui.viewModel.AppIconListVM
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface IconsHolder {
  fun getCurrentIconPack(): IconPack?

  suspend fun loadIcon(pair: Pair<IconInfo, IconEntryWithPack?>): ImageBitmap

  fun saveIcon(info: IconInfo, icon: VariantIcon)
}

private class TabItem(val name: Int, val flow: Flow<List<Pair<IconInfo, IconEntryWithPack?>>?>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconListPage(onBack: () -> Unit, iconsHolder: IconsHolder, vm: AppIconListVM) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val iconChooser: IconChooserVM = viewModel(key = "IconsListPageIconChooser")

  val expandSearchBar = rememberSaveable { mutableStateOf(false) }

  val tabs = remember {
    listOf(
      TabItem(R.string.activities, vm.activityIcons),
      TabItem(R.string.shortcuts, vm.shortcutIcons),
    )
  }
  val pagerState = rememberPagerState(pageCount = { tabs.count() })
  val coroutineScope = rememberCoroutineScope()

  fun openChooser(pair: Pair<IconInfo, IconEntryWithPack?>) {
    val (info, entry) = pair
    iconChooser.open(
      info,
      iconsHolder.getCurrentIconPack() ?: return,
      entry?.entry?.name,
      iconsHolder::saveIcon,
    )
  }

  val (info, entry) = vm.appIcon ?: return

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      Column {
        Box {
          LargeTopAppBar(
            navigationIcon = {
              IconButtonWithTooltip(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            },
            title = {
              val expanded =
                LocalTextStyle.current.fontSize == MaterialTheme.typography.headlineMedium.fontSize
              val packageName = info.componentName.packageName
              if (expanded)
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(end = 18.dp),
                ) {
                  LazyImage(
                    entry?.entry?.name,
                    contentDescription = info.label,
                    modifier =
                      Modifier.size(86.dp).sharedBounds("AppIcon/$packageName").clickable {
                        openChooser(info to entry)
                      },
                    contentScale = ContentScale.Crop,
                    loadImage = { iconsHolder.loadIcon(info to entry) },
                  )
                  Spacer(modifier = Modifier.width(16.dp))
                  Column {
                    TwoLineText(
                      info.label,
                      modifier = Modifier.sharedBounds("AppLabel/$packageName"),
                    )
                    OneLineText(
                      info.componentName.packageName,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                }
              else
                Row(verticalAlignment = Alignment.CenterVertically) {
                  LazyImage(
                    entry?.entry?.name,
                    contentDescription = info.label,
                    modifier = Modifier.size(36.dp).clickable { openChooser(info to entry) },
                    contentScale = ContentScale.Crop,
                    loadImage = { iconsHolder.loadIcon(info to entry) },
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  OneLineText(info.label)
                }
            },
            actions = {
              IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.search)) {
                expandSearchBar.value = true
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          AppbarSearchBar(expandSearchBar, vm.searchText)
        }

        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
          tabs.forEachIndexed { index, item ->
            Tab(
              selected = pagerState.currentPage == index,
              onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
              text = { Text(stringResource(item.name)) },
            )
          }
        }
      }
    },
  ) { contentPadding ->
    val pagePadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    HorizontalPager(
      pagerState,
      contentPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
      beyondViewportPageCount = 0,
    ) {
      IconList(tabs[it].flow.getValue(null), ::openChooser, iconsHolder::loadIcon, pagePadding)
    }

    IconChooserSheet(iconChooser) { iconsHolder.loadIcon(it to null) }
  }
}

@Composable
private fun IconList(
  icons: List<Pair<IconInfo, IconEntryWithPack?>>?,
  onClick: (pair: Pair<IconInfo, IconEntryWithPack?>) -> Unit,
  loadIcon: suspend (pair: Pair<IconInfo, IconEntryWithPack?>) -> ImageBitmap,
  contentPadding: PaddingValues,
) {
  if (icons != null)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
      itemsIndexed(icons, key = { _, it -> it.first.componentName.className }) { index, it ->
        val (info) = it
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .height(IntrinsicSize.Min)
              .clickable { onClick(it) }
              .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LazyImage(
            it.second?.entry?.name,
            contentDescription = info.label,
            modifier = Modifier.size(47.dp),
            contentScale = ContentScale.Crop,
            loadImage = { loadIcon(it) },
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              info.label,
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.bodyLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              info.componentName.shortClassName.ifEmpty { info.componentName.packageName },
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (index < icons.lastIndex) HorizontalDivider()
      }
    }
  else
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}
