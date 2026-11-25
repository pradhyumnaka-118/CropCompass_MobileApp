package com.cropcompass.app.data

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PhotoStore {
    suspend fun delete(context: Context, uri: String?) = withContext(Dispatchers.IO) {
        if(uri==null)return@withContext
        val parsed=Uri.parse(uri)
        if(parsed.scheme!="file" || parsed.path==null)return@withContext
        val file=File(parsed.path!!).canonicalFile
        val root=File(context.filesDir,"report_photos").canonicalFile
        if(file.path.startsWith(root.path+File.separator))file.delete()
    }
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val temp=File.createTempFile("report-import-",".image",context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected photo could not be opened." }
                temp.outputStream().use { output ->
                    val buffer=ByteArray(8192);var total=0
                    while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=20*1024*1024){"Choose a photo under 20 MB."};output.write(buffer,0,n)}
                }
            }
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(temp.path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0){"Choose a valid image."}
            var sample=1;while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600)sample*=2
            val bitmap=requireNotNull(BitmapFactory.decodeFile(temp.path,BitmapFactory.Options().apply{inSampleSize=sample}))
            val orientation=runCatching{ExifInterface(temp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(1)
            val matrix=Matrix().apply { when(orientation){3->postRotate(180f);6->postRotate(90f);8->postRotate(270f);2->postScale(-1f,1f);4->postScale(1f,-1f);5->{postRotate(90f);postScale(-1f,1f)};7->{postRotate(270f);postScale(-1f,1f)}} }
            val upright=if(matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            val directory=File(context.filesDir,"report_photos").apply{mkdirs()};val file=File(directory,"${UUID.randomUUID()}.jpg")
            file.outputStream().use{upright.compress(Bitmap.CompressFormat.JPEG,85,it)}
            if(upright!==bitmap)upright.recycle();bitmap.recycle()
            require(file.length()<5*1024*1024){"The compressed photo exceeds 5 MB."}
            Uri.fromFile(file).toString()
        } finally {temp.delete()}
    }
}
