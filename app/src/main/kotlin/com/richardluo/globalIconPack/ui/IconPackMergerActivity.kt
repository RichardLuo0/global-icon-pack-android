package com.richardluo.globalIconPack.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.components.AppbarSearchBar
import com.richardluo.globalIconPack.ui.components.IconForApp
import com.richardluo.globalIconPack.ui.components.IconPackItem
import com.richardluo.globalIconPack.ui.components.InfoDialog
import com.richardluo.globalIconPack.ui.components.LazyImage
import com.richardluo.globalIconPack.ui.components.LazyListDialog
import com.richardluo.globalIconPack.ui.components.LoadingDialog
import com.richardluo.globalIconPack.ui.components.SampleTheme
import com.richardluo.globalIconPack.ui.components.WarnDialog
import com.richardluo.globalIconPack.ui.viewModel.MergerVM
import com.richardluo.globalIconPack.ui.viewModel.NewAppIconInfo
import com.richardluo.globalIconPack.utils.getValue
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class IconPackMergerActivity : ComponentActivity() {
  private val viewModel: MergerVM by viewModels()

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
  @Preview
  @Composable
  private fun Screen() {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pagerState = rememberPagerState(pageCount = { Page.Count.ordinal })
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage > 0) {
      coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Scaffold(
      modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        Box {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { finish() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
              }
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
              if (pagerState.currentPage == Page.IconList.ordinal)
                IconButton(onClick = { viewModel.expandSearchBar.value = true }) {
                  Icon(Icons.Outlined.Search, stringResource(R.string.search))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            scrollBehavior = scrollBehavior,
          )

          if (pagerState.currentPage == Page.IconList.ordinal)
            AppbarSearchBar(viewModel.expandSearchBar, viewModel.searchText)
        }
      },
      contentWindowInsets = windowInsets,
      floatingActionButton = {
        class FabState(val icon: ImageVector, val text: String, val onClick: () -> Unit)
        val nextStep =
          FabState(Icons.AutoMirrored.Outlined.ArrowForward, getString(R.string.nextStep)) {
            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          }
        val done =
          FabState(Icons.Outlined.Done, getString(R.string.done), viewModel::openWarningDialog)
        val fabState by remember {
          derivedStateOf {
            if (pagerState.currentPage != pagerState.pageCount - 1) nextStep else done
          }
        }
        FloatingActionButton(onClick = fabState.onClick) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(fabState.icon, fabState.text)
            AnimatedVisibility(scrollBehavior.state.contentOffset > -12) {
              AnimatedContent(targetState = fabState, label = "Fab text change") {
                Text(text = it.text, modifier = Modifier.padding(start = 8.dp))
              }
            }
          }
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
        requestIconPackStorage.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
      }

      InfoDialog(
        viewModel.instructionDialogState,
        icon = Icons.Outlined.Notifications,
        title = { Text(getString(R.string.notice)) },
        content = { Text(getString(R.string.mergerInstruction)) },
      )

      if (viewModel.isLoading) LoadingDialog()
    }
  }

  private val requestIconPackStorage =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri -> lifecycleScope.launch { viewModel.createIconPack(uri) } }
      }
    }

  @Composable
  private fun SelectBasePack(pagerState: PagerState) {
    val coroutineScope = rememberCoroutineScope()
    val valueMap = IconPackApps.getFlow(this).getValue(mapOf())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(valueMap.toList()) { (key, value) ->
        IconPackItem(key, value, viewModel.basePackFlow.getValue()) {
          lifecycleScope.launch {
            coroutineScope.async { pagerState.animateScrollToPage(Page.IconList.ordinal) }.await()
            viewModel.basePackFlow.value = key
          }
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
        items(icons.toList(), key = { entry -> entry.first }) { (cn, info) ->
          IconForApp(info.label, key = info, loadImage = { viewModel.loadIcon(info) }) {
            viewModel.openPackDialog(cn)
          }
        }
      }
    else
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(24.dp))
      }

    LazyListDialog(
      viewModel.packDialogState,
      title = { Text(getString(R.string.chooseNewIcon)) },
      value = viewModel.iconsForSelectedApp.collectAsStateWithLifecycle(null).value,
      nothing = {
        Text(
          getString(R.string.noCandidateIconForThisApp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      },
    ) { item, dismiss ->
      NewAppIconItem(item) {
        viewModel.saveNewIcon(item.entry)
        dismiss()
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

  @Composable
  fun NewAppIconItem(info: NewAppIconInfo, onClick: () -> Unit) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(IntrinsicSize.Min)
          .padding(horizontal = 16.dp)
          .clip(MaterialTheme.shapes.extraLarge)
          .clickable(onClick = onClick)
          .padding(horizontal = 8.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) {
        LazyImage(
          info,
          contentDescription = info.app,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
          loadImage = { viewModel.loadIcon(info) },
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          info.packLabel.ifEmpty { getString(R.string.originalIcon) },
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          info.app,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
