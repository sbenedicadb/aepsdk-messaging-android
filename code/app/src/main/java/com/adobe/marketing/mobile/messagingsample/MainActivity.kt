/*
 Copyright 2021 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.messagingsample

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.adobe.marketing.mobile.*
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val customMessagingDelegate = CustomDelegate()
    private lateinit var spinner: Spinner
    private var count = 0;
    private var triggerKey = "key"
    private var triggerValue = "value"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MobileCore.setFullscreenMessageDelegate(customMessagingDelegate)
        customMessagingDelegate.autoTrack = true

        spinner = findViewById(R.id.iamTypeSpinner)
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            this,
            R.array.iam_types_array2,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // spinner handling
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (parent.getItemAtPosition(pos).equals("Banner IAM")) {
                    triggerKey = "ryan"
                    triggerValue = "banner"
                } else if (parent.getItemAtPosition(pos).equals("Foo bar iam")) {
                    triggerKey = "foo"
                    triggerValue = "bar"
                } else if (parent.getItemAtPosition(pos).equals("Ryan test no advanced settings")) {
                    triggerKey = "user"
                    triggerValue = "ryan"
                } else if (parent.getItemAtPosition(pos).equals("Ryan test with advanced settings")) {
                    triggerKey = "ryan"
                    triggerValue = "test2"
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // no-op
            }

        }

        btnGetLocalNotification.setOnClickListener {
            scheduleNotification(getNotification("Click on the notification for tracking"), 1000)
        }
        btnTriggerFullscreenIAM.setOnClickListener {
            val eventData = HashMap<String, Any>()
            eventData.put(triggerKey, triggerValue)

            val iamTrigger = Event.Builder(
                "test",
                "iamtest", "iamtest"
            )
                .setEventData(eventData)
                .build()
            MobileCore.dispatchEvent(iamTrigger, null)
        }

        allowIAMSwitch.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Fullscreen IAM enabled" else "Fullscreen IAM disabled"
            Toast.makeText(
                this@MainActivity, message,
                Toast.LENGTH_SHORT
            ).show()
            customMessagingDelegate.showMessages = isChecked
        }

        btnTriggerLastIAM.setOnClickListener {
            Toast.makeText(
                this@MainActivity, "Showing last message.",
                Toast.LENGTH_SHORT
            ).show()
            customMessagingDelegate.getLastTriggeredMessage()?.show()
        }
        btnCleanEventHistory.setOnClickListener {
            Messaging.refreshInAppMessages()
        }

        intent?.extras?.apply {
            if (getString(FROM) == "action") {
                Messaging.handleNotificationResponse(intent, true, "button")
            } else {
                Messaging.handleNotificationResponse(intent, true, null)
            }
        }
    }

    private fun scheduleNotification(notification: Notification?, delay: Int) {
        val notificationIntent = Intent(this, NotificationBroadcastReceiver::class.java)
        notificationIntent.putExtra(NotificationBroadcastReceiver.NOTIFICATION_ID, 1)
        notificationIntent.putExtra(NotificationBroadcastReceiver.NOTIFICATION, notification)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val futureInMillis = SystemClock.elapsedRealtime() + delay
        val alarmManager = (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        alarmManager[AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis] = pendingIntent
    }

    private fun getNotification(content: String): Notification? {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, "default")
        builder.setContentTitle("Scheduled Notification")
        builder.setContentText(content)
        builder.setSmallIcon(R.drawable.ic_launcher_background)
        builder.setAutoCancel(true)
        builder.setChannelId("10001")
        val actionReceiver = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(FROM, "action")
            Messaging.addPushTrackingDetails(this, "messageId", NotificationBroadcastReceiver.XDM_DATA)
        }
        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(this, System.currentTimeMillis().toInt(), actionReceiver, 0)
        builder.addAction(R.drawable.ic_launcher_background, "buttonAction",
            pendingIntent)
        return builder.build()
    }

    companion object {
        const val FROM = "from"
    }

    fun generateAndDispatchEdgeResponseEvent(height: Int, width: Int, vAlign: String, vInset: Int, hAlign: String, hInset: Int, displayAnimation: String, dismissAnimation: String, colorString: String, opacity: Double, cornerRadius: Double, uiTakeover: Boolean) {
        // simulate edge response event containing offers
        val eventData = HashMap<String, Any>()
        eventData.put("type", "personalization:decisions")
        eventData.put("requestEventId", "2E964037-E319-4D14-98B8-0682374E547B")
        val payload = JSONObject("{\n" +
                "  \"activity\": {\n" +
                "    \"id\": \"xcore:offer-activity:14090235e6b6757a\",\n" +
                "    \"etag\": \"9\"\n" +
                "  },\n" +
                "  \"id\": \"57d8f990-734a-4d7b-a7e7-739b03753226\",\n" +
                "  \"scope\": \"eyJhY3Rpdml0eUlkIjoieGNvcmU6b2ZmZXItYWN0aXZpdHk6MTQwOTAyMzVlNmI2NzU3YSIsInBsYWNlbWVudElkIjoieGNvcmU6b2ZmZXItcGxhY2VtZW50OjE0MGExNzZmMjcyZWU2NTEiLCJpdGVtQ291bnQiOjMwfQ==\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"id\": \"xcore:personalized-offer:140f858533497db9\",\n" +
                "      \"data\": {\n" +
                "        \"characteristics\": {\n" +
                "          \"inappmessageExecutionId\": \"dummy_message_execution_id_7a237b33-2648-4903-82d1-efa1aac7c60d-all-visitors-everytime\"\n" +
                "        },\n" +
                "        \"id\": \"xcore:personalized-offer:140f858533497db9\",\n" +
                "        \"content\": \"{\\\"version\\\":1,\\\"rules\\\":[{\\\"condition\\\":{\\\"type\\\":\\\"group\\\",\\\"definition\\\":{\\\"conditions\\\":[{\\\"definition\\\":{\\\"key\\\":\\\"foo\\\",\\\"matcher\\\":\\\"eq\\\",\\\"values\\\":[\\\"bar\\\"]},\\\"type\\\":\\\"matcher\\\"}],\\\"logic\\\":\\\"and\\\"}},\\\"consequences\\\":[{\\\"id\\\":\\\"e56a8dbb-c5a4-4219-9563-0496e00b4083\\\",\\\"type\\\":\\\"cjmiam\\\",\\\"detail\\\":{\\\"mobileParameters\\\":{\\\"verticalAlign\\\":"+vAlign+"\",\\\"horizontalInset\\\":"+hInset+",\\\"dismissAnimation\\\":\\\""+dismissAnimation+"\\\",\\\"uiTakeover\\\":"+uiTakeover+",\\\"horizontalAlign\\\":\\\""+hAlign+"\\\",\\\"verticalInset\\\":"+vInset+",\\\"displayAnimation\\\":\\\""+displayAnimation+"\\\",\\\"width\\\":"+width+",\\\"height\\\":"+height+",\\\"gestures\\\":{}},\\\"html\\\":\\\"<html>\\\\n<head>\\\\n\\\\t<style>\\\\n\\\\t\\\\thtml,\\\\n\\\\t\\\\tbody {\\\\n\\\\t\\\\t\\\\tmargin: 0;\\\\n\\\\t\\\\t\\\\tpadding: 0;\\\\n\\\\t\\\\t\\\\ttext-align: center;\\\\n\\\\t\\\\t\\\\twidth: 100%;\\\\n\\\\t\\\\t\\\\theight: 100%;\\\\n\\\\t\\\\t\\\\tfont-family: adobe-clean, \\\\\\\"Source Sans Pro\\\\\\\", -apple-system, BlinkMacSystemFont, \\\\\\\"Segoe UI\\\\\\\", Roboto, sans-serif;\\\\n\\\\t\\\\t}\\\\n\\\\n    h3 {\\\\n\\\\t\\\\t\\\\tmargin: .1rem auto;\\\\n\\\\t\\\\t}\\\\n\\\\t\\\\tp {\\\\n\\\\t\\\\t\\\\tmargin: 0;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.body {\\\\n\\\\t\\\\t\\\\tdisplay: flex;\\\\n\\\\t\\\\t\\\\tflex-direction: column;\\\\n\\\\t\\\\t\\\\tbackground-color: #"+colorString+";\\\\n\\\\t\\\\t\\\\tborder-radius: 5px;\\\\n\\\\t\\\\t\\\\tcolor: #333333;\\\\n\\\\t\\\\t\\\\twidth: 100vw;\\\\n\\\\t\\\\t\\\\theight: 100vh;\\\\n\\\\t\\\\t\\\\ttext-align: center;\\\\n\\\\t\\\\t\\\\talign-items: center;\\\\n\\\\t\\\\t\\\\tbackground-size: 'cover';\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.content {\\\\n\\\\t\\\\t\\\\twidth: 100%;\\\\n\\\\t\\\\t\\\\theight: 100%;\\\\n\\\\t\\\\t\\\\tdisplay: flex;\\\\n\\\\t\\\\t\\\\tjustify-content: center;\\\\n\\\\t\\\\t\\\\tflex-direction: column;\\\\n\\\\t\\\\t\\\\tposition: relative;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\ta {\\\\n\\\\t\\\\t\\\\ttext-decoration: none;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.image {\\\\n\\\\t\\\\t  height: 1rem;\\\\n\\\\t\\\\t  flex-grow: 4;\\\\n\\\\t\\\\t  flex-shrink: 1;\\\\n\\\\t\\\\t  display: flex;\\\\n\\\\t\\\\t  justify-content: center;\\\\n\\\\t\\\\t  width: 90%;\\\\n      flex-direction: column;\\\\n      align-items: center;\\\\n\\\\t\\\\t}\\\\n    .image img {\\\\n      max-height: 100%;\\\\n      max-width: 100%;\\\\n    }\\\\n\\\\n\\\\t\\\\t.text {\\\\n\\\\t\\\\t\\\\ttext-align: center;\\\\n\\\\t\\\\t\\\\tline-height: 20px;\\\\n\\\\t\\\\t\\\\tfont-size: 14px;\\\\n\\\\t\\\\t\\\\tcolor: #333333;\\\\n\\\\t\\\\t\\\\tpadding: 0 25px;\\\\n\\\\t\\\\t\\\\tline-height: 1.25rem;\\\\n\\\\t\\\\t\\\\tfont-size: 0.875rem;\\\\n\\\\t\\\\t}\\\\n\\\\t\\\\t.title {\\\\n\\\\t\\\\t\\\\tline-height: 1.3125rem;\\\\n\\\\t\\\\t\\\\tfont-size: 1.025rem;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.buttons {\\\\n\\\\t\\\\t\\\\twidth: 100%;\\\\n\\\\t\\\\t\\\\tdisplay: flex;\\\\n\\\\t\\\\t\\\\tflex-direction: column;\\\\n\\\\t\\\\t\\\\tfont-size: 1rem;\\\\n\\\\t\\\\t\\\\tline-height: 1.3rem;\\\\n\\\\t\\\\t\\\\ttext-decoration: none;\\\\n\\\\t\\\\t\\\\ttext-align: center;\\\\n\\\\t\\\\t\\\\tbox-sizing: border-box;\\\\n\\\\t\\\\t\\\\tpadding: .8rem;\\\\n\\\\t\\\\t\\\\tpadding-top: .4rem;\\\\n\\\\t\\\\t\\\\tgap: 0.3125rem;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.button {\\\\n\\\\t\\\\t\\\\tflex-grow: 1;\\\\n\\\\t\\\\t\\\\tbackground-color: #1473E6;\\\\n\\\\t\\\\t\\\\tcolor: #FFFFFF;\\\\n\\\\t\\\\t\\\\tborder-radius: "+cornerRadius+"rem;\\\\n\\\\t\\\\t\\\\tcursor: pointer;\\\\n\\\\t\\\\t\\\\tpadding: .3rem;\\\\n\\\\t\\\\t\\\\tgap: .5rem;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.btnClose {\\\\n\\\\t\\\\t\\\\tcolor: #000000;\\\\n\\\\t\\\\t}\\\\n\\\\n\\\\t\\\\t.closeBtn {\\\\n\\\\t\\\\t\\\\talign-self: flex-end;\\\\n\\\\t\\\\t\\\\twidth: 1.8rem;\\\\n\\\\t\\\\t\\\\theight: 1.8rem;\\\\n\\\\t\\\\t\\\\tmargin-top: 1rem;\\\\n\\\\t\\\\t\\\\tmargin-right: .3rem;\\\\n\\\\t\\\\t}\\\\n\\\\t</style>\\\\n\\\\t<style type=\\\\\\\"text/css\\\\\\\" id=\\\\\\\"editor-styles\\\\\\\">\\\\n[data-uuid=\\\\\\\"e69adbda-8cfc-4c51-bcab-f8b2fd314d8f\\\\\\\"]  {\\\\n  flex-direction: row !important;\\\\n}\\\\n</style>\\\\n</head>\\\\n\\\\n<body>\\\\n\\\\t<div class=\\\\\\\"body\\\\\\\">\\\\n    <div class=\\\\\\\"closeBtn\\\\\\\" data-btn-style=\\\\\\\"plain\\\\\\\" data-uuid=\\\\\\\"fbc580c2-94c1-43c8-a46b-f66bb8ca8554\\\\\\\">\\\\n  <a class=\\\\\\\"btnClose\\\\\\\" href=\\\\\\\"adbinapp://cancel\\\\\\\">\\\\n    <svg xmlns=\\\\\\\"http://www.w3.org/2000/svg\\\\\\\" height=\\\\\\\"18\\\\\\\" viewbox=\\\\\\\"0 0 18 18\\\\\\\" width=\\\\\\\"18\\\\\\\" class=\\\\\\\"close\\\\\\\">\\\\n  <rect id=\\\\\\\"Canvas\\\\\\\" fill=\\\\\\\"#ffffff\\\\\\\" opacity=\\\\\\\""+opacity+"\\\\\\\" width=\\\\\\\"18\\\\\\\" height=\\\\\\\"18\\\\\\\" />\\\\n  <path fill=\\\\\\\"currentColor\\\\\\\" xmlns=\\\\\\\"http://www.w3.org/2000/svg\\\\\\\" d=\\\\\\\"M13.2425,3.343,9,7.586,4.7575,3.343a.5.5,0,0,0-.707,0L3.343,4.05a.5.5,0,0,0,0,.707L7.586,9,3.343,13.2425a.5.5,0,0,0,0,.707l.707.7075a.5.5,0,0,0,.707,0L9,10.414l4.2425,4.243a.5.5,0,0,0,.707,0l.7075-.707a.5.5,0,0,0,0-.707L10.414,9l4.243-4.2425a.5.5,0,0,0,0-.707L13.95,3.343a.5.5,0,0,0-.70711-.00039Z\\\\\\\" />\\\\n</svg>\\\\n  </a>\\\\n</div><div class=\\\\\\\"image\\\\\\\" data-uuid=\\\\\\\"88c0afd9-1e3d-4d52-900f-fa08a56ad5e2\\\\\\\">\\\\n<img src=\\\\\\\"https://d2sqqyy3wk3mtg.cloudfront.net/6d123ed0-79c8-40bb-923e-d079c810bb68/urn:aaid:aem:f42f6d80-433f-4811-bda2-7fb42d17aa8f/oak:1.0::ci:c33d550028ccaba70c8208b42ae86ae7/c44127de-e0de-3436-9866-7673da9a798a\\\\\\\" alt=\\\\\\\"\\\\\\\">\\\\n</div><div class=\\\\\\\"text\\\\\\\" data-uuid=\\\\\\\"e7e49562-a365-4d38-be0e-d69cba829cb4\\\\\\\">\\\\n<h3>In App for the dogs</h3>\\\\n<p>For E2E testing!</p>\\\\n</div><div data-uuid=\\\\\\\"e69adbda-8cfc-4c51-bcab-f8b2fd314d8f\\\\\\\" class=\\\\\\\"buttons\\\\\\\">\\\\n  <a class=\\\\\\\"button\\\\\\\" data-uuid=\\\\\\\"864bd990-fc7f-4b1a-8d45-069c58981eb2\\\\\\\" href=\\\\\\\"adbinapp://dismiss\\\\\\\">Dismiss</a>\\\\n</div>\\\\n\\\\t</div>\\\\n\\\\n\\\\n</body></html>\\\",\\\"_xdm\\\":{\\\"mixins\\\":{\\\"_experience\\\":{\\\"customerJourneyManagement\\\":{\\\"messageExecution\\\":{\\\"messageExecutionID\\\":\\\"dummy_message_execution_id_7a237b33-2648-4903-82d1-efa1aac7c60d-all-visitors-everytime\\\",\\\"messageID\\\":\\\"fb31245e-3382-4b3a-a15a-dcf11f463020\\\",\\\"messagePublicationID\\\":\\\"aafe8df1-9c30-496d-ba0c-4b300f8cbabb\\\",\\\"ajoCampaignID\\\":\\\"7a237b33-2648-4903-82d1-efa1aac7c60d\\\",\\\"ajoCampaignVersionID\\\":\\\"38bd427b-f48e-4b3e-a4e1-03033b830d66\\\"},\\\"messageProfile\\\":{\\\"channel\\\":{\\\"_id\\\":\\\"https://ns.adobe.com/xdm/channels/inapp\\\"}}}}}}}}]}]}\",\n" +
                "        \"format\": \"application/json\"\n" +
                "      },\n" +
                "      \"etag\": \"1\",\n" +
                "      \"schema\": \"https://ns.adobe.com/experience/offer-management/content-component-json\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"placement\": {\n" +
                "    \"id\": \"xcore:offer-placement:140a176f272ee651\",\n" +
                "    \"etag\": \"1\"\n" +
                "  }\n" +
                "}")
        eventData.put("payload", payload)
        eventData.put("requestId", "D158979E-0506-4968-8031-17A6A8A87DA8")

        val edgeResponseEvent = Event.Builder(
            "AEP Response Event Handle",
            "com.adobe.eventType.edge", "personalization:decisions"
        )
            .setEventData(eventData)
            .build()
        MobileCore.dispatchEvent(edgeResponseEvent, null)
        // increment count to update message id
        // count++
    }
}

class CustomDelegate: MessageDelegate() {
    private var currentMessage: UIService.FullscreenMessage? = null
    var showMessages = true

    override fun shouldShowMessage(fullscreenMessage: UIService.FullscreenMessage?): Boolean {
        if(!showMessages) {
            if (fullscreenMessage != null) {
                this.currentMessage = fullscreenMessage
                val message = (currentMessage?.settings as? AEPMessageSettings)?.parent as? Message
                println("message was suppressed: ${message?.messageId}")
                message?.track("message suppressed")
            }
        }
        return showMessages
    }

    override fun onShow(fullscreenMessage: UIService.FullscreenMessage?) {
        this.currentMessage = fullscreenMessage
    }

    override fun onDismiss(fullscreenMessage: UIService.FullscreenMessage?) {
        this.currentMessage = fullscreenMessage
    }

    fun getLastTriggeredMessage(): UIService.FullscreenMessage? {
        return currentMessage
    }
}