package com.richardluo.globalIconPack.iconPack.database

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.ui.util.fastJoinToString
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.IconPackApps
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.SQLiteOpenHelper
import com.richardluo.globalIconPack.utils.flowTrigger
import com.richardluo.globalIconPack.utils.ifNotEmpty
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.tryEmit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// To bypass the permission reset
class DBContext(context: Context) : ContextWrapper(context) {
  override fun getDatabasePath(name: String): File? =
    if (name.getOrNull(0) == File.separatorChar) File(name) else super.getDatabasePath(name)
}

class IconPackDB(
  private val context: Context,
  path: String = AppPreference.get(context).get(AppPref.PATH),
) : SQLiteOpenHelper(DBContext(context.createDeviceProtectedStorageContext()), path, null, 7) {
  val iconsUpdateFlow = flowTrigger()
  val modifiedUpdateFlow = flowTrigger()

  override fun onCreate(db: SQLiteDatabase) {}

  fun onIconPackChange(iconPack: IconPack) {
    update(writableDatabase, iconPack)
  }

  private fun update(db: SQLiteDatabase, iconPack: IconPack) {
    val pack = iconPack.pack
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
          .useFirstRow {
            if (lastUpdateTime < it.getLong(0)) return
            it.getInt(1) != 0
          } == true
      // Create tables
      execSQL(
        "CREATE TABLE IF NOT EXISTS '$packTable' (packageName TEXT NOT NULL, className TEXT NOT NULL, entry BLOB NOT NULL, pack TEXT NOT NULL DEFAULT '')"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON '$packTable' (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON '$packTable' (packageName)")
      // If not modified, delete everything
      if (!modified) delete("'${pt(pack)}'", null, null)
      // Insert icons
      insertIcons(db, pack, iconPack.iconEntryMap.toList())
      // Insert fallback
      insertFallbackSettings(db, pack, FallbackSettings(iconPack.info))
      // Get installed icon packs
      val packs = runBlocking { IconPackApps.get(context).keys }
      // Delete expired iconPack
      val packSet = packs.joinToString(", ") { "'$it'" }
      delete("iconPack", "pack not in ($packSet)", null)
      // Drop expired tables
      val packTables = packs.map { pt(it) }
      foreachPackTable { if (!packTables.contains(it)) db.execSQL("DROP TABLE '$it'") }
      log("Database: $pack updated")
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
      .useFirstRow { it.getInt(0) != 0 } == true

  enum class GetIconColumn {
    Index,
    Entry,
    Pack,
    Fallback,
  }

  private fun getIconExact(pack: String, cnList: List<ComponentName>) =
    readableDatabase.rawQueryList(
      "SELECT entry, pack FROM '${pt(pack)}' WHERE packageName=? AND className=? LIMIT 1",
      cnList,
    ) {
      add(it.packageName)
      add(it.className)
    }

  private fun getIconFallback(pack: String, cnList: List<ComponentName>) =
    readableDatabase.rawQueryList(
      "SELECT * FROM ( " +
        "SELECT entry, pack, 0 AS fallback FROM '${pt(pack)}' WHERE packageName=? AND className=? " +
        "UNION ALL " +
        "SELECT entry, pack, 1 AS fallback FROM '${pt(pack)}' WHERE packageName=? AND className='' " +
        ") ORDER BY fallback LIMIT 1",
      cnList,
    ) {
      add(it.packageName)
      add(it.className)
      add(it.packageName)
    }

  fun getIcon(pack: String, cnList: List<ComponentName>, fallback: Boolean = false) =
    if (fallback) getIconFallback(pack, cnList) else getIconExact(pack, cnList)

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

  fun insertOrUpdateIcon(pack: String, cn: ComponentName, entry: IconEntry, entryPack: String) {
    val packTable = "'${pt(pack)}'"
    val values =
      ContentValues().apply {
        put("entry", entry.toByteArray())
        put("pack", if (entryPack == pack) "" else entryPack)
      }
    writableDatabase.transaction {
      if (!cn.packageName.endsWith("@")) {
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
      }
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

  fun deleteIcon(pack: String, cn: ComponentName) {
    writableDatabase.transaction {
      if (!cn.packageName.endsWith("@"))
        delete("'${pt(pack)}'", "packageName=?", arrayOf(cn.packageName))
      else
        delete(
          "'${pt(pack)}'",
          "packageName=? AND className=?",
          arrayOf(cn.packageName, cn.className),
        )
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun resetPack(iconPack: IconPack) {
    writableDatabase.transaction {
      update(
        "iconPack",
        ContentValues().apply {
          put("updateAt", 0)
          put("modified", false)
        },
        "pack=?",
        arrayOf(iconPack.pack),
      )
      update(this, iconPack)
      iconsUpdateFlow.tryEmit()
      modifiedUpdateFlow.tryEmit()
    }
  }

  inline fun transaction(block: IconPackDB.() -> Unit) = writableDatabase.transaction { block() }

  private fun pt(pack: String) = "pack/$pack"

  private fun SQLiteDatabase.foreachPackTable(block: (String) -> Unit) =
    rawQuery("select DISTINCT tbl_name from sqlite_master WHERE tbl_name LIKE '${pt("%")}'", null)
      .use { while (it.moveToNext()) block(it.getString(0)) }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 7) {
      runBlocking {
        withContext(Dispatchers.Main) {
          Toast.makeText(
              context,
              "Please clear data, the db version is not compatible.",
              Toast.LENGTH_LONG,
            )
            .show()
        }
      }
      throw Exception("Old version < 7!")
    }
  }
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

private fun <T> SQLiteDatabase.rawQueryList(
  singleSql: String,
  argList: List<T>,
  indexColumn: String = "index",
  expandArg: MutableList<String>.(T) -> Unit,
): Cursor {
  if (argList.isEmpty()) return MatrixCursor(arrayOf())
  var i = 0
  val cursors =
    argList.chunked(500).map {
      val sql =
        it.fastJoinToString(" UNION ALL ") {
          "SELECT${indexColumn.ifNotEmpty { " ${i++} AS '$it'," }} * FROM ($singleSql)"
        }
      rawQuery(sql, buildList { for (item in it) expandArg(item) }.toTypedArray())
    }
  return MergeCursor(cursors.toTypedArray())
}

inline fun <reified T : Enum<T>> getAsColumnArray() =
  T::class.java.enumConstants!!.let { entries -> Array(entries.size) { entries[it].name } }

fun Cursor.getBlob(enum: Enum<*>): ByteArray = getBlob(enum.ordinal)

fun Cursor.getLong(enum: Enum<*>): Long = getLong(enum.ordinal)

fun Cursor.getString(enum: Enum<*>): String = getString(enum.ordinal)

fun Cursor.getInt(enum: Enum<*>): Int = getInt(enum.ordinal)

inline fun <T> Cursor.useFirstRow(block: (Cursor) -> T) = takeIf { it.moveToFirst() }?.use(block)

inline fun Cursor.useEachRow(block: (Cursor) -> Unit) = use {
  if (moveToFirst())
    do {
      block(this)
    } while (moveToNext())
}

inline fun <T> Cursor.useMap(block: (Cursor) -> T): List<T> = use {
  buildList {
    if (moveToFirst()) {
      do {
        add(block(this@useMap))
      } while (moveToNext())
    }
  }
}

inline fun <reified T> Cursor.useMapToArray(size: Int, indexColumn: Int = 0, block: (Cursor) -> T) =
  arrayOfNulls<T?>(size).apply { useEachRow { set(it.getInt(indexColumn), block(it)) } }

inline fun <reified T> Cursor.useMapToArray(size: Int, indexEnum: Enum<*>, block: (Cursor) -> T) =
  useMapToArray(size, indexEnum.ordinal, block)
