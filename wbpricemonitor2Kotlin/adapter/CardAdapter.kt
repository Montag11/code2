package com.example.wbpricemonitor2Kotlin.adapter
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.bumptech.glide.Glide
import com.example.wbpricemonitor2Kotlin.MonitoringActivity
import com.example.wbpricemonitor2Kotlin.R
import com.example.wbpricemonitor2Kotlin.UpdateCardData
import com.example.wbpricemonitor2Kotlin.adapter.CardAdapter.CardViewHolder
import com.example.wbpricemonitor2Kotlin.databinding.CardItemBinding
import com.example.wbpricemonitor2Kotlin.model.Card
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CardAdapter(var context: Context, private var cardsList: MutableList<Card>, private val lifecycleOwner: LifecycleOwner) :
    RecyclerView.Adapter<CardViewHolder>() {
    lateinit var prefs: SharedPreferences
    private var isGlideImgSet = false
    private var isPrefsChanged = false
    fun isPrefsChanged(isPrefsChanged: Boolean) {
        this.isPrefsChanged = isPrefsChanged
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val b = CardItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(b)
    }

    @SuppressLint("CheckResult", "NotifyDataSetChanged", "SetTextI18n", "ClickableViewAccessibility")
    override fun onBindViewHolder(holder: CardViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val configuration = context.resources.configuration
        configuration.fontScale = 1.1f
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        val cardsMaxLimit = 20

        prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var idsArraySet = prefs.getStringSet("idsArraySet", null) ?: HashSet()
        val cardId = cardsList[position].cardId
        val switchState = prefs.getBoolean("${cardId}_switchState", false)
        if (isPrefsChanged) {
            Log.d("MyPriceCheckService", "isPrefsChanged")
            if (prefs.getBoolean("isFirstWorkLaunch", true) && switchState) {
                Log.d("MyPriceCheckService", "startFirstUniqueWork")
                startUniqueWork()
                prefs.edit().putBoolean("isFirstWorkLaunch", false).apply()
            } else {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("MyWork").observe(context as LifecycleOwner)
            { value ->
                for (workInfo in value) {
                    val state = workInfo.state
                    Log.d("MyPriceCheckService", "WorkOnChanged: $state")
                    if (idsArraySet.size > 0 && isPrefsChanged && state != WorkInfo.State.ENQUEUED) {
                        Log.d("MyPriceCheckService", "startUniqueWork")
                        startUniqueWork()
                    } else if (idsArraySet.size == 0 && isPrefsChanged && state != WorkInfo.State.CANCELLED) {
                        WorkManager.getInstance(context).cancelUniqueWork("MyWork")
                        Log.d("MyPriceCheckService", "cancelUniqueWork")
                    }
                    isPrefsChanged = false
                }}}
        }
        holder.b.monCardBrandValue.text = cardsList[position].brand
        Glide.with(holder.b.cardImgRes.context).load(cardsList[position].cardImgUrl)
            .into(holder.b.cardImgRes)
        if (cardsList[position].cardImgUrl == "") {
            if (isGlideImgSet) {
                Glide.with(holder.b.cardImgRes.context)
                    .load(prefs.getString("${cardId}_cardImgUrl", "")).into(holder.b.cardImgRes)
            } else {
                lifecycleOwner.lifecycleScope.launch {
                    delay(2000)
                    Glide.with(holder.b.cardImgRes.context).load(prefs.getString("${cardId}_cardImgUrl", "")).into(holder.b.cardImgRes)
                    isGlideImgSet = true
                }}
        }
        if (cardsList[position].name.length > 16) {
            holder.b.monCardNameValue.text = cardsList[position].name.take(16) + "..."
        } else {
            holder.b.monCardNameValue.text = cardsList[position].name
        }
        val priceChangedValue = prefs.getString("${cardId}_priceChangedValue", "")?:""
        val isOutOfStock = prefs.getBoolean("${cardId}_isOutOfStock", false)

        holder.b.monCardChangedPriceValue.apply {
        if (priceChangedValue.isEmpty()) {
                if(!switchState) {
                    text = "- - -"
                    textSize = 15.5f
                    setTypeface(null, Typeface.NORMAL)
                    paintFlags = holder.b.monCardOutOfStock.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                    setTextColor(Color.parseColor("#FFB8B8B8"))
                } else {
                    text = "Ожидание..."
                    textSize = 12.5f
                    paintFlags = holder.b.monCardOutOfStock.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(Color.parseColor("#ABABAB"))
                }} else {
                textSize = 15.5f
                setTypeface(null, Typeface.NORMAL)
                paintFlags = holder.b.monCardOutOfStock.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                text = "-${prefs.getString("${cardId}_priceChangedValue", "")} ₽"
                setTextColor(Color.parseColor("#FF78D347"))
            }}
        if (isOutOfStock) {
            holder.b.monCardChangedPriceValue.visibility = View.GONE
            holder.b.monCardOutOfStock.apply {
                text = context.getString(R.string.out_of_stock_msg)
                visibility = View.VISIBLE
                paintFlags = holder.b.monCardOutOfStock.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }} else {
            holder.b.monCardChangedPriceValue.visibility = View.VISIBLE
            holder.b.monCardOutOfStock.visibility = View.GONE
        }
        holder.b.cardImgRes.setOnClickListener { shortToastMsg(prefs.getString("${cardId}_price", ""))
//            val allEntries = prefs.all
//            for ((key) in allEntries) {
//            if (key.endsWith("_priceChangedValue")) {
//                edit.remove(key)
//            }}
//            edit.apply()
            prefs.edit().remove("${cardId}_priceChangedValue").apply()
//            WorkManager.getInstance(context).cancelUniqueWork("MyWork")
            holder.b.monCardChangedPriceValue.text = "- - -"
//            edit.remove("isFirstWorkLaunch").apply()
        }

        holder.b.cardDeleteButton.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(holder.itemView.context)
            val messageView = TextView(context)
            messageView.text = "Вы уверены, что хотите удалить товар?"
            messageView.textSize = 15.5f
            messageView.setPadding(40, 65, 40, 10)
            messageView.gravity = Gravity.CENTER
            builder.setView(messageView)
                .setPositiveButton("Удалить") { _, _ ->
                    val allEntries = prefs.all
                    for ((key) in allEntries) {
                        if (key.startsWith(cardId)) {
                            edit.remove(key)
                        }}
                    edit.apply()
                    removeFromIdsArray(idsArraySet, cardId, prefs)
                    cardsList.removeAt(position)
                    notifyDataSetChanged()
                    if (cardsList.isEmpty()) {
                        val intent = Intent(context, MonitoringActivity::class.java)
                        context.startActivity(intent)
                    }
                }
                .setNegativeButton("Отменить") { dialog, _ -> dialog.cancel() }
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, R.color.dialog_btn_color))
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(context, R.color.dialog_btn_color))
                }
        }
        holder.b.goToWbBtn.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cardsList[position].wbUrl))
            context.startActivity(browserIntent)
//            holder.b.monCardChangedPriceValue.text = ""
//            edit.remove(cardId + "_priceChangedValue")
//            edit.apply()
//            holder.b.monCardChangedPriceValue.text = "- - -"
        }
        val switchListener = CompoundButton.OnCheckedChangeListener { _, b ->
            val idsArrayList: MutableList<String> = ArrayList(idsArraySet)
            if (b) {
                holder.b.monCardStatusValue.text = "Активен"
                holder.b.monCardStatusValue.setTextColor(context.getColor(R.color.active_status_color))
                holder.b.switchDescription.text = "Отключить"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    holder.b.statusSwitch.trackDrawable.colorFilter =  BlendModeColorFilter(Color.parseColor("#FF78D347"), BlendMode.SRC_IN)
                }
                else {
                    @Suppress("Deprecation")
                    holder.b.statusSwitch.trackDrawable.setColorFilter(Color.parseColor("#FF78D347"), PorterDuff.Mode.SRC_IN)
                }
                edit.putBoolean("${cardId}_switchState", true)
                val marginInDp = 40
                val marginInPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    marginInDp.toFloat(),
                    context.resources.displayMetrics
                ).toInt()
                val layoutParams = holder.b.statusSwitch.layoutParams as MarginLayoutParams
                layoutParams.width = marginInPx
                holder.b.statusSwitch.layoutParams = layoutParams
                val case2CardId = cardId + "_" + prefs.getInt("${cardId}_spinnerItemPriceValue", 0)
                if (prefs.getInt("${cardId}_spinnerItemValue", 0) == 1 && !idsArrayList.contains(cardId)) {
                        idsArrayList.add(cardId)
                }
                else if (prefs.getInt("${cardId}_spinnerItemValue", 0) == 2 && !idsArrayList.contains(case2CardId)) {
                        idsArrayList.add(case2CardId)
                }
                idsArraySet = HashSet(idsArrayList)
                edit.putStringSet("idsArraySet", idsArraySet)
                edit.apply()
            } else {
                holder.b.monCardStatusValue.text = "Неактивен"
                holder.b.switchDescription.text = "Включить"
                holder.b.monCardStatusValue.setTextColor(context.getColor(R.color.Inactive_status_color))
                holder.b.statusSwitch.trackDrawable.clearColorFilter()
                edit.putBoolean("${cardId}_switchState", false)
                edit.remove("${cardId}_outOfStock")
                edit.apply()
                val layoutParams = holder.b.statusSwitch.layoutParams as MarginLayoutParams
                layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                holder.b.statusSwitch.layoutParams = layoutParams
                removeFromIdsArray(idsArraySet, cardId, prefs)
            }
        }
        holder.b.statusSwitch.setOnCheckedChangeListener(switchListener)
        holder.b.statusSwitch.isChecked = switchState
        switchListener.onCheckedChanged(holder.b.statusSwitch, switchState)

        val spinner = holder.b.monCardMethodValue
        val longNames = arrayOf("Отменить", "Оповестить, если цена будет ниже чем текущая", "Оповестить, если цена будет ниже на выбранное значение")
        val adapter = ArrayAdapter(holder.itemView.context, R.layout.my_spinner_layout, R.id.spinnerItem, longNames)
        adapter.setDropDownViewResource(R.layout.my_spinner_dropdown_layout)
        spinner.adapter = adapter
        val savedPosition = prefs.getInt("${cardId}_spinnerItemValue", 0)
        spinner.setSelection(savedPosition)
        val savedPriceSelect = prefs.getInt("${cardId}_spinnerItemPriceValue", 0)
        val shortNames = arrayOf("Не выбран", "Ниже текущей", "Ниже на $savedPriceSelect ₽")
        val spinnerSelectedCount = intArrayOf(0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, position: Int, l: Long) {
                val spinnerItemText = view.findViewById<View>(R.id.spinnerItem) as TextView
                spinnerSelectedCount[0]++
                spinnerItemText.text = shortNames[position]
                if (position == 0) {
                    spinnerItemText.setTextColor(Color.parseColor("#919191"))
                } else {
                    spinnerItemText.setTextColor(Color.parseColor("#FF626262"))
                }
                edit.putInt("${cardId}_spinnerItemValue", position)
                edit.apply()
                when (position) {
                    0 -> {
                        holder.b.statusSwitch.isEnabled = false
                        holder.b.switchDescription.setTextColor(Color.parseColor("#919191"))
                        holder.b.statusSwitch.isChecked = false
                        if (prefs.getInt("${cardId}_spinnerItemPriceValue", 0) > 0) {
                            edit.remove("${cardId}_spinnerItemPriceValue")
                            edit.apply()
                            notifyDataSetChanged()
                        }}
                    1 -> if ((idsArraySet.size < cardsMaxLimit) || switchState) {
                        holder.b.statusSwitch.isEnabled = true
                        holder.b.switchDescription.setTextColor(Color.parseColor("#FF717171"))
                    } else {
                        holder.b.switchDescription.setTextColor(Color.parseColor("#919191"))
                        holder.b.statusSwitch.isEnabled = false
                    }
                    2 -> {
                        if (idsArraySet.size < cardsMaxLimit || switchState) {
                            holder.b.statusSwitch.isEnabled = true
                            holder.b.switchDescription.setTextColor(Color.parseColor("#FF717171"))
                        } else {
                            holder.b.switchDescription.setTextColor(Color.parseColor("#919191"))
                            holder.b.statusSwitch.isEnabled = false
                        }
                        if (spinnerSelectedCount[0] > 1) {
                            val builder = MaterialAlertDialogBuilder(holder.itemView.context)
                            val input = EditText(holder.itemView.context)
                            input.inputType = InputType.TYPE_CLASS_NUMBER
                            input.gravity = Gravity.CENTER
                            input.setTextColor(Color.parseColor("#FF626262"))
                            input.textSize = 15f
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                input.setTextCursorDrawable(R.drawable.cursor_color)
                            }
                            input.setPadding(0, 90, 0, 30)
                            input.setBackgroundColor(Color.TRANSPARENT)
                            input.hint = context.getString(R.string.enter_value_msg)
                            builder.setView(input)
                            builder.setCancelable(false)
                                .setPositiveButton("Потвердить") { _, _ -> }
                            builder.setNegativeButton("Отменить") { dialog, _ ->
                                if (prefs.getInt("${cardId}_spinnerItemPriceValue", 0) == 0) {
                                    spinner.setSelection(0)
                                }
                                dialog.cancel()
                            }
                            val dialog = builder.create()
                            dialog.show()
                            dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                            input.requestFocus()
                            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            positiveButton.setTextColor(context.getColor(R.color.dialog_btn_color))
                            positiveButton.setOnClickListener {
                                if (input.text.toString() == "") {
                                    shortToastMsg(context.getString(R.string.enter_value_msg))
                                } else if (input.text.toString().toInt() < 1) {
                                    shortToastMsg(context.getString(R.string.zero_enter_msg))
                                } else {
                                    edit.putInt("${cardId}_spinnerItemPriceValue", input.text.toString().toInt())
                                    edit.apply()
                                    notifyDataSetChanged()
                                    dialog.dismiss()
                                }
                            }
                            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            negativeButton.setTextColor(context.getColor(R.color.dialog_btn_color))
                            input.setOnEditorActionListener { _, actionId, event ->
                                if (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER || actionId == EditorInfo.IME_ACTION_DONE) {
                                    positiveButton.callOnClick()
                                }
                                false
                            }}}
                }
            }
            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        spinner.setOnTouchListener { _, event ->
            val spinnerPosition = spinner.selectedItemPosition
            if (event.action == MotionEvent.ACTION_UP && spinnerPosition != 0 && prefs.getBoolean("${cardId}_switchState", false)) {
                shortToastMsg(context.getString(R.string.spinner_change_method_msg))
                return@setOnTouchListener true
            } else if (event.action == MotionEvent.ACTION_UP && spinnerPosition == 2 && !prefs.getBoolean("${cardId}_switchState", false)) {
                spinner.setSelection(1)
            }
            false
        }
        holder.b.spinnerLayout.setOnClickListener {
            if (spinner.selectedItemPosition == 0) {
                shortToastMsg(context.getString(R.string.spinner_set_method_msg))
            } else if (idsArraySet.size < cardsMaxLimit || prefs.getBoolean("${cardId}_switchState", false)) {
                holder.b.statusSwitch.toggle()
            } else {
                longToastMsg(context.getString(R.string.cards_limit_msg))
            }}
    }

    override fun getItemCount(): Int { return cardsList.size }

    class CardViewHolder(val b: CardItemBinding) : RecyclerView.ViewHolder(b.root)

    private fun removeFromIdsArray(idsArraySet: Set<String>, cardId: String, prefs: SharedPreferences) {
        val modifiedIdsArraySet: MutableSet<String> = HashSet(idsArraySet)
        modifiedIdsArraySet.removeIf { str -> str.startsWith(cardId) }
        prefs.edit().putStringSet("idsArraySet", HashSet(modifiedIdsArraySet)).apply()
    }
    private fun shortToastMsg(text: String?) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    private fun longToastMsg(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
    private fun startUniqueWork () {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val saveRequest = PeriodicWorkRequest.Builder(UpdateCardData::class.java,15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("MyWork", ExistingPeriodicWorkPolicy.KEEP, saveRequest)
    }
}