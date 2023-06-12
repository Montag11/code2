package com.example.wbpricemonitor2Kotlin
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.wbpricemonitor2Kotlin.databinding.ActivitySearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private lateinit var prefs: SharedPreferences
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.mainLayout.setBackgroundColor(Color.WHITE)
        b.bottomMenuCardsListBtn.setImageResource(R.drawable.cards_list_icon_unselected)
        b.searchField.requestFocus()
        prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val aspectRatio = displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()

        val marginInDp1 = when {
            aspectRatio < 1.8 && prefs.getBoolean("isFirstLaunch", true) -> 70
            aspectRatio < 1.8 -> 120
            else -> 180
        }
        val marginInDp2 = when {
            aspectRatio < 1.8 && prefs.getBoolean("isFirstLaunch", true) -> 140
            aspectRatio < 1.8 -> 80
            else -> 130
        }
        setElementMargin(b.mainTittle, marginInDp1)
        setElementMargin(b.searchField, marginInDp2)

        if (prefs.getBoolean("isFirstLaunch", true)) {
            prefs.edit().putBoolean("isFirstLaunch", false).apply()
        } else {
            b.firstStartHint.visibility = View.GONE
            b.firstStartWelcomeHint.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        b.bottomMenuSearchBtn.setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork("MyWork")
            Log.d("MyPriceCheckService", "cancelWork")
        }
        b.mainTittle.setOnClickListener {
            prefs.edit().putBoolean("isFirstLaunch", true).apply()
//            prefs.edit().putBoolean("isFirstWorkLaunch", true).apply()
        }
        b.searchField.setOnEditorActionListener { _, actionId, event ->
            if (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER || actionId == EditorInfo.IME_ACTION_SEARCH) {
                b.searchFieldBtn.callOnClick()
            }
            false
        }
        b.bottomMenuCardsListBtn.setOnClickListener {
            val intent = Intent(this, MonitoringActivity::class.java)
            if (!this.intent.getBooleanExtra("isIntentFromNotificationCase", false)) {
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }
        b.searchFieldBtn.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(b.searchField.windowToken, 0)

            val inputText = b.searchField.text.toString().trim()
            if (inputText.isEmpty()) {
                showErrorMessage(R.string.no_user_input)
            } else if (!isConnected(this)) {
                showErrorMessage(R.string.no_internet_msg_1)
            } else {
                prefs.edit().putString("inputId", inputText).apply()
                val url = "https://card.wb.ru/cards/detail?dest=-1257786&nm=$inputText"
                b.searchCardInfo.text = "Загрузка..."

                lifecycleScope.launch {getCardDataTask(url)?.let { processingCardData(it) }}
            }}

        b.searchField.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                b.searchCardInfo.text = ""
                b.searchHelp.text = ""
                b.searchCardInfo.isEnabled = false
            }})
    }

    private suspend fun getCardDataTask(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                URL(url).readText()
            } catch (e: IOException) {
                null
            }}}

    private fun processingCardData(result: String) {
                try {
                    val products = JSONObject(result).getJSONObject("data").getJSONArray("products")

                    if (products.length() == 0) {
                        b.searchCardInfo.apply {
                            isEnabled = false
                            setTextColor(getColor(R.color.search_field_info_msg_color))
                            text = resources.getString(R.string.productNotFoundMsg)
                        }
                    }
                    else {
                        val cardId: String = b.searchField.text.toString()
                        val wbUrl = "https://www.wildberries.ru/catalog/$cardId/detail.aspx"
                        val value = products.getJSONObject(0)
                        val price = value.getString("salePriceU").dropLast(2)
                        val name = value.getString("name")
                        val brand = value.getString("brand")
                        val stocks = value.getJSONArray("sizes").getJSONObject(0).getJSONArray("stocks")

                        if (stocks.length() > 0) {
                            lifecycleScope.launch{ GetCardImg.execute(prefs) }
                            val spannableString = SpannableString("$brand\n$name").apply { setSpan(
                                ForegroundColorSpan(Color.BLACK), indexOf("\n") + 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
                            b.searchHelp.text = resources.getString(R.string.searchHelp)
                            b.searchCardInfo.apply {
                                setTextColor(Color.parseColor("#FF565656"))
                                text = spannableString
                                isEnabled = true
                                setOnClickListener {
                                if (cardId == prefs.getString("${cardId}_id", "")) {
                                    shortToastMsg(getString(R.string.productAlreadyExistMsg))
                                } else {
                                    val currentTime = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                    val edit = prefs.edit()
                                    edit.apply {
                                        putString("${cardId}_id", cardId)
                                        putString("${cardId}_brand", brand)
                                        putString("${cardId}_name", name)
                                        putString("${cardId}_price", price)
                                        putString("${cardId}_wbUrl", wbUrl)
                                        putString("${cardId}_time", currentTime)
                                        apply()
                                    }
                                    shortToastMsg(getString(R.string.productAddedMsg))
                                    lifecycleScope.launch {
                                        delay(1000)
                                        prefs.edit().putBoolean("isFirstNewCardIntentLaunch", true).apply()
                                        val intent = Intent(this@SearchActivity, MonitoringActivity::class.java)
                                        intent.putExtra("isNewCardIntent", true)
                                        startActivity(intent)
                                    }}}}

                        } else {
                            b.searchCardInfo.apply {
                                setTextColor(getColor(R.color.search_field_info_msg_color))
                                text = resources.getString(R.string.productOutOfStockMsg)
                                isEnabled = false
                            }}}
                } catch (je: JSONException) {
                    je.printStackTrace()
                }}
    private fun shortToastMsg(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
    private fun setElementMargin(element: View, marginInDp: Int) {
        val marginInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            marginInDp.toFloat(),
            resources.displayMetrics
        ).toInt()
        val params = element.layoutParams as RelativeLayout.LayoutParams
        params.setMargins(
            params.leftMargin,
            marginInPixels,
            params.rightMargin,
            params.bottomMargin
        )
        element.layoutParams = params
    }
    private fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return (capabilities != null) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    private fun showErrorMessage(messageResId: Int) {
        b.searchCardInfo.apply {
            isEnabled = false
            text = resources.getString(messageResId)
            setTextColor(getColor(R.color.search_field_info_msg_color))
        }}
}