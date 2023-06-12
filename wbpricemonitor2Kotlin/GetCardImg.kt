package com.example.wbpricemonitor2Kotlin
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
class GetCardImg {
    companion object {
    suspend fun execute(prefs: SharedPreferences) {
        withContext(Dispatchers.IO) {
            val id = prefs.getString("inputId", "") ?: ""
            val id2 = id.take(2)
            val id3 = id.take(3)
            val id4 = id.take(4)
            val id5 = id.take(5)
            val id6 = id.take(6)
            val urls = arrayOf(
                "https://basket-01.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg",
                "https://basket-02.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg",
                "https://basket-03.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg",
                "https://basket-04.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg",
                "https://basket-05.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg",
                "https://basket-06.wb.ru/vol$id3/part$id5/$id/images/c246x328/1.jpg")
            val urlsLess8 = arrayOf(
                "https://basket-01.wb.ru/vol$id2/part$id4/$id/images/c246x328/1.jpg",
                "https://basket-02.wb.ru/vol$id2/part$id4/$id/images/c246x328/1.jpg",
                "https://basket-03.wb.ru/vol$id2/part$id4/$id/images/c246x328/1.jpg",
                "https://basket-04.wb.ru/vol$id2/part$id4/$id/images/c246x328/1.jpg")
            val urlsMore8 = arrayOf(
                "https://basket-05.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-06.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-07.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-08.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-09.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-10.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg",
                "https://basket-11.wb.ru/vol$id4/part$id6/$id/images/c246x328/1.jpg")
            val urlsToCheck = if (id.length == 8) urls else if (id.length > 8) urlsMore8 else urlsLess8
            for (url in urlsToCheck) {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "HEAD"
                    connection.connect()
                    val contentType = connection.contentType
                    if (contentType != null && contentType.startsWith("image/")) {
                        prefs.edit().putString("${id}_cardImgUrl", url).apply()
                        break
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    connection.disconnect();
                }}
}}}}