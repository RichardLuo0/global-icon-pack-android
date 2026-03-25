package com.richardluo.globalIconPack.reflect

import android.os.UserHandle
import android.os.UserManager
import com.richardluo.globalIconPack.utils.call
import com.richardluo.globalIconPack.utils.classOf
import com.richardluo.globalIconPack.utils.field
import com.richardluo.globalIconPack.utils.getAs
import com.richardluo.globalIconPack.utils.method

object UserManager {
  private val getAliveUsersM by lazy { UserManager::class.java.method("getAliveUsers") }

  fun getAliveUsers(thisObj: UserManager): List<Any>? = getAliveUsersM?.call(thisObj)
}

object UserInfo {
  private val clazz by lazy { classOf("android.content.pm.UserInfo") }

  private val idF by lazy { clazz?.field("id") }

  fun getId(thisObj: Any) = idF?.getAs<Int>(thisObj)
}

object UserHandle {
  private val getAppIdM by lazy { UserHandle::class.java.method("getAppId") }

  fun getAppId(uid: Int) = getAppIdM?.call<Int>(null, uid)

  private val getUserIdM by lazy { UserHandle::class.java.method("getUserId") }

  fun getUserId(uid: Int) = getUserIdM?.call<Int>(null, uid)
}
