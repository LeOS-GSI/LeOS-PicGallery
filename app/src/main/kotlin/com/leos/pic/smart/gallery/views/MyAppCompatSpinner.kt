package ca.on.sudbury.hojat.smartgallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import ca.on.sudbury.hojat.smartgallery.R
import ca.on.sudbury.hojat.smartgallery.adapters.MyArrayAdapter
import ca.on.sudbury.hojat.smartgallery.usecases.ApplyColorFilterUseCase

class MyAppCompatSpinner : AppCompatSpinner {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun setColors(textColor: Int, backgroundColor: Int) {
        if (adapter == null)
            return

        val cnt = adapter.count
        val items = arrayOfNulls<Any>(cnt)
        for (i in 0 until cnt)
            items[i] = adapter.getItem(i)

        val position = selectedItemPosition
        val padding = resources.getDimension(R.dimen.activity_margin).toInt()
        adapter = MyArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            items,
            textColor,
            backgroundColor,
            padding
        )
        setSelection(position)

        val superListener = onItemSelectedListener
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view != null) {
                    (view as TextView).setTextColor(textColor)
                }
                superListener?.onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
        ApplyColorFilterUseCase(background, textColor)
    }
}
