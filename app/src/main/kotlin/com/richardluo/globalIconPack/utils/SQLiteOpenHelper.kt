package com.richardluo.globalIconPack.utils

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteDatabase.OpenParams
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import java.io.File
import java.lang.AutoCloseable
import java.util.Objects
import kotlin.Boolean
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.String
import kotlin.check
import kotlin.math.max
import kotlin.require
import kotlin.synchronized

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* A fork of SQLiteOpenHelper to bypass permission issue. Rewrite in kotlin. 2025/4/14 */

/**
 * A helper class to manage database creation and version management.
 *
 * You create a subclass implementing [.onCreate], [.onUpgrade] and optionally [.onOpen], and this
 * class takes care of opening the database if it exists, creating it if it does not, and upgrading
 * it as necessary. Transactions are used to make sure the database is always in a sensible state.
 *
 * This class makes it easy for [android.content.ContentProvider] implementations to defer opening
 * and upgrading the database until first use, to avoid blocking application startup with
 * long-running database upgrades.
 *
 * For an example, see the NotePadProvider class in the NotePad sample application, in the
 * *samples/ * directory of the SDK.
 *
 * **Note:** this class assumes monotonically increasing version numbers for upgrades.
 *
 * **Note:** the [AutoCloseable] interface was first added in the [android.os.Build.VERSION_CODES.Q]
 * release.
 */
abstract class SQLiteOpenHelper
private constructor(
  private val context: Context,
  name: String?,
  version: Int,
  minimumSupportedVersion: Int,
  private var mOpenParamsBuilder: OpenParams.Builder,
) : AutoCloseable {
  /** Return the name of the SQLite database being opened, as given to the constructor. */
  val databaseName: String?
  private val mNewVersion: Int
  private val mMinimumSupportedVersion: Int

  private var mDatabase: SQLiteDatabase? = null
  private var mIsInitializing = false

  /**
   * Create a helper object to create, open, and/or manage a database. The database is not actually
   * created or opened until one of [.getWritableDatabase] or [.getReadableDatabase] is called.
   *
   * Accepts input param: a concrete instance of [DatabaseErrorHandler] to be used to handle
   * corruption when sqlite reports database corruption.
   *
   * @param context to use for locating paths to the the database
   * @param name of the database file, or null for an in-memory database
   * @param factory to use for creating cursor objects, or null for the default
   * @param version number of the database (starting at 1); if the database is older, [.onUpgrade]
   *   will be used to upgrade the database; if the database is newer, [.onDowngrade] will be used
   *   to downgrade the database
   * @param errorHandler the [DatabaseErrorHandler] to be used when sqlite reports database
   *   corruption, or null to use the default error handler.
   */
  @JvmOverloads
  constructor(
    context: Context,
    name: String?,
    factory: CursorFactory?,
    version: Int,
    errorHandler: DatabaseErrorHandler? = null,
  ) : this(context, name, factory, version, 0, errorHandler)

  /**
   * Same as [.SQLiteOpenHelper] but also accepts an integer minimumSupportedVersion as a
   * convenience for upgrading very old versions of this database that are no longer supported. If a
   * database with older version that minimumSupportedVersion is found, it is simply deleted and a
   * new database is created with the given name and version
   *
   * @param context to use for locating paths to the the database
   * @param name the name of the database file, null for a temporary in-memory database
   * @param factory to use for creating cursor objects, null for default
   * @param version the required version of the database
   * @param minimumSupportedVersion the minimum version that is supported to be upgraded to
   *   `version` via [.onUpgrade]. If the current database version is lower than this, database is
   *   simply deleted and recreated with the version passed in `version`. [.onBeforeDelete] is
   *   called before deleting the database when this happens. This is 0 by default.
   * @param errorHandler the [DatabaseErrorHandler] to be used when sqlite reports database
   *   corruption, or null to use the default error handler.
   * @see .onBeforeDelete
   * @see .SQLiteOpenHelper
   * @see .onUpgrade
   * @hide
   */
  constructor(
    context: Context,
    name: String?,
    factory: CursorFactory?,
    version: Int,
    minimumSupportedVersion: Int,
    errorHandler: DatabaseErrorHandler?,
  ) : this(
    context,
    name,
    version,
    minimumSupportedVersion,
    OpenParams.Builder().setCursorFactory(factory).setErrorHandler(errorHandler),
  )

  init {
    Objects.requireNonNull<OpenParams.Builder?>(mOpenParamsBuilder)
    require(version >= 1) { "Version must be >= 1, was $version" }
    this.databaseName = name
    mNewVersion = version
    mMinimumSupportedVersion = max(0.0, minimumSupportedVersion.toDouble()).toInt()
    mOpenParamsBuilder.addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
  }

  fun usable() = context.getDatabasePath(databaseName).let { it.canRead() && it.canWrite() }

  /**
   * Configures [lookaside memory allocator](https://sqlite.org/malloc.html#lookaside)
   *
   * This method should be called from the constructor of the subclass, before opening the database,
   * since lookaside memory configuration can only be changed when no connection is using it
   *
   * SQLite default settings will be used, if this method isn't called. Use
   * `setLookasideConfig(0,0)` to disable lookaside
   *
   * **Note:** Provided slotSize/slotCount configuration is just a recommendation. The system may
   * choose different values depending on a device, e.g. lookaside allocations can be disabled on
   * low-RAM devices
   *
   * @param slotSize The size in bytes of each lookaside slot.
   * @param slotCount The total number of lookaside memory slots per database connection.
   */
  fun setLookasideConfig(slotSize: Int, slotCount: Int) {
    synchronized(this) {
      check(!(mDatabase != null && mDatabase!!.isOpen)) {
        "Lookaside memory config cannot be changed after opening the database"
      }
      mOpenParamsBuilder.setLookasideConfig(slotSize, slotCount)
    }
  }

  /**
   * Sets configuration parameters that are used for opening [SQLiteDatabase].
   *
   * Please note that [SQLiteDatabase.CREATE_IF_NECESSARY] flag will always be set when opening the
   * database
   *
   * @param openParams configuration parameters that are used for opening [SQLiteDatabase].
   * @throws IllegalStateException if the database is already open
   */
  fun setOpenParams(openParams: OpenParams) {
    Objects.requireNonNull(openParams)
    synchronized(this) {
      check(!(mDatabase != null && mDatabase!!.isOpen)) {
        "OpenParams cannot be set after opening the database"
      }
      mOpenParamsBuilder =
        OpenParams.Builder(openParams).addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
    }
  }

  val writableDatabase: SQLiteDatabase
    /**
     * Create and/or open a database that will be used for reading and writing. The first time this
     * is called, the database will be opened and [.onCreate], [.onUpgrade] and/or [.onOpen] will be
     * called.
     *
     * Once opened successfully, the database is cached, so you can call this method every time you
     * need to write to the database. (Make sure to call [.close] when you no longer need the
     * database.) Errors such as bad permissions or a full disk may cause this method to fail, but
     * future attempts may succeed if the problem is fixed.
     *
     * Database upgrade may take a long time, you should not call this method from the application
     * main thread, including from
     * [ContentProvider.onCreate()][android.content.ContentProvider.onCreate].
     *
     * @return a read/write database object valid until [.close] is called
     * @throws SQLiteException if the database cannot be opened for writing
     */
    get() {
      synchronized(this) {
        return getDatabaseLocked(true)
      }
    }

  val readableDatabase: SQLiteDatabase
    /**
     * Create and/or open a database. This will be the same object returned by
     * [.getWritableDatabase] unless some problem, such as a full disk, requires the database to be
     * opened read-only. In that case, a read-only database object will be returned. If the problem
     * is fixed, a future call to [.getWritableDatabase] may succeed, in which case the read-only
     * database object will be closed and the read/write object will be returned in the future.
     *
     * Like [.getWritableDatabase], this method may take a long time to return, so you should not
     * call it from the application main thread, including from
     * [ContentProvider.onCreate()][android.content.ContentProvider.onCreate].
     *
     * @return a database object valid until [.getWritableDatabase] or [.close] is called.
     * @throws SQLiteException if the database cannot be opened
     */
    get() {
      synchronized(this) {
        return getDatabaseLocked(false)
      }
    }

  private fun getDatabaseLocked(writable: Boolean): SQLiteDatabase {
    if (mDatabase != null) {
      if (!mDatabase!!.isOpen) {
        // Darn!  The user closed the database by calling mDatabase.close().
        mDatabase = null
      } else if (!writable || !mDatabase!!.isReadOnly) {
        // The database is already open for business.
        return mDatabase!!
      }
    }

    check(!mIsInitializing) { "getDatabase called recursively" }

    var db = mDatabase
    try {
      mIsInitializing = true

      if (db != null) {
        if (writable && db.isReadOnly) {
          db.close()
          db = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE)
        }
      } else if (databaseName == null) {
        db = SQLiteDatabase.createInMemory(mOpenParamsBuilder.build())
      } else {
        val filePath = context.getDatabasePath(databaseName)
        val params = mOpenParamsBuilder.build()
        try {
          db = SQLiteDatabase.openDatabase(filePath, params)
        } catch (ex: SQLException) {
          if (writable) {
            throw ex
          }
          Log.e(TAG, "Couldn't open database for writing (will try read-only):", ex)
          db =
            SQLiteDatabase.openDatabase(
              filePath,
              OpenParams.Builder(params).addOpenFlags(SQLiteDatabase.OPEN_READONLY).build(),
            )
        }
      }

      onConfigure(db)

      val version = db.version
      if (version != mNewVersion) {
        if (db.isReadOnly) {
          throw SQLiteException(
            "Can't upgrade read-only database from version " +
              db.version +
              " to " +
              mNewVersion +
              ": " +
              this.databaseName
          )
        }

        if (version > 0 && version < mMinimumSupportedVersion) {
          val databaseFile = File(db.path)
          onBeforeDelete(db)
          db.close()
          if (SQLiteDatabase.deleteDatabase(databaseFile)) {
            mIsInitializing = false
            return getDatabaseLocked(writable)
          } else {
            throw IllegalStateException(
              ("Unable to delete obsolete database " +
                this.databaseName +
                " with version " +
                version)
            )
          }
        } else {
          db.transaction {
            try {
              if (version == 0) {
                onCreate(this)
              } else {
                if (version > mNewVersion) {
                  onDowngrade(this, version, mNewVersion)
                } else {
                  onUpgrade(this, version, mNewVersion)
                }
              }
              setVersion(mNewVersion)
            } finally {}
          }
        }
      }

      onOpen(db)
      mDatabase = db
      return db
    } finally {
      mIsInitializing = false
      if (db != null && db != mDatabase) {
        db.close()
      }
    }
  }

  /** Close any open database object. */
  @Synchronized
  override fun close() {
    check(!mIsInitializing) { "Closed during initialization" }

    if (mDatabase != null && mDatabase!!.isOpen) {
      mDatabase!!.close()
      mDatabase = null
    }
  }

  /**
   * Called when the database connection is being configured, to enable features such as write-ahead
   * logging or foreign key support.
   *
   * This method is called before [.onCreate], [.onUpgrade], [.onDowngrade], or [.onOpen] are
   * called. It should not modify the database except to configure the database connection as
   * required.
   *
   * This method should only call methods that configure the parameters of the database connection,
   * such as [SQLiteDatabase.enableWriteAheadLogging]
   * [SQLiteDatabase.setForeignKeyConstraintsEnabled], [SQLiteDatabase.setLocale],
   * [SQLiteDatabase.setMaximumSize], or executing PRAGMA statements.
   *
   * @param db The database.
   */
  open fun onConfigure(db: SQLiteDatabase) {}

  /**
   * Called before the database is deleted when the version returned by [SQLiteDatabase.getVersion]
   * is lower than the minimum supported version passed (if at all) while creating this helper.
   * After the database is deleted, a fresh database with the given version is created. This will be
   * followed by [.onConfigure] and [.onCreate] being called with a new SQLiteDatabase object
   *
   * @param db the database opened with this helper
   * @see .SQLiteOpenHelper
   * @hide
   */
  open fun onBeforeDelete(db: SQLiteDatabase) {}

  /**
   * Called when the database is created for the first time. This is where the creation of tables
   * and the initial population of the tables should happen.
   *
   * @param db The database.
   */
  abstract fun onCreate(db: SQLiteDatabase)

  /**
   * Called when the database needs to be upgraded. The implementation should use this method to
   * drop tables, add tables, or do anything else it needs to upgrade to the new schema version.
   *
   * The SQLite ALTER TABLE documentation can be found
   * [here](http://sqlite.org/lang_altertable.html). If you add new columns you can use ALTER TABLE
   * to insert them into a live table. If you rename or remove columns you can use ALTER TABLE to
   * rename the old table, then create the new table and then populate the new table with the
   * contents of the old table.
   *
   * This method executes within a transaction. If an exception is thrown, all changes will
   * automatically be rolled back.
   *
   * *Important:* You should NOT modify an existing migration step from version X to X+1 once a
   * build has been released containing that migration step. If a migration step has an error and it
   * runs on a device, the step will NOT re-run itself in the future if a fix is made to the
   * migration step.
   *
   * For example, suppose a migration step renames a database column from `foo` to `bar` when the
   * name should have been `baz`. If that migration step is released in a build and runs on a user's
   * device, the column will be renamed to `bar`. If the developer subsequently edits this same
   * migration step to change the name to `baz` as intended, the user devices which have already run
   * this step will still have the name `bar`. Instead, a NEW migration step should be created to
   * correct the error and rename `bar` to `baz`, ensuring the error is corrected on devices which
   * have already run the migration step with the error.
   *
   * @param db The database.
   * @param oldVersion The old database version.
   * @param newVersion The new database version.
   */
  abstract fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)

  /**
   * Called when the database needs to be downgraded. This is strictly similar to [.onUpgrade]
   * method, but is called whenever current version is newer than requested one. However, this
   * method is not abstract, so it is not mandatory for a customer to implement it. If not
   * overridden, default implementation will reject downgrade and throws SQLiteException
   *
   * This method executes within a transaction. If an exception is thrown, all changes will
   * automatically be rolled back.
   *
   * @param db The database.
   * @param oldVersion The old database version.
   * @param newVersion The new database version.
   */
  open fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    throw SQLiteException("Can't downgrade database from version $oldVersion to $newVersion")
  }

  /**
   * Called when the database has been opened. The implementation should check
   * [SQLiteDatabase.isReadOnly] before updating the database.
   *
   * This method is called after the database connection has been configured and after the database
   * schema has been created, upgraded or downgraded as necessary. If the database connection must
   * be configured in some way before the schema is created, upgraded, or downgraded, do it in
   * [.onConfigure] instead.
   *
   * @param db The database.
   */
  open fun onOpen(db: SQLiteDatabase) {}

  companion object {
    private val TAG: String = SQLiteOpenHelper::class.java.getSimpleName()
  }
}
