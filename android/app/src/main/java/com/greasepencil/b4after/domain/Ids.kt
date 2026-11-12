package com.greasepencil.b4after.domain

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
