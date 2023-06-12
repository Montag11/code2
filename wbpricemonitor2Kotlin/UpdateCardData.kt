package com.example.wbpricemonitor2Kotlin
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
class UpdateCardData(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.d("MyPriceCheckService", "doWorkStart")
        return try {
            Log.d("MyPriceCheckService", "getDataTaskTryStart")
            getDataTask()
            Log.d("MyPriceCheckService", "getDataTaskTryEnd")
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MyPriceCheckService", "getDataTaskCatch", e)
            Result.failure()
        }
    }
    private fun getDataTask() {
            Log.d("MyPriceCheckService", "getDataTaskStart")
            if (!isConnected(context)) {
                sendNotificationIfNoConnection()
                Log.d("MyPriceCheckService", "getDataTaskStartNoConnection")
                return
            }
            var IOExceptionOccurred = false
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            sendCountNotification(prefs)

            val idsArraySet = prefs.getStringSet("idsArraySet", null) ?: HashSet()
            val idsArrayList: List<String> = ArrayList(idsArraySet)
            if (idsArraySet.isNotEmpty()) {
                var case2ValueInt = 0
                var isCase2 = false
                for (valCardId in idsArrayList) {
                    var cardId = valCardId
                    if (cardId.contains("_")) {
                        cardId = cardId.substringBefore("_")
                        val case2Value = cardId.substringAfter("_")
                        case2ValueInt = case2Value.toInt()
                        isCase2 = true
                        Log.d("MyPriceCheckService", "Case2: $cardId")
                    } else {
                        Log.d("MyPriceCheckService", "Case1: $cardId")
                    }
                    val urlString = "https://card.wb.ru/cards/detail?dest=-1257786&nm=$cardId"
                    try {
                        val result = URL(urlString).readText()
                        val jsonObject = JSONObject(result)
                        val data = jsonObject.getJSONObject("data")
                        val products = data.getJSONArray("products")
                        for (i in 0 until products.length()) {
                            val product = products.getJSONObject(i)
                            val salePriceWith00 = product.getInt("salePriceU")
                            var name = product.getString("name")
                            name = if (name.length > 25) name.take(25) else name
                            val sizes = product.getJSONArray("sizes")
                            for (j in 0 until sizes.length()) {
                                val size = sizes.getJSONObject(j)
                                val stocks = size.getJSONArray("stocks")
                                if (stocks.length() == 0) {
                                    prefs.edit().putBoolean("${cardId}_isOutOfStock", true).apply()
                                } else if (stocks.length() > 0) {
                                    prefs.edit().putBoolean("${cardId}_isOutOfStock", false).apply()
                                }
                            }
                            val salePriceString = salePriceWith00.toString().dropLast(2)
                            val salePrice = salePriceString.toInt()
                            val savedPrice = prefs.getString("${cardId}_price", "")!!.toInt()
                            val newPriceChangedValueInt = savedPrice - salePrice
                            Log.d("MyPriceCheckService", "newPriceChangedValueInt: ${name.take(16)} $savedPrice $newPriceChangedValueInt")
                            val newPriceChangedValue = newPriceChangedValueInt.toString()
                            val savedPriceChangedValue = prefs.getString("${cardId}_priceChangedValue", "")
                            if (isCase2) {
                                if (newPriceChangedValueInt >= case2ValueInt) {
                                    prefs.edit().putString("${cardId}_priceChangedValue", newPriceChangedValue).apply()
                                    if (!prefs.getBoolean("${cardId}_isOutOfStock", false) && newPriceChangedValue != savedPriceChangedValue) {
                                        sendNotification(cardId, name, prefs, true)
                                        Log.d("MyPriceCheckService", "sendNotificationCallCase2: $cardId")
                                    }}
                                if (newPriceChangedValueInt < case2ValueInt && savedPriceChangedValue != "") {
                                    cancelNotification(cardId, name, prefs)
                                    prefs.edit().putString("${cardId}_priceChangedValue", "").apply()
                                }
                            } else {
                                if (newPriceChangedValueInt >= 10) {
                                    prefs.edit().putString("${cardId}_priceChangedValue", newPriceChangedValue).apply()
                                    if (!prefs.getBoolean("${cardId}_isOutOfStock", false) && newPriceChangedValue != savedPriceChangedValue) {
                                        sendNotification(cardId, name, prefs, false)
                                        Log.d("MyPriceCheckService", "sendNotificationCall: $cardId")
                                    }}
                                if (newPriceChangedValueInt < 10 && savedPriceChangedValue != "") {
                                    cancelNotification(cardId, name, prefs)
                                    prefs.edit().putString("${cardId}_priceChangedValue", "").apply()
                                }}
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        IOExceptionOccurred = true
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
            if (IOExceptionOccurred) {
                sendNotificationIfNoConnection()
                Log.d("MyPriceCheckService", "onPostExecuteNoInternet")
            }
            Log.d("MyPriceCheckService", "getDataTaskEnd")
        }
    private fun sendNotification(cardId: String, prodName: String, prefs: SharedPreferences, isCase2: Boolean) {
        val groupNotificationIds = prefs.getStringSet("groupNotificationIds", null) ?: HashSet()
        val updatedCardIds: MutableSet<String> = HashSet(groupNotificationIds)
        if (!updatedCardIds.contains(cardId)) {
            updatedCardIds.add(cardId)
        }
        prefs.edit().putStringSet("groupNotificationIds", updatedCardIds).apply()

        val priceChangedValue = prefs.getString("${cardId}_priceChangedValue", "")?:"".drop(1).replace(" ", "")
        val singleNotificationIntent = Intent(context, MonitoringActivity::class.java)
        singleNotificationIntent.putExtra("clickedNotificationCardId", cardId)

        singleNotificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        val requestCode = cardId.toInt()
        val singleNotificationPendingIntent = PendingIntent.getActivity(context, requestCode, singleNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val groupIntent = Intent(context, MonitoringActivity::class.java)
        groupIntent.putExtra("isGroupNotificationIntent", true)
        groupIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        val groupPendingIntent = PendingIntent.getActivity(context, 0, groupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "main_channel_id"
            val channelName: CharSequence = "Уведомления о снижении цен"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        val notificationId = cardId.toInt()
        val builder = NotificationCompat.Builder(context, "main_channel_id")
        builder
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(context.getColor(R.color.notification_color))
            .setContentIntent(singleNotificationPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("group")
        if (isCase2) {
            builder
                .setContentTitle("Снижение цены! (-$priceChangedValue)  ✅ ")
                .setContentText("У $prodName снизилась цена на $priceChangedValue.")
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("У $prodName снизилась цена на $priceChangedValue."))
            Log.d("MyPriceCheckService", "notificationCase2: $cardId")
        } else {
            builder
                .setContentTitle("Снижение цены!  ✅ ")
                .setContentText("У $prodName снизилась цена.")
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("У $prodName снизилась цена."))
            Log.d("MyPriceCheckService", "notificationCase1: $cardId")
        }
        val groupBuilder = NotificationCompat.Builder(context, "main_channel_id")
            .setContentTitle("Снижение цен!  ✅ ")
            .setContentText("Снижение цен!  ✅ ")
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(context.getColor(R.color.notification_color))
            .setGroup("group")
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(groupPendingIntent)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return }
        notificationManager.notify(notificationId, builder.build())
        notificationManager.notify("group", 0, groupBuilder.build())
    }

    private fun cancelNotification(cardId: String, prodName: String, prefs: SharedPreferences) {
        val notificationId = cardId.toInt()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        for (notification in activeNotifications) {
            if (notification.id == notificationId) {
                notificationManager.cancel(notificationId)
                removeItemFromGroupNotificationIds(cardId, prefs)
                Log.d("MyPriceCheckService", "cancelNotification: $cardId $prodName")
                break
            }}

    }
    private fun sendNotificationIfNoConnection() {
        val intent = Intent(context, MonitoringActivity::class.java)
        @SuppressLint("UnspecifiedImmutableFlag") val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "my_channel_id"
            val channelName: CharSequence = "Уведомления об отсутствии соединения"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val description = "Channel description"
            val channel = NotificationChannel(channelId, channelName, importance)
            channel.description = description
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, "my_channel_id")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Не удается установить соединение.")
            .setContentText("Нет интернета.")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(
            context
        )
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(0, builder.build())
        Log.d("MyPriceCheckService", "notificationNoInternet")
    }
    private fun sendCountNotification(prefs: SharedPreferences) {
        var notificationId = prefs.getInt("count", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "my_channel_id"
            val channelName: CharSequence = "Уведомления об отсутствии соединения"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance)
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, "my_channel_id")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Test")
            .setGroup("countGroup")
            .setContentText("Count: $notificationId")
            .setColor(context.getColor(R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
        val groupBuilder = NotificationCompat.Builder(context, "main_channel_id")
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(context.getColor(R.color.notification_color))
            .setGroup("countGroup")
            .setGroupSummary(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(
            context
        )
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationId++
        notificationManager.notify(notificationId, builder.build())
        notificationManager.notify("countGroup", 0, groupBuilder.build())
        Log.d("MyPriceCheckService", "notificationCount: $notificationId")
        prefs.edit().putInt("count", notificationId).apply()
    }
    private fun isConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            return (capabilities != null) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    private fun removeItemFromGroupNotificationIds( cardId: String, prefs: SharedPreferences) {
        val groupNotificationIds = prefs.getStringSet("groupNotificationIds", null) ?: HashSet()
        val modifiedIdsArraySet: MutableSet<String> = HashSet(groupNotificationIds)
        modifiedIdsArraySet.remove(cardId)
        prefs.edit().putStringSet("groupNotificationIds", HashSet(modifiedIdsArraySet)).apply()
    }
}