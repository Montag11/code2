package com.example.wbpricemonitor2Kotlin
import android.graphics.Canvas
import android.util.Log
import android.view.View
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.size
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.example.wbpricemonitor2Kotlin.model.Card
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class RecyclerItemHighlight (private val lifecycleOwner: LifecycleOwner) : ItemDecoration() {
    private var highlightPosition = 0
    fun setHighlightPosition(position: Int) { highlightPosition = position }
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        parent.forEach {child ->
            if (parent.getChildAdapterPosition(child) != highlightPosition) {
                child.visibility = View.GONE
                lifecycleOwner.lifecycleScope.launch { delay(2000)
                    child.visibility = View.VISIBLE }
            }}}
    }