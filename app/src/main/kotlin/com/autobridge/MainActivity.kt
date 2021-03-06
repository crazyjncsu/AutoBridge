package com.autobridge

import android.Manifest
import android.app.AlertDialog
import android.app.Fragment
import android.content.ContentResolver
import android.content.Intent
import android.databinding.ObservableArrayList
import android.databinding.ObservableList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.configuration.*
import java.io.File

class MainActivity : NavigationDrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA), 1)

        this.applicationContext.startService(Intent(this, Service::class.java))

        if (this.intent.action == Intent.ACTION_SEND) {
            Toast.makeText(this, "Importing configuration...", Toast.LENGTH_LONG).show()

            val uri = this.intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            this.contentResolver.openInputStream(uri).use { fromStream ->
                File(this.filesDir, CONFIGURATION_FILE_NAME).outputStream().use { toStream ->
                    fromStream.copyTo(toStream)
                }
            }

            this.stopService(Intent(this, Service::class.java))
            this.startService(Intent(this, Service::class.java))

            Toast.makeText(this, "Imported configuration and restarted service.", Toast.LENGTH_LONG).show()
        }
    }

    override fun getMenu(): Int = R.menu.main_drawer

    override fun getDefaultMenuItemId(): Int = R.id.dashboard

    override fun getHeaderViewResource(): Int = R.layout.drawer_header

    override fun createFragmentForMenuItemId(menuItemId: Int): Fragment =
            when (menuItemId) {
                R.id.dashboard -> DashboardFragment()
                R.id.configuration -> ConfigurationFragment()
                R.id.log -> LogFragment()
                else -> throw IllegalArgumentException()
            }
}

class DashboardFragment : Fragment() {
//    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
//        return TextView(this.activity).mutate { it.text = "BOO" }
//    }
}

class ConfigurationFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater!!.inflate(R.layout.configuration, null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        this.setHasOptionsMenu(true)

        deviceContainersGridView.layoutManager = GridLayoutManager(this.activity, 4)
        deviceContainersGridView.adapter = ObservableListAdapter(ObservableArrayList<ConfigurationItem>(), R.layout.device_container_cell, BR.data)

        //deviceContainersGridView.emptyView = deviceContainersEmptyView

        bridgesListView.adapter = ArrayAdapter(this.activity, android.R.layout.simple_list_item_1, mutableListOf<String>())
        bridgesListView.emptyView = bridgesEmptyView

        addDeviceContainerButton.setOnClickListener {
            (deviceContainersGridView.adapter as ObservableListAdapter<ConfigurationItem>).list.add(ConfigurationItem("name", "type"))
        }

        addBridgeButton.setOnClickListener {
            (bridgesListView.adapter as ArrayAdapter<String>).add("hoo")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.configuration, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.importConfiguration -> AlertDialog.Builder(this.activity)
                    .setMessage("Importing needs to be invoked from the application from which you would like to import from. For example, using Google Drive, view the document you'd like to import, then click \"Send a copy\" to select AutoBridge.")
                    .create()
                    .show()
            R.id.exportConfiguration -> this.startActivity(ContentProvider.createChooserIntent("Export Configuration", "/configuration"))
        }

        return true
    }
}

data class ConfigurationItem(val name: String, val type: String)

class LogFragment : Fragment() {
    private val logEntries get() = this.activity.application.to<Application>().logEntries

    private val listChangedCallback = object : ObservableList.OnListChangedCallback<ObservableList<LogEntry>>() {
        override fun onItemRangeInserted(sender: ObservableList<LogEntry>, positionStart: Int, itemCount: Int) =
                this@LogFragment.view.to<RecyclerView>().scrollToPosition(sender.size - 1)

        override fun onChanged(sender: ObservableList<LogEntry>?) {}
        override fun onItemRangeRemoved(sender: ObservableList<LogEntry>?, positionStart: Int, itemCount: Int) {}
        override fun onItemRangeMoved(sender: ObservableList<LogEntry>?, fromPosition: Int, toPosition: Int, itemCount: Int) {}
        override fun onItemRangeChanged(sender: ObservableList<LogEntry>?, positionStart: Int, itemCount: Int) {}
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return RecyclerView(this.activity)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
            inflater.inflate(R.menu.log, menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clearLog -> this.logEntries.clear()
            R.id.shareLog -> this.startActivity(ContentProvider.createChooserIntent("Share Log", "/log"))
        }

        return true
    }

    override fun onStart() {
        super.onStart()

        this.setHasOptionsMenu(true)

        this.view.to<RecyclerView>().apply {
            layoutManager = LinearLayoutManager(this@LogFragment.activity)
            adapter = ObservableListAdapter(this@LogFragment.logEntries, R.layout.log_row, BR.data)
        }

        this.logEntries.addOnListChangedCallback(this.listChangedCallback)
    }

    override fun onStop() {
        this.view.to<RecyclerView>().apply {
            layoutManager = null
            adapter = null // necessary for it to remove handlers
        }

        this.logEntries.removeOnListChangedCallback(this.listChangedCallback)

        super.onStop()
    }
}
