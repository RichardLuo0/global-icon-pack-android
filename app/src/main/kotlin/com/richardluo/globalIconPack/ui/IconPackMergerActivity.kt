package com.richardluo.globalIconPack.ui

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.components.AnimatedFab
import com.richardluo.globalIconPack.ui.components.AnimatedNavHost
import com.richardluo.globalIconPack.ui.components.AppFilterButtonGroup
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AutoFillDialog
import com.richardluo.globalIconPack.ui.components.ExpandedScrollConnection
import com.richardluo.globalIconPack.ui.components.FabDesc
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconChooserSheet
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.InfoDialog
import com.richardluo.globalIconPack.ui.components.LazyDialog
import com.richardluo.globalIconPack.ui.components.LazyImage
import com.richardluo.globalIconPack.ui.components.ListItemPos
import com.richardluo.globalIconPack.ui.components.LoadingCircle
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.LocalNavControllerWithArgs
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.ScrollIndicationBox
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.WithSearch
import com.richardluo.globalIconPack.ui.components.appFilterHeight
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.components.navPage
import com.richardluo.globalIconPack.ui.components.pinnedScrollBehaviorWithPager
import com.richardluo.globalIconPack.ui.components.toShape
import com.richardluo.globalIconPack.ui.model.AppCompIcon
import com.richardluo.globalIconPack.ui.model.CompInfo
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.repo.IconPackApps
import com.richardluo.globalIconPack.ui.state.rememberAutoFillState
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.MergerVM
import com.richardluo.globalIconPack.ui.viewModel.emptyImageBitmap
import com.richardluo.globalIconPack.utils.ConsumablePadding
import com.richardluo.globalIconPack.utils.consumable
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class IconPackMergerActivity : ComponentActivity() {
  private val vm: MergerVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      SampleTheme {
        AnimatedNavHost(
          startDestination = "Main",
          pages =
            arrayOf(
              navPage("Main") { Screen() },
              navPage<AppCompIcon>("AppIconList") {
                val navController = LocalNavControllerWithArgs.current!!
                AppIconListPage({ navController.popBackStack() }, vm, it)
              },
            ),
        )
      }
    }
  }

  private class Page(
    val title: String,
    val actions: @Composable (RowScope.() -> Unit)? = null,
    val fab: @Composable (() -> Unit)? = null,
    val animatedFab: FabDesc? = null,
    val screen: @Composable (ConsumablePadding) -> Unit,
  )

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Screen() {
    val expandSearchBar = rememberSaveable { mutableStateOf(false) }
    val iconOptionDialogState = rememberSaveable { mutableStateOf(false) }
    val warningDialogState = rememberSaveable { mutableStateOf(false) }
    val instructionDialogState = rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val scrollBehavior = pinnedScrollBehaviorWithPager(pagerState)
    val expandedScrollConnection = remember { ExpandedScrollConnection() }
    LaunchedEffect(pagerState.currentPage) { expandedScrollConnection.expanded = true }

    val coroutineScope = rememberCoroutineScope()
    val nextStep = remember {
      FabDesc(Icons.AutoMirrored.Outlined.ArrowForward, getString(R.string.merger_nextStep)) {
        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
      }
    }
    val done = remember {
      FabDesc(Icons.Outlined.Done, getString(R.string.merger_done)) {
        if (vm.baseIconPack != null) warningDialogState.value = true
      }
    }

    val pages = remember {
      arrayOf(
        Page(getString(R.string.merger_basePack_title), animatedFab = nextStep) {
          SelectBasePack(it) { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
        },
        Page(
          getString(R.string.merger_replaceIcons_title),
          actions = { IconListActions(expandSearchBar) },
          fab = {
            FloatingActionButton(onClick = { iconOptionDialogState.value = true }) {
              Icon(Icons.Outlined.Settings, getString(R.string.common_options))
            }
          },
          animatedFab = nextStep,
        ) {
          IconList(it, iconOptionDialogState, expandedScrollConnection)
        },
        Page(getString(R.string.merger_newPack_title), animatedFab = done) { PackInfoForm(it) },
      )
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

    val density = LocalDensity.current
    var scaffoldBottom by remember { mutableStateOf(0.dp) }
    var fabY by remember { mutableStateOf(0.dp) }

    Scaffold(
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned {
            scaffoldBottom = with(density) { (it.positionInRoot().y + it.size.height).toDp() }
          }
          .nestedScroll(scrollBehavior.nestedScrollConnection)
          .nestedScroll(expandedScrollConnection),
      topBar = {
        WithSearch(expandSearchBar, vm.searchText) {
          TopAppBar(
            navigationIcon = {
              IconButtonWithTooltip(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { finish() }
            },
            title = {
              AnimatedContent(targetState = pagerState.currentPage) { Text(pages[it].title) }
            },
            actions = {
              pages.forEachIndexed { i, it ->
                val actions = it.actions
                if (actions != null)
                  AnimatedVisibility(
                    pagerState.currentPage == i,
                    enter = fadeIn(),
                    exit = fadeOut(),
                  ) {
                    Row(content = actions)
                  }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )
        }
      },
      floatingActionButton = {
        Column(
          modifier =
            Modifier.onGloballyPositioned { fabY = with(density) { it.positionInRoot().y.toDp() } }
        ) {
          pages.forEachIndexed { i, it ->
            val fab = it.fab
            if (fab != null)
              AnimatedVisibility(
                pagerState.currentPage == i,
                enter = scaleIn(),
                exit = scaleOut(),
                modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp),
              ) {
                fab()
              }
          }

          val fabState = pages[pagerState.currentPage].animatedFab
          if (fabState != null) AnimatedFab(fabState, expandedScrollConnection.expanded)
        }
      },
    ) { contentPadding ->
      val consumablePadding = contentPadding.consumable()
      val pagePadding =
        ConsumablePadding(bottom = scaffoldBottom - fabY + consumablePadding.consumeBottomValue())

      HorizontalPager(
        pagerState,
        contentPadding = consumablePadding.consume(),
        beyondViewportPageCount = 2,
      ) {
        pages[it].screen(pagePadding)
      }
    }

    val createIconPackLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it ?: return@rememberLauncherForActivityResult
        vm.createIconPack(it) { instructionDialogState.value = true }
      }

    WarnDialog(
      warningDialogState,
      title = { Text(getString(R.string.common_warning)) },
      onOk = { createIconPackLauncher.launch(null) },
    ) {
      Text(getString(R.string.merger_warn_generated))
    }

    InfoDialog(
      instructionDialogState,
      icon = Icons.Outlined.Notifications,
      title = { Text(getString(R.string.common_notice)) },
    ) {
      Text(getString(R.string.merger_info_instruction))
    }

    if (vm.loading > 0) LoadingDialog()

    val progress = vm.creatingApkProgress
    if (progress != null)
      LoadingDialog(progress.percentage, "${progress.current}/${progress.total} ${progress.info}")
  }

  @Composable
  private fun SelectBasePack(consumablePadding: ConsumablePadding, scrollToNextPage: () -> Unit) {
    val valueMap = IconPackApps.flow.collectAsState(null).value
    if (valueMap == null) LoadingCircle()
    else
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
          modifier = Modifier.widthIn(max = 400.dp).fillMaxHeight(),
          contentPadding = consumablePadding.consume(),
        ) {
          itemsIndexed(valueMap.toList()) { index, (pack, app) ->
            val selected = pack == vm.basePack
            IconPackItem(pack, app, selected, ListItemPos.from(index, valueMap.size).toShape()) {
              vm.basePack = pack
              scrollToNextPage()
            }
          }
        }
      }
  }

  @Composable
  private fun IconList(
    consumablePadding: ConsumablePadding,
    iconOptionDialogState: MutableState<Boolean>,
    scrollConnection: ExpandedScrollConnection,
  ) {
    val navController = LocalNavControllerWithArgs.current!!

    Box(modifier = Modifier.padding(consumablePadding.consumeTop())) {
      val icons = vm.filteredIconsFlow.getValue(null)
      if (icons != null)
        LazyVerticalGrid(
          modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
          contentPadding = consumablePadding.apply { top += appFilterHeight }.consume(),
          columns = GridCells.Adaptive(minSize = 74.dp),
        ) {
          items(icons, key = { it.info.componentName }) {
            val (info, entry) = it
            AppIcon(
              info.label,
              key =
                if (entry != null) "${entry.pack.pack}/icon/${entry.entry.name}"
                else "${vm.basePack}/fallback/${vm.iconCacheToken}",
              loadImage = { vm.loadIcon(it) },
              shareKey = info.componentName.packageName,
            ) {
              navController.navigate("AppIconList", it)
            }
          }
        }
      else LoadingCircle()

      val animatedFloat by
        animateFloatAsState(targetValue = if (scrollConnection.expanded) 0f else 1f)
      AppFilterButtonGroup(
        Modifier.padding(horizontal = 8.dp)
          .fillMaxWidth()
          .offset(y = -appFilterHeight * animatedFloat),
        vm.filterType,
      )
    }

    val iconOptionScrollState = rememberLazyListState()
    LazyDialog(
      iconOptionDialogState,
      title = { Text(getString(R.string.common_options)) },
      value = vm.optionsFlow,
    ) {
      ProvidePreferenceLocals(flow = it, myPreferenceTheme()) {
        ScrollIndicationBox(
          modifier = Modifier.padding(top = 8.dp),
          state = iconOptionScrollState,
        ) {
          MainPreference.Fallback(state = it)
        }
      }
    }
  }

  @Composable
  private fun IconListActions(expandSearchBar: MutableState<Boolean>) {
    IconButtonWithTooltip(Icons.Outlined.Search, stringResource(R.string.common_search)) {
      expandSearchBar.value = true
    }
    var expand by rememberSaveable { mutableStateOf(false) }
    val autoFillState = rememberAutoFillState()
    IconButtonWithTooltip(Icons.Outlined.MoreVert, stringResource(R.string.common_moreOptions)) {
      expand = true
    }
    MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
      DropdownMenuItem(
        leadingIcon = { Icon(Icons.Outlined.FormatColorFill, "auto fill") },
        text = { Text(stringResource(R.string.autoFill)) },
        onClick = {
          autoFillState.open(vm.basePack ?: return@DropdownMenuItem)
          expand = false
        },
      )
      DropdownMenuItem(
        leadingIcon = { Icon(Icons.Outlined.Restore, "restore default") },
        text = { Text(stringResource(R.string.icons_restoreDefault)) },
        onClick = {
          vm.restoreDefault()
          expand = false
        },
      )
    }
    AutoFillDialog(autoFillState) { vm.autoFill(it) }
  }

  @Parcelize
  private class NewPackCompInfo(
    override val componentName: ComponentName,
    override val label: String,
  ) : CompInfo() {
    constructor(pack: String) : this(ComponentName(pack, ""), "")

    override fun getIcon(context: Context) = null
  }

  @Composable
  private fun PackInfoForm(consumablePadding: ConsumablePadding) {
    val iconChooser: IconChooserVM = viewModel(key = "newPackIconIconChooser")

    val symDefAppIcon = remember {
      getDrawable(android.R.drawable.sym_def_app_icon)?.toBitmap()?.asImageBitmap()
        ?: emptyImageBitmap
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(
      modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ) {
          keyboardController?.hide()
          focusManager.clearFocus(true)
        },
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(consumablePadding.top))
      Card(
        modifier = Modifier.padding(8.dp).wrapContentHeight().widthIn(max = 400.dp),
        colors =
          CardDefaults.cardColors()
            .copy(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          LazyImage(
            key = vm.newPackIcon,
            contentDescription = "newPackIcon",
            modifier =
              Modifier.align(Alignment.CenterHorizontally)
                .clickable {
                  val iconPack = vm.baseIconPack ?: return@clickable
                  iconChooser.open(NewPackCompInfo(iconPack.pack), iconPack)
                }
                .padding(12.dp)
                .size(72.dp),
            contentScale = ContentScale.Crop,
            loadImage = { vm.newPackIcon?.let { vm.loadIcon(it) } ?: symDefAppIcon },
          )
          OutlinedTextField(
            value = vm.newPackName,
            onValueChange = { vm.newPackName = it },
            label = { Text(getString(R.string.merger_newPack_Name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          OutlinedTextField(
            value = vm.newPackPackage,
            onValueChange = { vm.newPackPackage = it },
            label = { Text(getString(R.string.merger_newPack_Package)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          Row(
            modifier =
              Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                vm.installedAppsOnly = !vm.installedAppsOnly
              },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = vm.installedAppsOnly,
              onCheckedChange = { vm.installedAppsOnly = it },
            )
            Text(
              text = getString(R.string.merger_newPack_installedAppsOnly),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(consumablePadding.bottom))
    }

    IconChooserSheet(iconChooser, { symDefAppIcon }) { info, icon ->
      vm.newPackIcon =
        if (icon is VariantPackIcon) IconEntryWithPack(icon.entry, icon.pack) else null
    }
  }
}
