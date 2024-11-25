package com.richardluo.globalIconPack

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.getPreferenceFlow
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

@Composable
@SuppressLint("WorldReadableFiles")
fun worldPreferenceFlow(): MutableStateFlow<Preferences> {
    val context = LocalContext.current
    @Suppress("DEPRECATION") return context.getSharedPreferences(
        PreferenceManager.getDefaultSharedPreferencesName(context), Context.MODE_WORLD_READABLE
    ).getPreferenceFlow()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ProvidePreferenceLocals(flow = worldPreferenceFlow()) {
                    SampleScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleScreen() {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val context = LocalContext.current
            val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
            TopAppBar(
                title = { Text(text = appLabel) },
                modifier = Modifier.fillMaxWidth(),
                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentColor = contentColorFor(MaterialTheme.colorScheme.background),
        contentWindowInsets = windowInsets,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = contentPadding
        ) {
            preferenceCategory(
                key = "general",
                title = { Text(text = "General") },
            )
            textFieldPreference(
                key = "iconPack",
                defaultValue = "",
                title = { Text(text = "Icon pack") },
                textToValue = { it },
                summary = { Text(text = it) },
            )
            switchPreference(
                key = "noForceShape",
                defaultValue = true,
                title = { Text(text = "No force shape") },
                summary = { Text(text = if (it) "On" else "Off") },
            )
            preferenceCategory(
                key = "iconPackSettings",
                title = { Text(text = "Icon pack settings") },
            )
            switchPreference(
                key = "noIconBack",
                defaultValue = false,
                title = { Text(text = "No icon back") },
                summary = { Text(text = if (it) "On" else "Off") },
            )
            switchPreference(
                key = "noIconUpon",
                defaultValue = false,
                title = { Text(text = "No icon upon") },
                summary = { Text(text = if (it) "On" else "Off") },
            )
            switchPreference(
                key = "noIconMask",
                defaultValue = false,
                title = { Text(text = "No icon mask") },
                summary = { Text(text = if (it) "On" else "Off") },
            )
            switchPreference(
                key = "noScale",
                defaultValue = false,
                title = { Text(text = "No scale") },
                summary = { Text(text = if (it) "On" else "Off") },
            )
        }
    }
}