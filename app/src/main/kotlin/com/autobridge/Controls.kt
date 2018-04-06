package com.autobridge

import android.annotation.SuppressLint
import android.app.Fragment
import android.databinding.DataBindingUtil
import android.databinding.ObservableList
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.preference.*
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.drawer.*

abstract class NavigationDrawerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.setContentView(R.layout.drawer)

        this.setSupportActionBar(this.toolbar)

        this.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        this.supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_menu)

        this.navigationView.inflateMenu(this.getMenu())

        this.setSelectedMenuItem(this.getDefaultMenuItemId())

        this.navigationView.inflateHeaderView(this.getHeaderViewResource())

        this.navigationView.setNavigationItemSelectedListener {
            this.findViewById<DrawerLayout>(R.id.drawerLayout).closeDrawers()
            this.setSelectedMenuItem(it.itemId)
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            android.R.id.home -> {
                this.findViewById<DrawerLayout>(R.id.drawerLayout).openDrawer(GravityCompat.START)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setSelectedMenuItem(menuItemId: Int) {
        this.navigationView.menu.getItems().forEach {
            it.isChecked = it.itemId == menuItemId

            if (it.itemId == menuItemId)
                this.toolbar.subtitle = it.title
        }

        this.fragmentManager
                .beginTransaction()
                .replace(R.id.fragmentPlaceholder, this.createFragmentForMenuItemId(menuItemId))
                .commit()
    }

    abstract fun getMenu(): Int
    abstract fun getDefaultMenuItemId(): Int
    abstract fun getHeaderViewResource(): Int
    abstract fun createFragmentForMenuItemId(menuItemId: Int): Fragment
}

class ObservableListViewAdapter<T>(val list: ObservableList<T>, val layout: Int, val bindingVariable: Int) : RecyclerView.Adapter<ViewHolder<T>>() {
    init {
        this.list.addOnListChangedCallback(object : ObservableList.OnListChangedCallback<ObservableList<T>>() {
            override fun onChanged(sender: ObservableList<T>?) = notifyDataSetChanged()
            override fun onItemRangeRemoved(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) = notifyItemRangeRemoved(positionStart, itemCount)
            override fun onItemRangeMoved(sender: ObservableList<T>?, fromPosition: Int, toPosition: Int, itemCount: Int) = notifyDataSetChanged()
            override fun onItemRangeInserted(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) = notifyItemRangeInserted(positionStart, itemCount)
            override fun onItemRangeChanged(sender: ObservableList<T>?, positionStart: Int, itemCount: Int) = notifyItemRangeChanged(positionStart, itemCount)
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder<T> =
            ViewHolder(
                    DataBindingUtil.inflate<ViewDataBinding>(
                            LayoutInflater.from(parent!!.context),
                            this.layout,
                            parent,
                            false
                    ),
                    this.bindingVariable
            )

    override fun getItemCount(): Int = this.list.size

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) = holder.bind(this.list[position])
}

class ViewHolder<T>(val viewDataBinding: ViewDataBinding, val bindingVariable: Int) : RecyclerView.ViewHolder(viewDataBinding.root) {
    fun bind(data: T) {
        this.viewDataBinding.setVariable(this.bindingVariable, data)
        this.viewDataBinding.executePendingBindings()
    }
}

//class TestActivity : AppCompatActivity() {
//    @SuppressLint("ResourceType", "NewApi")
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        val layout = android.widget.LinearLayout(this).mutate {
//            it.id = 200
//            it.orientation = android.widget.LinearLayout.VERTICAL
//        }
//
//        layout.addView(TextView(this).mutate {
//            it.text = "this is another test"
//        })
//
//        this.fragmentManager.beginTransaction().add(200, Fragment2()).commit()
//
//        this.setContentView(layout)
//
//        this.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
//        this.supportActionBar!!.setDisplayShowHomeEnabled(true)
//        this.supportActionBar!!.setSubtitle("Configuration")
//
//        //this.setSupportActionBar(Toolbar(this))
//    }
//
//    override fun onSupportNavigateUp(): Boolean {
//        this.onBackPressed();
//        return true;
//    }
//}
//
//class Fragment2 : PreferenceFragment() {
//    @SuppressLint("NewApi")
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        this.preferenceScreen = this.preferenceManager.createPreferenceScreen(this.context)
//
//        for (test in arrayOf("Front Camera", "Back Camera", "Sound Sensor")) {
//            val category = PreferenceCategory(this.context).mutate {
//                it.setTitle(test)
//            }
//
//            this.preferenceScreen.addPreference(category)
//
//            category.addPreference(CheckBoxPreference(this.context).mutate {
//                it.key = "check" + test
//                it.setTitle("Expose as Device?")
//                it.summaryOn = "The $test will be exposed as a device. (Requires the $test be present on the Android device)"
//                it.summaryOff = "The $test will not be exposed as a device"
//            })
//
//            category.addPreference(EditTextPreference(this.context).mutate {
//                it.key = "edit" + test
//                it.setTitle("Device Name")
//                it.setSummary("Expose $test as '%s' to target systems")
//                // it.dependency = "check"
//            })
//
//            category.addPreference(ListPreference(this.context).mutate {
//                it.setTitle("Relay for Opening Door")
//                it.setSummary("Relay %s will be activated to open the door")
//                it.entries = arrayOf("value 1", "value 2", "value 3", "value 4")
//                it.entryValues = arrayOf("value 1", "value 2", "value 3", "value 4")
//                //it.setValueIndex(2)
//            })
//
//            category.addPreference(ListPreference(this.context).mutate {
//                it.setTitle("Relay for Closing Door")
//                it.setSummary("Relay %s will be activated to close the door")
//                it.entries = arrayOf("value 1", "value 2", "value 3", "value 4")
//                it.entryValues = arrayOf("value 1", "value 2", "value 3", "value 4")
//                //it.setValueIndex(2)
//            })
//
//            this.findPreference("edit" + test).dependency = "check" + test
//        }
//    }
//}
