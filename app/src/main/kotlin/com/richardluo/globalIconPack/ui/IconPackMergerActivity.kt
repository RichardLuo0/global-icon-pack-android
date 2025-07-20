package com.richardluo.globalIconPack.ui

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.ui.MainPreference.fallback
import com.richardluo.globalIconPack.ui.components.AnimatedFab
import com.richardluo.globalIconPack.ui.components.AnimatedNavHost
import com.richardluo.globalIconPack.ui.components.AppFilterByType
import com.richardluo.globalIconPack.ui.components.AppIcon
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.AutoFillDialog
import com.richardluo.globalIconPack.ui.components.ExpandFabScrollConnection
import com.richardluo.globalIconPack.ui.components.FabSnapshot
import com.richardluo.globalIconPack.ui.components.IconButtonWithTooltip
import com.richardluo.globalIconPack.ui.components.IconChooserSheet
import com.richardluo.globalIconPack.ui.components.IconPackItemContent
import com.richardluo.globalIconPack.ui.components.InfoDialog
import com.richardluo.globalIconPack.ui.components.LazyDialog
import com.richardluo.globalIconPack.ui.components.LazyImage
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.MyDropdownMenu
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.ScrollIndicationBox
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.components.getLabelByType
import com.richardluo.globalIconPack.ui.components.myPreferenceTheme
import com.richardluo.globalIconPack.ui.components.navPage
import com.richardluo.globalIconPack.ui.model.IconEntryWithPack
import com.richardluo.globalIconPack.ui.model.IconInfo
import com.richardluo.globalIconPack.ui.model.VariantPackIcon
import com.richardluo.globalIconPack.ui.viewModel.AutoFillVM
import com.richardluo.globalIconPack.ui.viewModel.IconChooserVM
import com.richardluo.globalIconPack.ui.viewModel.IconPackApps
import com.richardluo.globalIconPack.ui.viewModel.MergerVM
import com.richardluo.globalIconPack.ui.viewModel.emptyImageBitmap
import com.richardluo.globalIconPack.utils.IconPackCreator
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class MergerActivityVM : ViewModel() {
  val expandSearchBar = mutableStateOf(false)

  val iconOptionDialogState = mutableStateOf(false)
  val warningDialogState = mutableStateOf(false)
  val instructionDialogState = mutableStateOf(false)

  var waiting by mutableIntStateOf(0)
  var creatingApkProgress = mutableStateOf<IconPackCreator.Progress?>(null)
}

class IconPackMergerActivity : ComponentActivity() {
  private lateinit var navController: NavHostController
  private val vm: MergerVM by viewModels()
  private val avm: MergerActivityVM by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      SampleTheme {
        navController = rememberNavController()
        AnimatedNavHost(navController = navController, startDestination = "Main") {
          navPage("Main") { Screen() }
          navPage("AppIconList") {
            AppIconListPage({ navController.popBackStack() }, vm, vm.appIconListVM)
          }
        }
      }
    }
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
                  avm.expandSearchBar.value = true
                }
                var expand by rememberSaveable { mutableStateOf(false) }
                val expandFilter = rememberSaveable { mutableStateOf(false) }
                val autoFillVM: AutoFillVM = viewModel()
                IconButtonWithTooltip(
                  Icons.Outlined.MoreVert,
                  stringResource(R.string.moreOptions),
                ) {
                  expand = true
                }
                MyDropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.FilterList, "filter") },
                    text = { Text(getLabelByType(vm.filterType.value)) },
                    onClick = {
                      expand = false
                      expandFilter.value = true
                    },
                  )
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.FormatColorFill, "auto fill") },
                    text = { Text(stringResource(R.string.autoFill)) },
                    onClick = {
                      autoFillVM.open(vm.basePack ?: return@DropdownMenuItem)
                      expand = false
                    },
                  )
                }
                AppFilterByType(expandFilter, vm.filterType)
                AutoFillDialog(autoFillVM) {
                  lifecycleScope.launch {
                    avm.waiting++
                    vm.autoFill(it)
                    avm.waiting--
                  }
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollBehavior = scrollBehavior,
          )

          if (pagerState.currentPage == Page.IconList.ordinal)
            AppbarSearchBar(avm.expandSearchBar, vm.searchText)
        }
      },
      floatingActionButton = {
        Column(
          modifier =
            Modifier.onGloballyPositioned { fabY = with(density) { it.positionInRoot().y.toDp() } }
        ) {
          AnimatedVisibility(
            pagerState.currentPage == Page.IconList.ordinal,
            enter = fadeIn() + scaleIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp),
          ) {
            FloatingActionButton(onClick = { avm.iconOptionDialogState.value = true }) {
              Icon(Icons.Outlined.Settings, getString(R.string.options))
            }
          }

          val nextStep = remember {
            FabSnapshot(Icons.AutoMirrored.Outlined.ArrowForward, getString(R.string.nextStep)) {
              coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
          }
          val done = remember {
            FabSnapshot(Icons.Outlined.Done, getString(R.string.done)) {
              if (vm.baseIconPack != null) avm.warningDialogState.value = true
            }
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
      val contentPadding =
        PaddingValues(
          top = contentPadding.calculateTopPadding(),
          bottom = scaffoldBottom - fabY + contentPadding.calculateBottomPadding(),
        )

      HorizontalPager(pagerState, beyondViewportPageCount = 2) {
        when (it) {
          Page.SelectBasePack.ordinal -> SelectBasePack(pagerState, contentPadding)
          Page.IconList.ordinal -> IconList(contentPadding)
          Page.PackInfoForm.ordinal -> PackInfoForm(contentPadding)
        }
      }

      WarnDialog(
        avm.warningDialogState,
        title = { Text(getString(R.string.warning)) },
        onOk = { createIconPackLauncher.launch(null) },
      ) {
        Text(getString(R.string.mergerWarning))
      }

      InfoDialog(
        avm.instructionDialogState,
        icon = Icons.Outlined.Notifications,
        title = { Text(getString(R.string.notice)) },
      ) {
        Text(getString(R.string.mergerInstruction))
      }

      if (avm.waiting > 0) LoadingDialog()

      val progress = avm.creatingApkProgress.value
      if (progress != null)
        LoadingDialog(progress.percentage, "${progress.current}/${progress.total} ${progress.info}")
    }
  }

  private val createIconPackLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
      it ?: return@registerForActivityResult
      vm.createIconPack(it, avm.creatingApkProgress) { avm.instructionDialogState.value = true }
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
        items(valueMap.toList()) { (pack, app) ->
          val selected = pack == vm.basePack
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.large)
                .selectable(selected, true, Role.RadioButton) {
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(Page.IconList.ordinal)
                    vm.basePack = pack
                  }
                }
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              modifier = Modifier.padding(horizontal = 8.dp),
              selected = selected,
              onClick = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconPackItemContent(pack, app)
          }
        }
      }
  }

  @Composable
  private fun IconList(contentPadding: PaddingValues) {
    val icons = vm.filteredIconsFlow.getValue(null)
    if (icons != null)
      LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        contentPadding = contentPadding,
        columns = GridCells.Adaptive(minSize = 74.dp),
      ) {
        items(icons, key = { it.first.componentName }) {
          val (info, entry) = it
          AppIcon(
            info.label,
            key =
              if (entry != null) "${entry.pack.pack}/icon/${entry.entry.name}"
              else "${vm.basePack}/fallback/${vm.iconCacheToken}",
            loadImage = { vm.loadIcon(it) },
            shareKey = info.componentName.packageName,
          ) {
            vm.appIconListVM.setup(it)
            navController.navigate("AppIconList")
          }
        }
      }
    else
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant)
      }

    val iconOptionScrollState = rememberLazyListState()
    LazyDialog(
      avm.iconOptionDialogState,
      title = { Text(getString(R.string.options)) },
      value = vm.optionsFlow,
    ) {
      ProvidePreferenceLocals(flow = it, myPreferenceTheme()) {
        ScrollIndicationBox(
          modifier = Modifier.padding(top = 8.dp),
          state = iconOptionScrollState,
        ) {
          val context = LocalContext.current
          LazyColumn(state = iconOptionScrollState) { fallback(context) }
        }
      }
    }
  }

  @Composable
  private fun PackInfoForm(contentPadding: PaddingValues) {
    val iconChooser: IconChooserVM = viewModel(key = "newPackIconIconChooser")

    val symDefAppIcon = remember {
      getDrawable(android.R.drawable.sym_def_app_icon)?.toBitmap()?.asImageBitmap()
        ?: emptyImageBitmap
    }

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
          LazyImage(
            key = vm.newPackIcon,
            contentDescription = "newPackIcon",
            modifier =
              Modifier.align(Alignment.CenterHorizontally)
                .clickable {
                  val iconPack = vm.baseIconPack ?: return@clickable
                  val info = object : IconInfo(ComponentName(iconPack.pack, ""), "") {}
                  iconChooser.open(info, iconPack, vm.newPackIcon?.entry?.name) { info, icon ->
                    vm.newPackIcon =
                      if (icon is VariantPackIcon) IconEntryWithPack(icon.entry, icon.pack)
                      else null
                  }
                }
                .padding(12.dp)
                .size(72.dp),
            contentScale = ContentScale.Crop,
            loadImage = { vm.newPackIcon?.let { vm.loadIcon(it) } ?: symDefAppIcon },
          )
          OutlinedTextField(
            value = vm.newPackName,
            onValueChange = { vm.newPackName = it },
            label = { Text(getString(R.string.newPackName)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          OutlinedTextField(
            value = vm.newPackPackage,
            onValueChange = { vm.newPackPackage = it },
            label = { Text(getString(R.string.newPackPackage)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          Row(
            modifier =
              Modifier.fillMaxWidth().clickable { vm.installedAppsOnly = !vm.installedAppsOnly },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = vm.installedAppsOnly,
              onCheckedChange = { vm.installedAppsOnly = it },
            )
            Text(text = getString(R.string.installedAppsOnly), modifier = Modifier.fillMaxWidth())
          }
        }
      }
    }

    IconChooserSheet(iconChooser) { symDefAppIcon }
  }
}
