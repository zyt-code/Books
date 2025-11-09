package com.androidbooks.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val progressSpineIndex: Int,
    val progressOffset: Float,
    val lastReadAt: Long,
    val totalSpineItems: Int
)
