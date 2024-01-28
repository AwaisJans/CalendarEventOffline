package com.jans.calendar.sync.app

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jans.calendar.sync.app.databinding.ActivityMainBinding
import com.jans.calendar.sync.app.databinding.CardViewDesignBinding
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.util.Calendar
import java.util.TimeZone


class MainActivity : AppCompatActivity() {


    lateinit var b: ActivityMainBinding

    private val permissions =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val recyclerview = findViewById<RecyclerView>(R.id.rvEvents)
        recyclerview.layoutManager = LinearLayoutManager(this)
        val data = eventList()
        val adapter = EventsAdapter(data)
        recyclerview.adapter = adapter


        val addUtil: () -> Unit = {
            val calendars = getCalendars()
            val calendarIds = mutableListOf<Long>()
            for (calendar in calendars) {
                Log.d("how123", calendar.accountName.toString())

                calendarIds.add(calendar.calendarId)
            }
            addEvents(1, data)
        }


        b.btnAdd.setOnClickListener {
            permChecker(addUtil)
        }
    }


    private var customDialog: Dialog? = null

    private fun addEvents(calendarId: Long, events: List<CalendarEvent>): List<Boolean> {
        val results = mutableListOf<Boolean>()
        var eventAdded: Boolean?
        for (event in events) {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, event.startDateTime.timeInMillis)
                put(CalendarContract.Events.DTEND, event.endDateTime.timeInMillis)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            eventAdded = uri != null

            results.add(eventAdded)

            val accounts = AccountManager.get(this).accounts
            Log.d("hows123", "Refreshing " + accounts.size + " accounts")
            val authority = CalendarContract.Calendars.CONTENT_URI.authority
            for (i in accounts.indices) {
                Log.d("hows123", "Refreshing calendars for: " + accounts[i])
                val extras = Bundle()
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                ContentResolver.requestSync(accounts[i], authority, extras)
            }

            val pd = ProgressDialog(this)
            pd.setTitle("Adding Events")
            pd.setMessage("Adding Events if not added then check Google Calendar App after 4 to 5 Minutes")

            pd.show()
            Handler(Looper.getMainLooper()).postDelayed({
                pd.dismiss()
                showToast("Event added successfully")
            }, 3000)


        }


//        customDialog = Dialog(this)
//        val dialogB = CustomDialogLayoutBinding.inflate(layoutInflater)
//
//        customDialog?.setContentView(dialogB.root)
//
//        val editText = dialogB.id2
//        val submitButton = dialogB.okBtn
//
//        submitButton.setOnClickListener {
//            // Launch the Google Calendar app using an implicit intent
//            val calendarIntent = Intent(Intent.ACTION_VIEW)
//                .setDataAndType(android.provider.CalendarContract.CONTENT_URI, "time/epoch")
//            startActivity(calendarIntent)
//            val pd = ProgressDialog(this)
//            pd.setTitle("Adding Events")
//            pd.setMessage("Adding Events if not added then check Google Calendar App after 4 to 5 Minutes")
//            val enteredText = editText.text.toString()
//            val accounts = AccountManager.get(this).getAccountsByType("com.google")
//
//            if (enteredText.isBlank()) {
//                val alertDialog = AlertDialog.Builder(this)
//                    .setTitle("Email can not be Empty!")
//                    .setPositiveButton("OK") { _, _ ->
//                    }.create()
//                alertDialog.show()
//            }
//            else if(!enteredText.endsWith("@gmail.com")){
//                val alertDialog = AlertDialog.Builder(this)
//                    .setTitle("Not a valid Email")
//                    .setPositiveButton("OK") { _, _ ->
//                    }.create()
//                alertDialog.show()
//            }
//            else {
//
//                    customDialog?.dismiss()
//                    pd.show()
//                    Handler(Looper.getMainLooper()).postDelayed({
//                        pd.dismiss()
//                        showToast("Event added successfully")
//                    }, 3000)
////                } else {
////                    val alertDialog = AlertDialog.Builder(this)
////                        .setTitle("Email not exist")
////                        .setPositiveButton("OK") { _, _ ->
////                        }.create()
////                    alertDialog.show()
////                }
//
//            }
//
//        }
//
//        customDialog?.show()
        return results
    }


    private fun showToast(msg: String) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("Range")
    fun getCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        val cursor = contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            while (it.moveToNext()) {
                val calendarId = it.getLong(it.getColumnIndex(CalendarContract.Calendars._ID))
                val accountName =
                    it.getString(it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME))
                val displayName =
                    it.getString(it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                val ownerAccount =
                    it.getString(it.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT))

                val calendarInfo =
                    CalendarInfo(calendarId, accountName, displayName, ownerAccount)
                calendars.add(calendarInfo)
                val cr: ContentResolver = contentResolver
                val values = ContentValues()
                values.put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                values.put(CalendarContract.Calendars.VISIBLE, 1)

                cr.update(
                    ContentUris.withAppendedId(uri, calendarId),
                    values,
                    null,
                    null
                )

                Log.d("myCalendarInfo", calendars.toString())


            }
        }

        return calendars
    }

    data class CalendarInfo(
        val calendarId: Long,
        val accountName: String?,
        val displayName: String?,
        val ownerAccount: String?
    )


    private fun permChecker(addUtil: () -> Unit) {
        Dexter.withContext(this)
            .withPermissions(permissions).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        addUtil()
                    } else {
                        val alertDialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Permission Error")
                            .setMessage("App can not run without permissions do grant app permission in setting to use this app")
                            .setPositiveButton("OK") { _, _ ->
                                finish()
                            }.create()
                        alertDialog.show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest?>?,
                    token: PermissionToken?
                ) {
                    token!!.continuePermissionRequest()
                }
            }).check()
    }

    private fun eventList(): List<CalendarEvent> {
        return listOf(
            CalendarEvent(
                "Meeting 1",
                "Discuss project updates",
                "Conference Room 1",
                Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 9, 10, 0) },
                Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 9, 11, 0) }
            ),
            CalendarEvent(
                "Meeting 2",
                "Discuss design changes",
                "Conference Room 2",
                Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 8, 14, 0) },
                Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 8, 15, 0) }
            ),
//            CalendarEvent(
//                "Meeting 3",
//                "Discuss project updates",
//                "Conference Room 3",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 3, 10, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 3, 11, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 4",
//                "Discuss design changes",
//                "Conference Room 4",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 4, 14, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 4, 15, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 5",
//                "Discuss project updates",
//                "Conference Room 5",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 5, 10, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 5, 11, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 6",
//                "Discuss design changes",
//                "Conference Room 6",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 6, 14, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 6, 15, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 7",
//                "Discuss project updates",
//                "Conference Room 7",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 7, 10, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 7, 11, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 8",
//                "Discuss design changes",
//                "Conference Room 8",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 8, 14, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 8, 15, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 9",
//                "Discuss project updates",
//                "Conference Room 9",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 9, 10, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 9, 11, 0) }
//            ),
//            CalendarEvent(
//                "Meeting 10",
//                "Discuss design changes",
//                "Conference Room 10",
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 10, 14, 0) },
//                Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 10, 15, 0) }
//            ),
        )
    }

    data class CalendarEvent(
        val title: String,
        val description: String,
        val location: String,
        val startDateTime: Calendar,
        val endDateTime: Calendar
    )

    class EventsAdapter(private val mList: List<CalendarEvent>) :
        RecyclerView.Adapter<EventsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                CardViewDesignBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ItemsViewModel = mList[position]

            val list1 = listEvents(ItemsViewModel)
            val strMain = "${list1[0]} ${list1[1]} ${list1[2]} ${list1[3]} ${list1[4]}"
            holder.textView.text = strMain
        }

        private fun listEvents(ItemsViewModel: CalendarEvent): List<String> {
            val titleStr = "Title: ${ItemsViewModel.title}\n"
            val descStr = "Description: ${ItemsViewModel.description}\n"
            val locStr = "Location: ${ItemsViewModel.location}\n"
            val startTimeStr = "Start Date & Time: ${ItemsViewModel.startDateTime.time}\n"
            val endTimeStr = "End Date & Time: ${ItemsViewModel.endDateTime.time}"
            return listOf(titleStr, descStr, locStr, startTimeStr, endTimeStr)
        }

        override fun getItemCount(): Int {
            return mList.size
        }

        class ViewHolder(binding: CardViewDesignBinding) : RecyclerView.ViewHolder(binding.root) {
            val textView: TextView = binding.titleTextView
        }
    }
}