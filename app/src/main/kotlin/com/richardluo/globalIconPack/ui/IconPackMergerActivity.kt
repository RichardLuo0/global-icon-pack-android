package com.richardluo.globalIconPack.ui

import android.content.ComponentName
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AnimatedFab
import com.richardluo.globalIconPack.ui.components.AppFilterByType
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.ExpandFabScrollConnection
import com.richardluo.globalIconPack.ui.components.FabSnapshot
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconChooserSheet
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.InfoDialog
import com.richardluo.globalIconPack.ui.components.LazyDialog
import com.richardluo.globalIconPack.ui.components.LazyImage
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.ScrollIndicationBox
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.getLabelByType
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.IconPackApps
import com.richardluo.globalIconPack.ui.viewModel.MergerVM
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class IconPackMergerActivity : ComponentActivity() {
  private val viewModel: MergerVM by viewModels()
  private val iconChooser: IconChooserVM by viewModels()
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

    var fabHeight by rememberSaveable { mutableIntStateOf(0) }
    val fabHeightInDp = with(LocalDensity.current) { fabHeight.toDp() }

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
                val expandFilter = rememberSaveable { mutableStateOf(false) }
                IconButtonWithTooltip(
                  Icons.Outlined.FilterList,
                  getLabelByType(viewModel.filterType.value),
                ) {
                  expandFilter.value = true
                }
                AppFilterByType(expandFilter, viewModel.filterType)
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          if (pagerState.currentPage == Page.IconList.ordinal)
            AppbarSearchBar(viewModel.expandSearchBar, viewModel.searchText)
        }
      },
      floatingActionButton = {
        Column(modifier = Modifier.onGloballyPositioned { fabHeight = it.size.height }) {
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
      HorizontalPager(pagerState, contentPadding = contentPadding, beyondViewportPageCount = 2) {
        val contentPadding = PaddingValues(bottom = fabHeightInDp)
        when (it) {
          Page.SelectBasePack.ordinal -> SelectBasePack(pagerState, contentPadding)
          Page.IconList.ordinal -> IconList(contentPadding)
          Page.PackInfoForm.ordinal -> PackInfoForm(contentPadding)
        }
      }

      IconChooserSheet(iconChooser) { viewModel.loadIcon(it to null) }

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
  private fun SelectBasePack(pagerState: PagerState, contentPadding: PaddingValues) {
    val coroutineScope = rememberCoroutineScope()
    val valueMap = IconPackApps.flow.collectAsState(null).value
    if (valueMap == null)
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
      }
    else
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(valueMap.toList()) { (key, value) ->
          IconPackItem(key, value, viewModel.basePack ?: "") {
            coroutineScope.launch {
              pagerState.animateScrollToPage(Page.IconList.ordinal)
              viewModel.basePack = key
            }
          }
        }
      }
  }

  @Composable
  private fun IconList(contentPadding: PaddingValues) {
    val icons = viewModel.filteredIcons.getValue(null)
    if (icons != null)
      LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        contentPadding = contentPadding,
        columns = GridCells.Adaptive(minSize = 74.dp),
      ) {
        items(icons, key = { it.first.componentName }) { pair ->
          val (info, entry) = pair
          AppIcon(
            info.label,
            key =
              if (entry != null) "${entry.pack.pack}/icon/${entry.entry.name}"
              else "${viewModel.basePack}/fallback/${viewModel.iconCacheToken}",
            loadImage = { viewModel.loadIcon(pair) },
          ) {
            iconChooser.open(
              info,
              viewModel.baseIconPack ?: return@AppIcon,
              entry?.entry?.name,
              viewModel::saveNewIcon,
            )
          }
        }
      }
    else
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
      }

    val iconOptionScrollState = rememberLazyListState()
    LazyDialog(
      iconOptionDialogState,
      title = { Text(getString(R.string.options)) },
      value = viewModel.optionsFlow,
    ) {
      ProvidePreferenceLocals(flow = it, myPreferenceTheme()) {
        ScrollIndicationBox(
          modifier = Modifier.padding(top = 8.dp),
          state = iconOptionScrollState,
        ) {
          MainPreference.IconPack(state = it, onlyOptions = true)
        }
      }
    }
  }

  private class FakeIconInfo(packageName: String) : IconInfo(ComponentName(packageName, ""), "")

  @Composable
  private fun PackInfoForm(contentPadding: PaddingValues) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Box(
      modifier =
        Modifier.fillMaxSize().padding(contentPadding).clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ) {
          keyboardController?.hide()
          focusManager.clearFocus(true)
        },
      contentAlignment = Alignment.TopCenter,
    ) {
      Card(modifier = Modifier.padding(8.dp).wrapContentHeight()) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val info = remember(viewModel.basePack) { viewModel.basePack?.let { FakeIconInfo(it) } }
          if (info != null)
            LazyImage(
              key = viewModel.newPackIcon,
              contentDescription = "newPackIcon",
              modifier =
                Modifier.align(Alignment.CenterHorizontally)
                  .clickable {
                    val iconPack = viewModel.baseIconPack ?: return@clickable
                    iconChooser.open(info, iconPack, viewModel.newPackIcon?.entry?.name) {
                      info,
                      icon ->
                      viewModel.newPackIcon =
                        if (icon is VariantPackIcon) IconEntryWithPack(icon.entry, icon.pack)
                        else null
                    }
                  }
                  .padding(12.dp)
                  .size(72.dp),
              contentScale = ContentScale.Crop,
              loadImage = {
                viewModel.newPackIcon?.let { viewModel.loadIcon(info to it) }
                  ?: getDrawable(android.R.drawable.sym_def_app_icon)!!.toBitmap().asImageBitmap()
              },
            )
          OutlinedTextField(
            value = viewModel.newPackName,
            onValueChange = { viewModel.newPackName = it },
            label = { Text(getString(R.string.newPackName)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          OutlinedTextField(
            value = viewModel.newPackPackage,
            onValueChange = { viewModel.newPackPackage = it },
            label = { Text(getString(R.string.newPackPackage)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          Row(
            modifier =
              Modifier.fillMaxWidth().clickable {
                viewModel.installedAppsOnly = !viewModel.installedAppsOnly
              },
            verticalAlignment = Alignment.CenterVertically,
          ) {
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
}
