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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.OneLineText
import com.richardluo.globalIconPack.ui.components.TwoLineText
import com.richardluo.globalIconPack.ui.components.sharedBounds
import com.richardluo.globalIconPack.ui.model.AnyCompIcon
import com.richardluo.globalIconPack.ui.model.AppCompIcon
import com.richardluo.globalIconPack.ui.model.to
import com.richardluo.globalIconPack.ui.viewModel.AppIconListVM
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.IconsHolder
import com.richardluo.globalIconPack.utils.consumable
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private class TabItem(val name: Int, val flow: Flow<List<AnyCompIcon>?>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconListPage(onBack: () -> Unit, iconsHolder: IconsHolder, appIcon: AppCompIcon) {
  val vm: AppIconListVM = viewModel { with(AppIconListVM) { initializer(iconsHolder, appIcon) } }

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

  fun openChooser(compIcon: AnyCompIcon) {
    val (info, entry) = compIcon
    iconChooser.open(info, iconsHolder.getCurrentIconPack() ?: return, entry?.entry?.name)
  }

  val info = appIcon.info
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
              // Will crash when config changes, issue:
              // https://issuetracker.google.com/issues/435980400
              val expanded =
                LocalTextStyle.current.fontSize == MaterialTheme.typography.headlineMedium.fontSize
              val entry = vm.appIconEntry.getValue()
              val compIcon = AnyCompIcon(info, entry)
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
                        openChooser(compIcon)
                      },
                    contentScale = ContentScale.Crop,
                    loadImage = { iconsHolder.loadIcon(compIcon) },
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
                    modifier = Modifier.size(36.dp).clickable { openChooser(compIcon) },
                    contentScale = ContentScale.Crop,
                    loadImage = { iconsHolder.loadIcon(compIcon) },
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  OneLineText(info.label)
                }
            },
            actions = {
              IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.search)) {
                expandSearchBar.value = true
              }

              var expand by rememberSaveable { mutableStateOf(false) }
              IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.moreOptions)) {
                expand = !expand
              }
              MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.Restore, "restore default") },
                  text = { Text(stringResource(R.string.restoreDefault)) },
                  onClick = {
                    iconsHolder.restoreDefault(info)
                    expand = false
                  },
                )
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Outlined.ClearAll, "clear all") },
                  text = { Text(stringResource(R.string.clearAll)) },
                  onClick = {
                    iconsHolder.clearAll(info)
                    expand = false
                  },
                )
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
    val consumablePadding = contentPadding.consumable()
    val pagePadding = consumablePadding.consumeBottom()

    HorizontalPager(
      pagerState,
      contentPadding = consumablePadding.consume(),
      beyondViewportPageCount = 0,
    ) {
      IconList(tabs[it].flow.getValue(null), pagePadding, iconsHolder::loadIcon, ::openChooser)
    }

    IconChooserSheet(iconChooser, { iconsHolder.loadIcon(it to null) }, iconsHolder::saveIcon)
  }
}

@Composable
private fun IconList(
  icons: List<AnyCompIcon>?,
  contentPadding: PaddingValues,
  loadIcon: suspend (AnyCompIcon) -> ImageBitmap,
  onClick: (AnyCompIcon) -> Unit,
) {
  if (icons != null)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
      itemsIndexed(icons, key = { _, it -> it.info.componentName.className }) { index, it ->
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
            it.entry?.entry?.name,
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
