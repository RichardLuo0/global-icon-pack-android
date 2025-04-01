package com.richardluo.globalIconPack.ui.model

import com.richardluo.globalIconPack.iconPack.database.IconEntry

interface VariantIcon

class VariantPackIcon(val pack: IconPack, val entry: IconEntry) : VariantIcon

class OriginalIcon : VariantIcon
