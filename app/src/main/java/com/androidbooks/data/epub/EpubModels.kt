package com.androidbooks.data.epub

data class EpubMetadata(
    val title: String,
    val author: String,
    val language: String?,
    val publisher: String?,
    val coverId: String?
)

data class SpineItem(
    val id: String,
    val href: String,
    val mediaType: String
)

data class TocEntry(
    val title: String,
    val href: String,
    val level: Int,
    val children: List<TocEntry> = emptyList()
)

data class EpubBook(
    val metadata: EpubMetadata,
    val spineItems: List<SpineItem>,
    val toc: List<TocEntry>,
    val coverImagePath: String?
)
