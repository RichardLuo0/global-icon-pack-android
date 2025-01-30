package com.richardluo.globalIconPack.iconPack.database

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.richardluo.globalIconPack.PrefDef
import com.richardluo.globalIconPack.PrefKey
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.iconPack.loadIconPack
import com.richardluo.globalIconPack.utils.WorldPreference
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInt
import com.richardluo.globalIconPack.utils.getLong
import com.richardluo.globalIconPack.utils.log
import kotlinx.coroutines.runBlocking

class IconPackDB(private val context: Context, path: String = "iconPack.db") :
  SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 6) {

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
        "CREATE TABLE IF NOT EXISTS 'fallbacks' (pack TEXT PRIMARY KEY NOT NULL, fallback BLOB NOT NULL, updateAt NUMERIC NOT NULL, modified INTEGER NOT NULL DEFAULT FALSE)"
      )
      val packTable = pt(pack)
      // Check update time
      val lastUpdateTime =
        runCatching { context.packageManager.getPackageInfo(pack, 0) }.getOrNull()?.lastUpdateTime
          ?: return
      val modified =
        rawQuery("select DISTINCT updateAt, modified from fallbacks where pack=?", arrayOf(pack))
          .getFirstRow {
            if (lastUpdateTime < it.getLong("updateAt")) return
            it.getInt("modified") != 0
          } ?: return
      // Create tables
      execSQL(
        "CREATE TABLE IF NOT EXISTS '$packTable' (packageName TEXT NOT NULL, className TEXT NOT NULL, entry BLOB NOT NULL, pack TEXT NOT NULL DEFAULT '')"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON '$packTable' (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON '$packTable' (packageName)")
      beginTransaction()
      try {
        // Load icon pack
        loadIconPack(context.packageManager.getResourcesForApplication(pack), pack).let { info ->
          if (!modified) delete("'${pt(pack)}'", null, null)
          // Insert icons
          insertIcons(db, pack, info.iconEntryMap.toList())
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
        val packTables = packs.map { pt(it) }
        foreachPackTable { if (!packTables.contains(it)) db.execSQL("DROP TABLE '$it'") }
        setTransactionSuccessful()
      } catch (e: Exception) {
        log(e)
      }
      endTransaction()
      log("database: $pack updated")
    }
  }

  fun getFallbackSettings(pack: String) =
    readableDatabase.query(
      "fallbacks",
      arrayOf("fallback"),
      "pack=?",
      arrayOf(pack),
      null,
      null,
      null,
      "1",
    )

  private fun insertFallbackSettings(db: SQLiteDatabase, pack: String, fs: FallbackSettings) {
    if (
      db.query("fallbacks", null, "pack=?", arrayOf(pack), null, null, null, "1").use {
        it.count > 0
      }
    )
      db.update(
        "fallbacks",
        ContentValues().apply {
          put("fallback", fs.toByteArray())
          put("updateAt", System.currentTimeMillis())
        },
        "pack=?",
        arrayOf(pack),
      )
    else
      db.insert(
        "fallbacks",
        null,
        ContentValues().apply {
          put("pack", pack)
          put("fallback", fs.toByteArray())
          put("updateAt", System.currentTimeMillis())
        },
      )
  }

  fun setPackModified(pack: String, modified: Boolean = true) {
    writableDatabase.update(
      "fallbacks",
      ContentValues().apply { put("modified", modified) },
      "pack=?",
      arrayOf(pack),
    )
  }

  fun isPackModified(pack: String) =
    readableDatabase
      .query("fallbacks", arrayOf("modified"), "pack=?", arrayOf(pack), null, null, null, "1")
      .getFirstRow { it.getInt("modified") != 0 } ?: false

  private fun getIconExact(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry, pack FROM '${pt(pack)}' WHERE packageName=? and className=? LIMIT 1",
      arrayOf(cn.packageName, cn.className),
    )

  private fun getIconFallback(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry, pack FROM '${pt(pack)}' WHERE packageName=? ORDER BY " +
        "CASE " +
        "  WHEN className = '${cn.className}' THEN 1 " +
        "  WHEN className = '' THEN 2 " +
        "  ELSE 3 " +
        "END LIMIT 1",
      arrayOf(cn.packageName),
    )

  fun getIcon(pack: String, cn: ComponentName, fallback: Boolean = false) =
    if (fallback) getIconFallback(pack, cn) else getIconExact(pack, cn)

  private fun insertIcons(
    db: SQLiteDatabase,
    pack: String,
    icons: List<Pair<ComponentName, IconEntry>>,
  ) {
    val insertIcon =
      db.compileStatement(
        "INSERT OR IGNORE INTO '${pt(pack)}' (packageName, className, entry) VALUES(?, ?, ?)"
      )
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

  fun insertOrUpdatePackageIcon(
    pack: String,
    cn: ComponentName,
    entry: IconEntry,
    entryPack: String,
  ) {
    writableDatabase.apply {
      beginTransaction()
      try {
        val packTable = "'${pt(pack)}'"
        val entryPackInTable = if (entryPack == pack) "" else entryPack
        update(
          packTable,
          ContentValues().apply {
            put("entry", entry.toByteArray())
            put("pack", entryPackInTable)
          },
          "packageName=?",
          arrayOf(cn.packageName),
        )
        insertWithOnConflict(
          packTable,
          null,
          ContentValues().apply {
            put("packageName", cn.packageName)
            put("className", "")
            put("entry", entry.toByteArray())
            put("pack", entryPackInTable)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
        insertWithOnConflict(
          packTable,
          null,
          ContentValues().apply {
            put("packageName", cn.packageName)
            put("className", cn.className)
            put("entry", entry.toByteArray())
            put("pack", entryPackInTable)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
        setPackModified(pack)
        setTransactionSuccessful()
      } catch (e: Exception) {
        log(e)
      }
      endTransaction()
    }
  }

  fun deleteIcon(pack: String, packageName: String) {
    writableDatabase.delete("'${pt(pack)}'", "packageName=?", arrayOf(packageName))
    setPackModified(pack)
  }

  fun resetPack(pack: String) {
    writableDatabase.update(
      "fallbacks",
      ContentValues().apply {
        put("updateAt", 0)
        put("modified", false)
      },
      "pack=?",
      arrayOf(pack),
    )
    update(writableDatabase, pack)
  }

  private fun pt(pack: String) = "pack_$pack"

  private fun SQLiteDatabase.foreachPackTable(block: (String) -> Unit) =
    rawQuery("select DISTINCT tbl_name from sqlite_master WHERE tbl_name LIKE '${pt("%")}'", null)
      .use { while (it.moveToNext()) block(it.getString(0)) }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.apply {
      if (oldVersion >= 4) {
        if (oldVersion == 4)
          foreachPackTable { execSQL("ALTER TABLE '$it' ADD COLUMN pack TEXT NOT NULL DEFAULT ''") }
        execSQL("ALTER TABLE 'fallbacks' ADD COLUMN modified INTEGER NOT NULL DEFAULT FALSE")
      } else {
        foreachPackTable {
          execSQL("DROP TABLE '$it'")
          execSQL("DROP TABLE 'fallbacks'")
        }
        WorldPreference.getPrefInApp(context).getString(PrefKey.ICON_PACK, PrefDef.ICON_PACK)?.let {
          update(db, it)
        }
      }
    }
  }
}
