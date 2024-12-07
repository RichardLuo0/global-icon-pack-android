package com.richardluo.globalIconPack.iconPack.database

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OpenParams
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.iconPack.loadIconPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.logInApp
import java.io.File

data class Icon(val componentName: ComponentName, val entry: IconEntry)

class WritableIconPackDB(private val context: Context, path: String = "iconPack.db") :
  SQLiteOpenHelper(context, path, null, 1), SharedPreferences.OnSharedPreferenceChangeListener {
  private val db by lazy { writableDatabase }

  init {
    WorldPreference.getPrefInApp(context)
      .also { onSharedPreferenceChanged(it, PrefKey.ICON_PACK) }
      .registerOnSharedPreferenceChangeListener(this)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS \"fallbacks\" (pack TEXT PRIMARY KEY, fallback BLOB)")
  }

  override fun onSharedPreferenceChanged(pref: SharedPreferences, key: String?) {
    if (key != PrefKey.ICON_PACK) return
    val pack =
      pref.getString(PrefKey.ICON_PACK, "")?.takeIf { it.isNotEmpty() }
        ?: run {
          logInApp("No icon pack set")
          return
        }
    val packUpdateTime = context.packageManager.getPackageInfo(pack, 0).lastUpdateTime
    db.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name=?", arrayOf(pack)).use {
      if (it.count > 0 && packUpdateTime < context.getDatabasePath(db.path).lastModified()) return
    }
    db.apply {
      beginTransaction()
      // Create tables
      execSQL("CREATE TABLE IF NOT EXISTS \"$pack\" (packageName TEXT, className TEXT, entry BLOB)")
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS \"${pack}_componentName\" ON \"$pack\" (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS \"${pack}_packageName\" ON \"$pack\" (packageName)")
      // Load icon pack
      val packResources = context.packageManager.getResourcesForApplication(pack)
      loadIconPack(packResources, pack, iconFallback = true, isInMod = false)?.let { info ->
        // Insert icons
        insertIcon(pack, info.iconEntryMap.map { (cn, entry) -> Icon(cn, entry) })
        // Insert fallback
        insertFallbackSettings(
          pack,
          FallbackSettings(info.iconBacks, info.iconUpons, info.iconMasks, info.iconScale),
        )
      }
      // Get installed icon packs
      val appSet = IconPackApps.load(context).keys.joinToString(", ") { "'$it'" }
      // Delete expired fallbacks
      delete("fallbacks", "pack not in (${appSet})", null)
      // Drop expired tables
      rawQuery(
          "SELECT 'DROP TABLE ' || name || ';' FROM sqlite_master " +
            "WHERE type = 'table' AND name NOT IN ($appSet)",
          null,
        )
        .close()
      setTransactionSuccessful()
      endTransaction()
    }
    logInApp("database: $pack updated")
  }

  fun insertFallbackSettings(pack: String, fs: FallbackSettings) {
    db.insertWithOnConflict(
      "fallbacks",
      null,
      ContentValues().apply {
        put("pack", pack)
        put("fallback", fs.toByteArray())
      },
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  fun insertIcon(pack: String, icons: List<Icon>) {
    val insertIcon: SQLiteStatement =
      db.compileStatement("INSERT OR REPLACE INTO \"$pack\" VALUES(?, ?, ?)")
    db.apply {
      beginTransaction()
      runCatching {
        for (icon in icons) {
          insertIcon.apply {
            clearBindings()
            val cn = icon.componentName
            bindString(1, cn.packageName)
            bindString(2, cn.className)
            bindBlob(3, icon.entry.toByteArray())
            execute()
          }
        }
        setTransactionSuccessful()
      }
      endTransaction()
    }
  }

  override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}

class ReadableIconPackDB(file: File) {
  private val db =
    SQLiteDatabase.openDatabase(
      file,
      OpenParams.Builder().setOpenFlags(SQLiteDatabase.OPEN_READONLY).build(),
    )

  fun getFallbackSettings(pack: String) =
    db.query("fallbacks", null, "pack=?", arrayOf(pack), null, null, null, "1")

  private fun getIconExact(pack: String, cn: ComponentName) =
    db.rawQuery(
      "SELECT entry FROM \"$pack\" WHERE packageName=? and className=? LIMIT 1",
      arrayOf(cn.packageName, cn.className),
    )

  private fun getIconFallback(pack: String, cn: ComponentName) =
    db.rawQuery(
      "SELECT entry FROM \"$pack\" WHERE packageName=? ORDER BY " +
        "CASE " +
        "  WHEN className = \"${cn.className}\" THEN 1 " +
        "  WHEN className = \"\" THEN 2 " +
        "  ELSE 3 " +
        "END LIMIT 1",
      arrayOf(cn.packageName),
    )

  fun getIcon(pack: String, cn: ComponentName, fallback: Boolean = false) =
    if (fallback) getIconFallback(pack, cn) else getIconExact(pack, cn)
}
