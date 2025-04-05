package com.richardluo.globalIconPack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.components.AnimatedFab
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.ChooseIconSheet
import com.richardluo.globalIconPack.ui.components.ExpandFabScrollConnection
import com.richardluo.globalIconPack.ui.components.FabSnapshot
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.InfoDialog
import com.richardluo.globalIconPack.ui.components.LazyDialog
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.viewModel.MergerVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class IconPackMergerActivity : ComponentActivity() {
  private val viewModel: MergerVM by viewModels()
  private val iconOptionDialogState = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SampleTheme { Screen() } }
  }

  enum class Page {
    SelectBasePack,
    IconList,
    PackInfoForm,
    Count,
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Screen() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pagerState = rememberPagerState(pageCount = { Page.Count.ordinal })
    val coroutineScope = rememberCoroutineScope()
    val expandFabScrollConnection = remember {
      object : ExpandFabScrollConnection() {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (available.y > 1) isExpand = true else if (available.y < -1) isExpand = false
          return Offset.Zero
        }
      }
    }

    PredictiveBackHandler(enabled = pagerState.settledPage > 0) { progress ->
      val oriPage = pagerState.currentPage
      val nextPage = oriPage - 1
      try {
        progress.collect { event ->
          if (event.progress < 0.5f) pagerState.scrollToPage(oriPage, -event.progress)
          else pagerState.scrollToPage(nextPage, 1f - event.progress)
        }
        pagerState.animateScrollToPage(nextPage)
      } catch (_: Exception) {
        pagerState.animateScrollToPage(oriPage)
      }
    }

    Scaffold(
      modifier =
        Modifier.fillMaxSize()
          .nestedScroll(scrollBehavior.nestedScrollConnection)
          .nestedScroll(expandFabScrollConnection),
      topBar = {
        Box {
          TopAppBar(
            navigationIcon = {
              IconButtonWithTooltip(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { finish() }
            },
            title = {
              AnimatedContent(targetState = pagerState.currentPage, label = "Title text change") {
                when (it) {
                  Page.SelectBasePack.ordinal -> Text(getString(R.string.chooseBasePack))
                  Page.IconList.ordinal -> Text(getString(R.string.chooseIconToReplace))
                  Page.PackInfoForm.ordinal -> Text(getString(R.string.fillNewPackInfo))
                }
              }
            },
            actions = {
              if (pagerState.currentPage == Page.IconList.ordinal) {
                IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.search)) {
                  viewModel.expandSearchBar.value = true
                }
                val expandFilter = remember { mutableStateOf(false) }
                IconButtonWithTooltip(
                  Icons.Outlined.FilterList,
                  getLabelByType(viewModel.filterAppsVM.type.value),
                ) {
                  expandFilter.value = true
                }
                AppFilterByType(expandFilter, viewModel.filterAppsVM.type)
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          if (pagerState.currentPage == Page.IconList.ordinal)
            AppbarSearchBar(viewModel.expandSearchBar, viewModel.filterAppsVM.searchText)
        }
      },
      floatingActionButton = {
        Column {
          AnimatedVisibility(
            pagerState.currentPage == Page.IconList.ordinal,
            enter = fadeIn() + scaleIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp),
          ) {
            FloatingActionButton(onClick = { iconOptionDialogState.value = true }) {
              Icon(Icons.Outlined.Settings, getString(R.string.options))
            }
          }

          val nextStep = remember {
            FabSnapshot(Icons.AutoMirrored.Outlined.ArrowForward, getString(R.string.nextStep)) {
              coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
          }
          val done = remember {
            FabSnapshot(Icons.Outlined.Done, getString(R.string.done), viewModel::openWarningDialog)
          }
          AnimatedFab(
            remember {
              derivedStateOf {
                if (pagerState.currentPage != pagerState.pageCount - 1) nextStep else done
              }
            },
            expandFabScrollConnection.isExpand,
          )
        }
      },
    ) { contentPadding ->
      HorizontalPager(pagerState, contentPadding = contentPadding, beyondViewportPageCount = 1) {
        when (it) {
          Page.SelectBasePack.ordinal -> SelectBasePack(pagerState)
          Page.IconList.ordinal -> IconList()
          Page.PackInfoForm.ordinal -> PackInfoForm()
        }
      }

      WarnDialog(
        viewModel.warningDialogState,
        title = { Text(getString(R.string.warning)) },
        content = { Text(getString(R.string.mergerWarning)) },
      ) {
        createIconPackLauncher.launch(null)
      }

      InfoDialog(
        viewModel.instructionDialogState,
        icon = Icons.Outlined.Notifications,
        title = { Text(getString(R.string.notice)) },
        content = { Text(getString(R.string.mergerInstruction)) },
      )

      if (viewModel.isCreatingApk) LoadingDialog()
    }
  }

  private val createIconPackLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
      if (it != null) viewModel.createIconPack(it)
    }

  @Composable
  private fun SelectBasePack(pagerState: PagerState) {
    val coroutineScope = rememberCoroutineScope()
    val valueMap = IconPackApps.getFlow(this).getValue(mapOf())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(valueMap.toList()) { (key, value) ->
        IconPackItem(key, value, viewModel.basePack) {
          coroutineScope.launch { pagerState.animateScrollToPage(Page.IconList.ordinal) }
          viewModel.basePack = key
        }
      }
    }
  }

  @Composable
  private fun IconList() {
    val icons = viewModel.filteredIcons.getValue(null)
    if (icons != null)
      LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        columns = GridCells.Adaptive(minSize = 74.dp),
      ) {
        items(icons.toList(), key = { it.first.componentName }) { pair ->
          val (info, entry) = pair
          IconForApp(
            info.label,
            key = "${viewModel.basePack}/${entry?.entry?.name ?: ""}/${viewModel.iconCacheToken}",
            loadImage = { viewModel.loadIcon(pair) },
          ) {
            viewModel.chooseIconVM.openVariantSheet(info)
          }
        }
      }
    else
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
      }

    ChooseIconSheet(viewModel.chooseIconVM, viewModel::saveNewIcon)

    LazyDialog(
      iconOptionDialogState,
      title = { Text(getString(R.string.options)) },
      value = viewModel.optionsFlow,
    ) {
      ProvidePreferenceLocals(flow = it, myPreferenceTheme()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          MainPreference.run { iconPack(this@IconPackMergerActivity, true) }
        }
      }
    }
  }

  @Composable
  private fun PackInfoForm() {
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "newPackName", contentType = "TextField") {
        OutlinedTextField(
          value = viewModel.newPackName,
          onValueChange = { viewModel.newPackName = it },
          label = { Text(getString(R.string.newPackName)) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      item(key = "newPackPackage", contentType = "TextField") {
        OutlinedTextField(
          value = viewModel.newPackPackage,
          onValueChange = { viewModel.newPackPackage = it },
          label = { Text(getString(R.string.newPackPackage)) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      item(key = "installedAppsOnly", contentType = "Checkbox") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = viewModel.installedAppsOnly,
            onCheckedChange = { viewModel.installedAppsOnly = it },
          )
          Text(text = getString(R.string.installedAppsOnly), modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}
