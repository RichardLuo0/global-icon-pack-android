package com.richardluo.globalIconPack.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CompIcon<out Info : CompInfo>(val info: Info, val entry: IconEntryWithPack?) :
  Parcelable

typealias AnyCompIcon = CompIcon<CompInfo>

typealias AppCompIcon = CompIcon<AppCompInfo>

infix fun <Info : CompInfo> Info.to(entry: IconEntryWithPack?) = CompIcon(this, entry)
