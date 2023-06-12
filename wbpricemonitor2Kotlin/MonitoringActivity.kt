package com.example.wbpricemonitor2Kotlin
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.wbpricemonitor2Kotlin.adapter.CardAdapter
import com.example.wbpricemonitor2Kotlin.databinding.ActivityMonitoringBinding
import com.example.wbpricemonitor2Kotlin.model.Card
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
class MonitoringActivity : AppCompatActivity(), LifecycleObserver {
    private lateinit var b: ActivityMonitoringBinding
    private lateinit var prefs: SharedPreferences
    private var cardAdapter: CardAdapter? = null
    private var cardList: MutableList<Card> = ArrayList()
    private var isGroupNotificationIntent: Boolean = false
    private var clickedNotificationCardId: String? = null
    private var isNewCardIntent: Boolean = false
    private lateinit var prefsListener: OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.bottomMenuSearchBtn.setImageResource(R.drawable.search_icon_unselected)
        prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val configuration = this.resources.configuration
        configuration.fontScale = 1.1f
        this.resources.updateConfiguration(configuration, this.resources.displayMetrics)

        cardList.clear()
        isGroupNotificationIntent = intent.getBooleanExtra("isGroupNotificationIntent", false)
        clickedNotificationCardId = intent.getStringExtra("clickedNotificationCardId")
        isNewCardIntent = intent.getBooleanExtra("isNewCardIntent", false)

        if (isGroupNotificationIntent || clickedNotificationCardId != null) {

            val cardIds = prefs.getStringSet("groupNotificationIds", null) ?: HashSet()
            Log.d("MyPriceCheckService", "groupNotificationIds: $cardIds")

            prefs.edit().remove("cardAddTimesSet").apply()
            val cardAddTimesSet = prefs.getStringSet("cardAddTimesSet", null) ?: HashSet()
            val updatedCardAddTimesSet = HashSet(cardAddTimesSet)

            for (cardId in cardIds) {
                val brand = prefs.getString("${cardId}_brand", "") ?: ""
                val name = prefs.getString("${cardId}_name", "") ?: ""
                val wbUrl = prefs.getString("${cardId}_wbUrl", "") ?: ""
                val cardImgUrl = prefs.getString("${cardId}_cardImgUrl", "") ?: ""
                cardList.add(Card(cardId, brand, name, wbUrl, cardImgUrl))

                updatedCardAddTimesSet.add("${prefs.getString("${cardId}_time", "")}_$cardId")
                prefs.edit().putStringSet("cardAddTimesSet", updatedCardAddTimesSet).apply()
            }
            setCardRecycler(cardList)
            if (isGroupNotificationIntent) {
                prefs.edit().remove("groupNotificationIds").apply()
                Log.d("MyPriceCheckService", "isGroupNotificationIntent")
            }
            clickedNotificationCardId?.let {
                removeItemFromGroupNotificationIds(it, prefs)
                Log.d("MyPriceCheckService", "isClickedNotificationIntent: $it")
            }
        } else {
            val allEntries = prefs.all
            for ((key) in allEntries) {
                if (key.endsWith("_id")) {
                    val cardId = key.replace("_id", "")
                    val brand = prefs.getString("${cardId}_brand", "") ?: ""
                    val name = prefs.getString("${cardId}_name", "") ?: ""
                    val wbUrl = prefs.getString("${cardId}_wbUrl", "") ?: ""
                    val cardImgUrl = prefs.getString("${cardId}_cardImgUrl", "") ?: ""
                    cardList.add(Card(cardId, brand, name, wbUrl, cardImgUrl))
                }}
            setCardRecycler(cardList)
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        if (!isGroupNotificationIntent && clickedNotificationCardId == null) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val intent = Intent(this@MonitoringActivity, SearchActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                }})}

        Log.d("MyPriceCheckService", "groupNotificationIds: ${prefs.getStringSet("groupNotificationIds", null) ?: HashSet()}")

        b.bottomMenuCardsListBtn.setOnClickListener {
            val idsSet = prefs.getStringSet("idsArraySet", HashSet())
            val idArray1String = TextUtils.join(", ", idsSet!!)
            Toast.makeText(this, "idsArraySet: $idArray1String", Toast.LENGTH_SHORT).show()
            Toast.makeText(this, idsSet.size.toString(), Toast.LENGTH_SHORT).show()
//            prefs.edit().remove("isFirstWorkLaunch").apply()
        }

        b.bottomMenuSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            if(isGroupNotificationIntent || clickedNotificationCardId != null) {
                intent.putExtra("isIntentFromNotificationCase", true)
                Log.d("MyPriceCheckService", "isIntentFromNotificationCase")
            }
            startActivity(intent)
        }

        if (cardList.isNotEmpty()) {
            b.cardsListEmptyMsg.visibility = View.GONE
            b.monTopBar.visibility = View.VISIBLE
        } else {
            b.monTopBar.visibility = View.GONE
            b.cardsListEmptyMsg.visibility = View.VISIBLE
        }
        prefsListener = OnSharedPreferenceChangeListener { _, key ->
            if (cardList.isNotEmpty() && cardAdapter != null && key != null && key.endsWith("_switchState")) {
                cardAdapter!!.apply {
                    val isFirstNewCardIntentLaunch = prefs.getBoolean("isFirstNewCardIntentLaunch", true)
                    if (isNewCardIntent && isFirstNewCardIntentLaunch) { lifecycleScope.launch{ delay(2000)
                        notifyDataSetChanged()
                        prefs.edit().putBoolean("isFirstNewCardIntentLaunch", false).apply()
                        Log.d("MyPriceCheckService", "cat")
                    }}
                    else { notifyDataSetChanged() }
                    isPrefsChanged(true)
                }
            }}
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        b.monTopBarDelBtn.setOnClickListener { view ->
            val builder = MaterialAlertDialogBuilder(this)
            val messageView = TextView(this)
            messageView.text = "Вы уверены, что хотите удалить все добавленные товары?"
            messageView.textSize = 15.5f
            messageView.setPadding(60, 50, 30, 10)
            builder.setView(messageView)
                .setPositiveButton("Удалить") { _, _ ->
                    cardList.clear()
                    cardAdapter!!.notifyDataSetChanged()
                    val isFirstLaunch = prefs.getBoolean("isFirstLaunch", false)
                    val edit = prefs.edit()
                    edit.clear()
                    edit.putBoolean("isFirstLaunch", isFirstLaunch)
                    edit.apply()
                    Glide.get(view.context).clearMemory()
                    lifecycleScope.launch(Dispatchers.IO) {Glide.get(view.context).clearDiskCache()}
                    b.monTopBar.visibility = View.GONE
                    b.cardsListEmptyMsg.visibility = View.VISIBLE
                }
                .setNegativeButton("Отменить") { dialog, _ -> dialog.cancel() }
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, R.color.dialog_btn_color))
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(context, R.color.dialog_btn_color))
                }
        }

        // sort state check
        val sortState = prefs.getString("sortState", "") ?: ""
        if (sortState.isEmpty()) {
            prefs.edit().putString("sortState", "Сначала новые  ⇅").apply()
        } else {
            b.monTopBarSortBtn.text = sortState
        }
        if (sortState == "Сначала новые  ⇅") {
            cardList.sortWith { c1, c2 ->
                sortCards(c1, c2, true)
            }
        } else if (sortState == "Сначала старые  ⇅") {
            cardList.sortWith { c1, c2 ->
                sortCards(c1, c2, false)
            }
            if (prefs.getBoolean("isActivityReloaded", false)) {
                b.recyclerView.scrollToPosition(cardAdapter!!.itemCount - 1)
            }
            prefs.edit().remove("isActivityReloaded").apply()
        }
        b.monTopBarSortBtn.setOnClickListener {
            if (b.monTopBarSortBtn.text.toString() == "Сначала новые  ⇅") {
                cardList.sortWith { c1, c2 ->
                    sortCards(c1, c2, false)
                }
                b.monTopBarSortBtn.text = "Сначала старые  ⇅"
            } else if (b.monTopBarSortBtn.text.toString() == "Сначала старые  ⇅") {
                cardList.sortWith { c1, c2 ->
                    sortCards(c1, c2, true)
                }
                b.monTopBarSortBtn.text = "Сначала новые  ⇅"
            }
            prefs.edit().putString("sortState", b.monTopBarSortBtn.text.toString()).apply()
            cardAdapter!!.notifyDataSetChanged()
        }
        if (isNewCardIntent && sortState == "Сначала старые  ⇅") {
            b.recyclerView.scrollToPosition(cardAdapter!!.itemCount - 1)
            val decoration = RecyclerItemHighlight(this)
            b.recyclerView.addItemDecoration(decoration)
            decoration.setHighlightPosition(cardAdapter!!.itemCount - 1)
            reloadActivity()
        }
        else if (isNewCardIntent && sortState == "Сначала новые  ⇅") {
            b.recyclerView.scrollToPosition(0)
            val decoration = RecyclerItemHighlight(this)
            b.recyclerView.addItemDecoration(decoration)
            decoration.setHighlightPosition(0)
            reloadActivity()
        }
        else if (clickedNotificationCardId != null) {
            val cardAddTimesSet = prefs.getStringSet("cardAddTimesSet", null) ?: HashSet()
            val cardAddTimesList = ArrayList<String>()
            cardAddTimesSet.forEach { cardAddTime ->
                val addTime = cardAddTime.substringBefore("_")
                val cardId = cardAddTime.substringAfter("_")
                cardAddTimesList.add("${addTime}_$cardId")
            }
            if (sortState == "Сначала новые  ⇅") {
                cardAddTimesList.sortWith{string1, string2 -> sortNotificationCardsList(string1, string2, true)}
            } else {
                cardAddTimesList.sortWith{string1, string2 -> sortNotificationCardsList(string1, string2, false)}
            }
            run loop@ {
                cardAddTimesList.forEachIndexed { index, cardAddTime ->
                    val cardId = cardAddTime.substringAfter("_")
                    if (cardId == clickedNotificationCardId) {
                        b.recyclerView.scrollToPosition(index)
                        val decoration = RecyclerItemHighlight(this)
                        b.recyclerView.addItemDecoration(decoration)
                        decoration.setHighlightPosition(index)
                        return@loop
                    }}}
        }
    }
    private fun sortCards(c1: Card, c2: Card, isNewFirst: Boolean): Int {
        val time1 = prefs.getString(c1.cardId + "_time", "")?:""
        val time2 = prefs.getString(c2.cardId + "_time", "")?:""
        val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val date1 = format.parse(time1)
        val date2 = format.parse(time2)
        return if (isNewFirst)
            date2?.compareTo(date1) ?: 0
        else
            date1?.compareTo(date2) ?: 0
    }
    private fun sortNotificationCardsList(dateString1: String, dateString2: String, isNewFirst: Boolean): Int {
        val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        try {
            val date1 = dateString1.substringBefore("_")
            val date2 = dateString2.substringBefore("_")
            val parsedDate1 = format.parse(date1)
            val parsedDate2 = format.parse(date2)
            if (isNewFirst && parsedDate2 != null) return parsedDate2.compareTo(parsedDate1)
            else if (parsedDate1 != null) return parsedDate1.compareTo(parsedDate2)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return 0
    }
    private fun setCardRecycler(cardList: MutableList<Card>) {
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        b.recyclerView.layoutManager = layoutManager
        cardAdapter = CardAdapter(this, cardList, this)
        b.recyclerView.adapter = cardAdapter
    }
    private fun reloadActivity() {
        lifecycleScope.launch{  delay(2000)
            val intent = Intent(this@MonitoringActivity, MonitoringActivity::class.java)
            startActivity(intent)
            prefs.edit().putBoolean("isActivityReloaded", true).apply()
        }}
    private fun removeItemFromGroupNotificationIds( cardId: String, prefs: SharedPreferences) {
        val groupNotificationIds = prefs.getStringSet("groupNotificationIds", null) ?: HashSet()
        val modifiedIdsArraySet: MutableSet<String> = HashSet(groupNotificationIds)
        modifiedIdsArraySet.remove(cardId)
        prefs.edit().putStringSet("groupNotificationIds", HashSet(modifiedIdsArraySet)).apply()
    }
}