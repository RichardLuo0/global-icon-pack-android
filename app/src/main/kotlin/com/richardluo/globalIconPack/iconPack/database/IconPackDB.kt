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
  SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 3) {

  override fun onCreate(db: SQLiteDatabase) {}

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
    val packTable = pt(pack)
    val packUpdateTime = context.packageManager.getPackageInfo(pack, 0).lastUpdateTime
    db
      .rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name=?", arrayOf(packTable))
      .use {
        if (it.count > 0 && packUpdateTime < context.getDatabasePath(db.path).lastModified()) return
      }
    db.apply {
      beginTransaction()
      // Create tables
      execSQL("CREATE TABLE IF NOT EXISTS 'fallbacks' (pack TEXT PRIMARY KEY, fallback BLOB)")
      execSQL(
        "CREATE TABLE IF NOT EXISTS '$packTable' (packageName TEXT, className TEXT, entry BLOB)"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON '$packTable' (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON '$packTable' (packageName)")
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
      val packs = IconPackApps.load(context).keys
      // Delete expired fallbacks
      val packSet = packs.joinToString(", ") { "'$it'" }
      delete("fallbacks", "pack not in ($packSet)", null)
      // Drop expired tables
      val packTableSet = packs.joinToString(", ") { "'${pt(it)}'" }
      rawQuery(
          "select DISTINCT tbl_name from sqlite_master WHERE tbl_name LIKE '${pt("%")}' AND tbl_name NOT IN ($packTableSet)",
          null,
        )
        .use {
          while (it.moveToNext()) {
            db.execSQL("DROP TABLE '${it.getString(0)}'")
          }
        }
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
      "SELECT entry FROM '${pt(pack)}' WHERE packageName=? and className=? LIMIT 1",
      arrayOf(cn.packageName, cn.className),
    )

  private fun getIconFallback(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry FROM '${pt(pack)}' WHERE packageName=? ORDER BY " +
        "CASE " +
        "  WHEN className = '${cn.className}' THEN 1 " +
        "  WHEN className = '' THEN 2 " +
        "  ELSE 3 " +
        "END LIMIT 1",
      arrayOf(cn.packageName),
    )

  fun getIcon(pack: String, cn: ComponentName, fallback: Boolean = false) =
    if (fallback) getIconFallback(pack, cn) else getIconExact(pack, cn)

  private fun insertIcon(db: SQLiteDatabase, pack: String, icons: List<Icon>) {
    val insertIcon: SQLiteStatement =
      db.compileStatement("INSERT OR REPLACE INTO '${pt(pack)}' VALUES(?, ?, ?)")
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

  private fun pt(pack: String) = "pack_$pack"

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.apply {
      rawQuery(
          "select DISTINCT tbl_name from sqlite_master WHERE name NOT IN ('android_metadata')",
          null,
        )
        .use {
          while (it.moveToNext()) {
            db.execSQL("DROP TABLE '${it.getString(0)}'")
          }
        }
      WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, "")?.let { update(db, it) }
    }
  }
}
