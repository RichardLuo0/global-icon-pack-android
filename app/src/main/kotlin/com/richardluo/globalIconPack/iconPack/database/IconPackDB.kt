package com.richardluo.globalIconPack.iconPack.database

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.iconPack.loadIconPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.log

data class Icon(val componentName: ComponentName, val entry: IconEntry)

class IconPackDB(private val context: Context, path: String = "iconPack.db") :
  SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 2) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS \"fallbacks\" (pack TEXT PRIMARY KEY, fallback BLOB)")
  }

  fun onIconPackChange(pack: String) {
    update(writableDatabase, pack)
  }

  private fun update(db: SQLiteDatabase, inputPack: String) {
    val pack =
      inputPack.takeIf { it.isNotEmpty() }
        ?: run {
          log("No icon pack set")
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
      loadIconPack(packResources, pack, iconFallback = true).let { info ->
        // Insert icons
        insertIcon(db, pack, info.iconEntryMap.map { (cn, entry) -> Icon(cn, entry) })
        // Insert fallback
        insertFallbackSettings(
          db,
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
    log("database: $pack updated")
  }

  fun getFallbackSettings(pack: String) =
    readableDatabase.query("fallbacks", null, "pack=?", arrayOf(pack), null, null, null, "1")

  private fun insertFallbackSettings(db: SQLiteDatabase, pack: String, fs: FallbackSettings) {
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

  private fun getIconExact(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry FROM \"$pack\" WHERE packageName=? and className=? LIMIT 1",
      arrayOf(cn.packageName, cn.className),
    )

  private fun getIconFallback(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
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

  private fun insertIcon(db: SQLiteDatabase, pack: String, icons: List<Icon>) {
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

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.apply {
      rawQuery(
          "SELECT 'DROP TABLE ' || name || ';' FROM sqlite_master " +
            "WHERE type = 'table' AND name != 'fallbacks'",
          null,
        )
        .close()
      WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, "")?.let { update(db, it) }
    }
  }
}
