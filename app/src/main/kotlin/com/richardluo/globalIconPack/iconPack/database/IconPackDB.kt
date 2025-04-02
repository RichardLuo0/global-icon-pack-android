package com.richardluo.globalIconPack.iconPack.database

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.iconPack.loadIconPack
import com.richardluo.globalIconPack.utils.flowTrigger
import com.richardluo.globalIconPack.utils.getFirstRow
import com.richardluo.globalIconPack.utils.getInt
import com.richardluo.globalIconPack.utils.getLong
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.tryEmit
import kotlinx.coroutines.runBlocking

class IconPackDB(private val context: Context, path: String = "iconPack.db") :
  SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 7) {
  val iconsUpdateFlow = flowTrigger()
  val modifiedUpdateFlow = flowTrigger()

  override fun onCreate(db: SQLiteDatabase) {}

  fun onIconPackChange(pack: String) {
    update(writableDatabase, pack)
  }

  private fun update(db: SQLiteDatabase, pack: String) {
    pack.ifEmpty {
      log("No icon pack set")
      return
    }
    db.transaction {
      execSQL(
        "CREATE TABLE IF NOT EXISTS 'iconPack' (pack TEXT PRIMARY KEY NOT NULL, fallback BLOB NOT NULL, updateAt NUMERIC NOT NULL, modified INTEGER NOT NULL DEFAULT FALSE)"
      )
      val packTable = pt(pack)
      // Check update time
      val lastUpdateTime =
        runCatching { context.packageManager.getPackageInfo(pack, 0) }.getOrNull()?.lastUpdateTime
          ?: return
      val modified =
        rawQuery("select DISTINCT updateAt, modified from iconPack where pack=?", arrayOf(pack))
          .getFirstRow {
            if (lastUpdateTime < it.getLong("updateAt")) return
            it.getInt("modified") != 0
          } == true
      // Create tables
      execSQL(
        "CREATE TABLE IF NOT EXISTS '$packTable' (packageName TEXT NOT NULL, className TEXT NOT NULL, entry BLOB NOT NULL, pack TEXT NOT NULL DEFAULT '')"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON '$packTable' (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON '$packTable' (packageName)")
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
      // Delete expired iconPack
      val packSet = packs.joinToString(", ") { "'$it'" }
      delete("iconPack", "pack not in ($packSet)", null)
      // Drop expired tables
      val packTables = packs.map { pt(it) }
      foreachPackTable { if (!packTables.contains(it)) db.execSQL("DROP TABLE '$it'") }
      log("database: $pack updated")
      iconsUpdateFlow.tryEmit()
    }
  }

  fun getFallbackSettings(pack: String) =
    readableDatabase.query(
      "iconPack",
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
      db.query("iconPack", null, "pack=?", arrayOf(pack), null, null, null, "1").use {
        it.count > 0
      }
    )
      db.update(
        "iconPack",
        ContentValues().apply {
          put("fallback", fs.toByteArray())
          put("updateAt", System.currentTimeMillis())
        },
        "pack=?",
        arrayOf(pack),
      )
    else
      db.insert(
        "iconPack",
        null,
        ContentValues().apply {
          put("pack", pack)
          put("fallback", fs.toByteArray())
          put("updateAt", System.currentTimeMillis())
        },
      )
  }

  fun setPackModified(pack: String, modified: Boolean = true) {
    writableDatabase.transaction {
      update(
        "iconPack",
        ContentValues().apply { put("modified", modified) },
        "pack=?",
        arrayOf(pack),
      )
      modifiedUpdateFlow.tryEmit()
    }
  }

  fun isPackModified(pack: String) =
    readableDatabase
      .query("iconPack", arrayOf("modified"), "pack=?", arrayOf(pack), null, null, null, "1")
      .getFirstRow { it.getInt("modified") != 0 } == true

  private fun getIconExact(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry, pack FROM '${pt(pack)}' WHERE packageName=? and className=? LIMIT 1",
      arrayOf(cn.packageName, cn.className),
    )

  private fun getIconFallback(pack: String, cn: ComponentName) =
    readableDatabase.rawQuery(
      "SELECT entry, pack FROM '${pt(pack)}' " +
        "WHERE (packageName=?1 AND className=?2) " +
        "OR (packageName=?1 AND className='') " +
        "ORDER BY className DESC LIMIT 1",
      arrayOf(cn.packageName, cn.className),
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

  private fun getIconUpdateInfo(pack: String, entry: IconEntry, entryPack: String) =
    "'${pt(pack)}'" to
      ContentValues().apply {
        put("entry", entry.toByteArray())
        put("pack", if (entryPack == pack) "" else entryPack)
      }

  fun insertOrUpdateAppIcon(pack: String, cn: ComponentName, entry: IconEntry, entryPack: String) {
    val (packTable, values) = getIconUpdateInfo(pack, entry, entryPack)
    writableDatabase.transaction {
      update(packTable, values, "packageName=?", arrayOf(cn.packageName))
      insertWithOnConflict(
        packTable,
        null,
        values.apply {
          put("packageName", cn.packageName)
          put("className", "")
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      insertWithOnConflict(
        packTable,
        null,
        values.apply { put("className", cn.className) },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun insertOrUpdateShortcutIcon(
    pack: String,
    cn: ComponentName,
    entry: IconEntry,
    entryPack: String,
  ) {
    val (packTable, values) = getIconUpdateInfo(pack, entry, entryPack)
    writableDatabase.transaction {
      insertWithOnConflict(
        packTable,
        null,
        values.apply {
          put("packageName", cn.packageName)
          put("className", cn.className)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun deleteIcon(pack: String, packageName: String) {
    writableDatabase.transaction {
      delete("'${pt(pack)}'", "packageName=?", arrayOf(packageName))
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun resetPack(pack: String) {
    writableDatabase.transaction {
      update(
        "iconPack",
        ContentValues().apply {
          put("updateAt", 0)
          put("modified", false)
        },
        "pack=?",
        arrayOf(pack),
      )
      update(this, pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  inline fun transaction(block: IconPackDB.() -> Unit) = writableDatabase.transaction { block() }

  private fun pt(pack: String) = "pack/$pack"

  private fun SQLiteDatabase.foreachPackTable(block: (String) -> Unit) =
    rawQuery("select DISTINCT tbl_name from sqlite_master WHERE tbl_name LIKE '${pt("%")}'", null)
      .use { while (it.moveToNext()) block(it.getString(0)) }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) =
  try {
    beginTransaction()
    block()
    setTransactionSuccessful()
  } catch (e: Exception) {
    log(e)
    throw e
  } finally {
    endTransaction()
  }
