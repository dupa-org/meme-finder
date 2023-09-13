package com.dupaorg.memefinder

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ImagesRepository {

    fun all(applicationContext: Context): Flow<Image> {
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA // This is the actual image file path
            )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor =
            applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

        return flow {
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)

                    val imageUri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    emit(
                        Image(
                            id,
                            name,
                            imageUri,
                            InputImage.fromFilePath(applicationContext, imageUri)
                        )
                    )
                }
            }
        }
    }
}
