package com.richardluo.globalIconPack.iconPack

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.ui.util.fastJoinToString
import com.richardluo.globalIconPack.AppPref
import com.richardluo.globalIconPack.get
import com.richardluo.globalIconPack.iconPack.model.FallbackSettings
import com.richardluo.globalIconPack.iconPack.model.IconEntry
import com.richardluo.globalIconPack.iconPack.model.IconEntry.Type
import com.richardluo.globalIconPack.ui.model.IconPack
import com.richardluo.globalIconPack.ui.viewModel.IconPackCache
import com.richardluo.globalIconPack.utils.AppPreference
import com.richardluo.globalIconPack.utils.SQLiteOpenHelper
import com.richardluo.globalIconPack.utils.SingletonManager
import com.richardluo.globalIconPack.utils.flowTrigger
import com.richardluo.globalIconPack.utils.log
import com.richardluo.globalIconPack.utils.tryEmit
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class IconPackDB(
  private val context: Application,
  path: String = AppPreference.get().get(AppPref.PATH),
) : SQLiteOpenHelper(context.createDeviceProtectedStorageContext(), path, null, 8) {
  val iconsUpdateFlow = flowTrigger()
  val modifiedUpdateFlow = flowTrigger()

  init {
    if (!usable()) throw Exception("DB file can not be read and write: $path")
  }

  override fun onCreate(db: SQLiteDatabase) {}

  fun onIconPackChange(iconPack: IconPack, installedPacks: Set<String>) {
    update(iconPack, installedPacks)
  }

  private fun update(iconPack: IconPack, installedPacks: Set<String>) {
    val pack = iconPack.pack
    writableDatabase.transaction {
      execSQL(
        "CREATE TABLE IF NOT EXISTS 'iconPack' (pack TEXT PRIMARY KEY NOT NULL, fallback BLOB NOT NULL, updateAt NUMERIC NOT NULL, modified INTEGER NOT NULL DEFAULT FALSE)"
      )
      val packTable = pt(pack)
      // Check update time
      val lastUpdateTime =
        runCatching { context.packageManager.getPackageInfo(pack, 0) }.getOrNull()?.lastUpdateTime
          ?: return@transaction
      val modified =
        rawQuery("select DISTINCT updateAt, modified from iconPack where pack=?", arrayOf(pack))
          .useFirstRow {
            if (lastUpdateTime < it.getLong(0)) return@transaction
            it.getInt(1) != 0
          } == true
      // Create tables
      execSQL(
        "CREATE TABLE IF NOT EXISTS $packTable (packageName TEXT NOT NULL, className TEXT NOT NULL, entry BLOB NOT NULL, pack TEXT NOT NULL DEFAULT '', id INTEGER NOT NULL DEFAULT 0)"
      )
      execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS '${pack}_componentName' ON $packTable (packageName, className)"
      )
      execSQL("CREATE INDEX IF NOT EXISTS '${pack}_packageName' ON $packTable (packageName)")
      // If not modified, delete everything
      if (!modified) delete(pt(pack), null, null)
      // Insert icons
      insertIcons(this, pack, iconPack.iconEntryMap.asIterable())
      // Update id
      updateIconId(this, iconPack)
      // Insert fallback
      insertFallbackSettings(this, pack, FallbackSettings(iconPack.info))
      // Delete expired iconPack
      val packSet = installedPacks.joinToString(", ") { "'$it'" }
      delete("iconPack", "pack not in ($packSet)", null)
      // Drop expired tables
      val packTables = installedPacks.map { pt(it) }
      foreachPackTable { if (!packTables.contains("'$it'")) execSQL("DROP TABLE '$it'") }
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

  enum class GetIconCol {
    Index,
    Entry,
    Pack,
    Id,
    Fallback,
  }

  private fun getIconExact(pack: String, cnList: List<ComponentName>) =
    readableDatabase.rawQueryList(
      "SELECT entry, pack, id FROM ${pt(pack)} WHERE packageName=? AND className=? LIMIT 1",
      cnList,
    ) {
      add(it.packageName)
      add(it.className)
    }

  private fun getIconFallback(pack: String, cnList: List<ComponentName>) =
    readableDatabase.rawQueryList(
      "SELECT * FROM ( " +
        "SELECT entry, pack, id, 0 AS fallback FROM ${pt(pack)} WHERE packageName=? AND className=? " +
        "UNION ALL " +
        "SELECT entry, pack, id, 1 AS fallback FROM ${pt(pack)} WHERE packageName=? AND className='' " +
        ") ORDER BY fallback LIMIT 1",
      cnList,
    ) {
      add(it.packageName)
      add(it.className)
      add(it.packageName)
    }

  fun getIcon(pack: String, cnList: List<ComponentName>, fallback: Boolean = false) =
    if (fallback) getIconFallback(pack, cnList) else getIconExact(pack, cnList)

  enum class GetAllIconsCol {
    Index,
    PackageName,
    ClassName,
    Entry,
    Pack,
    Id,
  }

  fun getAllIcons(pack: String, packageNameList: List<String>) =
    readableDatabase.rawQueryList(
      "SELECT packageName, className, entry, pack, id FROM ${pt(pack)} WHERE packageName=?",
      packageNameList,
    ) {
      add(it)
    }

  private fun insertIcons(
    db: SQLiteDatabase,
    pack: String,
    icons: Iterable<Map.Entry<ComponentName, IconEntry>>,
  ) {
    val insertIcon =
      db.compileStatement(
        "INSERT OR IGNORE INTO ${pt(pack)} (packageName, className, entry) VALUES(?, ?, ?)"
      )
    icons.forEach { icon ->
      insertIcon.apply {
        val cn = icon.key
        clearBindings()
        bindString(1, cn.packageName)
        bindString(2, cn.className)
        bindBlob(3, icon.value.toByteArray())
        execute()
      }
    }
  }

  private fun getId(type: Type, name: String, iconPack: IconPack): Int? {
    return iconPack
      .getDrawableId(
        when (type) {
          Type.Normal,
          Type.Clock -> name
          else -> return 0
        }
      )
      .takeIf { it != 0 }
  }

  private fun updateIconId(db: SQLiteDatabase, iconPack: IconPack, packageName: String? = null) {
    val iconPackCache = SingletonManager.get { IconPackCache(context) }.value
    val packTable = pt(iconPack.pack)
    val updateId = db.compileStatement("UPDATE $packTable SET id=? WHERE ROWID=?")
    db
      .query(
        packTable,
        arrayOf("ROWID", "entry", "pack"),
        packageName?.let { "packageName=?" },
        packageName?.let { arrayOf(it) },
        null,
        null,
        null,
      )
      .useEachRow { c ->
        val rowId = c.getInt(0)
        val entry = c.getBlob(1)
        val pack = c.getString(2)
        DataInputStream(ByteArrayInputStream(entry)).use {
          val id =
            getId(
              Type.entries[it.readByte().toInt()],
              it.readUTF(),
              if (pack.isNotEmpty()) iconPackCache[pack] else iconPack,
            ) ?: return@use
          updateId
            .apply {
              clearBindings()
              bindLong(1, id.toLong())
              bindLong(2, rowId.toLong())
            }
            .execute()
        }
      }
  }

  fun insertOrUpdateIcon(
    pack: String,
    cn: ComponentName,
    entry: IconEntry,
    entryIconPack: IconPack,
  ) {
    val entryPack = entryIconPack.pack
    val packTable = pt(pack)
    writableDatabase.transaction {
      insertWithOnConflict(
        packTable,
        null,
        ContentValues().apply {
          put("packageName", cn.packageName)
          put("className", cn.className)
          put("entry", entry.toByteArray())
          put("pack", if (entryPack == pack) "" else entryPack)
          put("id", getId(entry.type, entry.name, entryIconPack) ?: return@transaction)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun deleteIcon(pack: String, cn: ComponentName) {
    writableDatabase.transaction {
      delete(pt(pack), "packageName=? AND className=?", arrayOf(cn.packageName, cn.className))
      setPackModified(pack)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun restorePack(iconPack: IconPack) {
    writableDatabase.transaction {
      val pack = iconPack.pack
      delete(pt(pack), null, null)
      insertIcons(this, pack, iconPack.iconEntryMap.asIterable())
      updateIconId(this, iconPack)
      setPackModified(pack, false)
      iconsUpdateFlow.tryEmit()
      modifiedUpdateFlow.tryEmit()
    }
  }

  fun restorePackForPackage(iconPack: IconPack, packageName: String) {
    writableDatabase.transaction {
      val pack = iconPack.pack
      delete(pt(pack), "packageName=?", arrayOf(packageName))
      insertIcons(
        this,
        pack,
        iconPack.iconEntryMap.filter { it.key.packageName == packageName }.asIterable(),
      )
      updateIconId(this, iconPack, packageName)
      iconsUpdateFlow.tryEmit()
    }
  }

  fun clearPackage(iconPack: IconPack, packageName: String) {
    writableDatabase.transaction {
      delete(pt(iconPack.pack), "packageName=?", arrayOf(packageName))
      iconsUpdateFlow.tryEmit()
    }
  }

  inline fun transaction(crossinline block: IconPackDB.() -> Unit) =
    writableDatabase.transaction { block() }

  private fun pt(pack: String) = "'pack/$pack'"

  private fun SQLiteDatabase.foreachPackTable(block: (String) -> Unit) =
    rawQuery("select DISTINCT tbl_name from sqlite_master WHERE tbl_name LIKE ${pt("%")}", null)
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
    } else if (oldVersion < 8) {
      val iconPackCache = SingletonManager.get { IconPackCache(context) }.value
      db.foreachPackTable {
        db.execSQL("ALTER TABLE '$it' ADD COLUMN id INTEGER NOT NULL DEFAULT 0")
        updateIconId(db, iconPackCache[it.removePrefix("pack/")])
      }
    }
  }
}

inline fun SQLiteDatabase.transaction(crossinline block: SQLiteDatabase.() -> Unit) =
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
  if (indexColumn.isEmpty()) throw Exception("Empty index column name!")
  if (argList.isEmpty()) return MatrixCursor(arrayOf())
  var i = 0
  val cursors =
    argList.chunked(500).map {
      val sql =
        it.fastJoinToString(" UNION ALL ") {
          "SELECT ${i++} AS '$indexColumn', * FROM ($singleSql)"
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

inline fun <reified T> Cursor.useMapToArray(
  size: Int,
  indexColumn: Int = 0,
  block: (Int, Cursor) -> T,
) =
  arrayOfNulls<T?>(size).apply {
    useEachRow {
      val id = it.getInt(indexColumn)
      set(id, block(id, it))
    }
  }

inline fun <reified T> Cursor.useMapToArray(
  size: Int,
  indexEnum: Enum<*>,
  block: (Int, Cursor) -> T,
) = useMapToArray(size, indexEnum.ordinal, block)
