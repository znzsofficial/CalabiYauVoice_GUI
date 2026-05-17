package com.nekolaska.calabiyau.feature.wiki.gallery.model

data class GalleryImage(
    val fileName: String,
    val caption: String,
    val imageUrl: String,
    val description: String = "",
    val obtainMethod: String = ""
)

data class GallerySection(
    val title: String,
    val images: List<GalleryImage>
)
