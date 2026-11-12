package com.greasepencil.pairshot.domain

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
