package com.nekolaska.calabiyau.feature.wiki.gallery.model

data class GalleryImage(
    val fileName: String,
    val caption: String,
    val imageUrl: String
)

data class GallerySection(
    val title: String,
    val images: List<GalleryImage>
)
