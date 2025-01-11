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
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getLong
import com.richardluo.globalIconPack.utils.log
import kotlinx.coroutines.runBlocking

class IconPackDB(private val context: Context, path: String = "iconPack.db") :
  SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 4) {

  override fun onCreate(db: SQLiteDatabase) {}

  fun onIconPackChange(pack: String) {
    update(writableDatabase, pack)
  }

  private fun update(db: SQLiteDatabase, pack: String) {
    pack.ifEmpty {
      log("No icon pack set")
      return
    }
    db.apply {
      execSQL(
        "CREATE TABLE IF NOT EXISTS 'fallbacks' (pack TEXT PRIMARY KEY, fallback BLOB, updateAt NUMERIC)"
      )
      val packTable = pt(pack)
      rawQuery("select DISTINCT updateAt from fallbacks where pack=?", arrayOf(pack)).use { c ->
        if (
          context.packageManager.getPackageInfo(pack, 0).lastUpdateTime <
            (c.getFirstRow { it.getLong("updateAt") } ?: 0)
        )
          return
      }
      // Create tables
      execSQL(
        "CREATE TABLE IF NOT EXISTS '$packTable' (packageName TEXT, className TEXT, entry BLOB)"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON '$packTable' (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON '$packTable' (packageName)")
      beginTransaction()
      try {
        // Load icon pack
        loadIconPack(
            context.packageManager.getResourcesForApplication(pack),
            pack,
            iconFallback = true,
          )
          .let { info ->
            // Insert icons
            insertIcon(db, pack, info.iconEntryMap.toList())
            // Insert fallback
            insertFallbackSettings(
              db,
              pack,
              FallbackSettings(info.iconBacks, info.iconUpons, info.iconMasks, info.iconScale),
            )
          }
        // Get installed icon packs
        val packs = runBlocking { IconPackApps.get(context).keys }
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
      } catch (e: Exception) {
        log(e)
      }
      endTransaction()
      log("database: $pack updated")
    }
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
        put("updateAt", System.currentTimeMillis())
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

  private fun insertIcon(
    db: SQLiteDatabase,
    pack: String,
    icons: List<Pair<ComponentName, IconEntry>>,
  ) {
    val insertIcon: SQLiteStatement =
      db.compileStatement("INSERT OR REPLACE INTO '${pt(pack)}' VALUES(?, ?, ?)")
    icons.forEach { icon ->
      insertIcon.apply {
        clearBindings()
        val cn = icon.first
        bindString(1, cn.packageName)
        bindString(2, cn.className)
        bindBlob(3, icon.second.toByteArray())
        execute()
      }
    }
  }

  fun replaceIcon(pack: String, cn: ComponentName, entry: IconEntry) {
    writableDatabase.update(
      "'${pt(pack)}'",
      ContentValues().apply { put("entry", entry.toByteArray()) },
      "packageName=? and className=?",
      arrayOf(cn.packageName, cn.className),
    )
  }

  fun resetPack(pack: String) {
    writableDatabase.update(
      "fallbacks",
      ContentValues().apply { put("updateAt", 0) },
      "pack=?",
      arrayOf(pack),
    )
    update(writableDatabase, pack)
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
