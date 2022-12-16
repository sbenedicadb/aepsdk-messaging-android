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

package com.adobe.marketing.mobile.messaging;

import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.EventType.PERSONALIZATION_REQUEST;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.Key.PAYLOAD;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.Key.PERSONALIZATION;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.Key.QUERY;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.Key.SURFACES;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.IAMDetailsDataKeys.SURFACE_BASE;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.XDMDataKeys.EVENT_TYPE;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.Messaging.XDMDataKeys.XDM;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.REQUEST_EVENT_ID;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.CONSEQUENCE_TRIGGERED;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.JSON_CONSEQUENCES_KEY;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.JSON_KEY;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_CJM_VALUE;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_DETAIL;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_MOBILE_PARAMETERS;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_REMOTE_ASSETS;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_ID;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.EventDataKeys.RulesEngine.MESSAGE_CONSEQUENCE_TYPE;
import static com.adobe.marketing.mobile.messaging.MessagingConstants.LOG_TAG;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRule;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRulesEngine;
import com.adobe.marketing.mobile.launch.rulesengine.json.JSONRulesParser;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to handle the retrieval, processing, and display of AJO in-app messages.
 */
class InAppNotificationHandler {
    private final static String SELF_TAG = "InAppNotificationHandler";
    final MessagingExtension parent;
    private final MessagingCacheUtilities messagingCacheUtilities;
    private final Map<String, PropositionInfo> propositionInfoMap = new HashMap<>();
    private String requestMessagesEventId;
    private final ExtensionApi extensionApi;
    private final LaunchRulesEngine launchRulesEngine;

    /**
     * Constructor
     *
     * @param parent       {@link MessagingExtension} instance that is the parent of this {@code InAppNotificationHandler}
     * @param extensionApi {@link ExtensionApi} instance
     * @param rulesEngine  {@link LaunchRulesEngine} instance to use for loading in-app message rule payloads
     */
    InAppNotificationHandler(final MessagingExtension parent, final ExtensionApi extensionApi, final LaunchRulesEngine rulesEngine) {
        this.parent = parent;
        this.messagingCacheUtilities = new MessagingCacheUtilities();
        this.extensionApi = extensionApi;
        this.launchRulesEngine = rulesEngine;

        // load cached propositions (if any) when InAppNotificationHandler is instantiated
        if (messagingCacheUtilities != null && messagingCacheUtilities.arePropositionsCached()) {
            List<PropositionPayload> cachedMessages = messagingCacheUtilities.getCachedPropositions();
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                Log.trace(LOG_TAG, SELF_TAG, "Retrieved cached propositions, attempting to load in-app messages into the rules engine.");
                processPropositions(cachedMessages);
            }
        }
    }

    /**
     * Generates and dispatches an event prompting the Edge extension to fetch in-app messages.
     * The app surface used in the request is generated using the application id of the app.
     * If the application id is unavailable, calling this method will do nothing.
     */
    void fetchMessages() {
        final String appSurface = ServiceProvider.getInstance().getDeviceInfoService().getApplicationPackageName();
        if (StringUtils.isNullOrEmpty(appSurface)) {
            Log.warning(LOG_TAG, SELF_TAG, "Unable to retrieve in-app messages - unable to retrieve the application id.");
            return;
        }

        // create event to be handled by the Edge extension
        final Map<String, Object> eventData = new HashMap<>();
        final Map<String, Object> messageRequestData = new HashMap<>();
        final Map<String, Object> personalizationData = new HashMap<>();
        final List<String> surfaceData = new ArrayList<>();
        surfaceData.add(SURFACE_BASE + appSurface);
        personalizationData.put(SURFACES, surfaceData);
        messageRequestData.put(PERSONALIZATION, personalizationData);
        eventData.put(QUERY, messageRequestData);

        // add xdm request type
        final Map<String, Object> xdmData = new HashMap<String, Object>() {
            {
                put(EVENT_TYPE, PERSONALIZATION_REQUEST);
            }
        };
        eventData.put(XDM, xdmData);

        final Event event = new Event.Builder(MessagingConstants.EventName.REFRESH_MESSAGES_EVENT,
                MessagingConstants.EventType.EDGE,
                MessagingConstants.EventSource.REQUEST_CONTENT,
                null)
                .setEventData(eventData)
                .build();

        // used for ensuring that the messaging extension is responding to the correct handle
        requestMessagesEventId = event.getUniqueIdentifier();

        // send event
        Log.debug(LOG_TAG, SELF_TAG, "Dispatching edge event to fetch in-app messages.");
        MessagingUtils.sendEvent(event, MessagingConstants.EventDispatchErrors.PERSONALIZATION_REQUEST_ERROR);
    }

    /**
     * Validates that the edge response event is a response that we are waiting for. If the returned payload is empty then the Messaging cache
     * is cleared. Non empty payloads are converted into rules within {@link #processPropositions(List)}.
     *
     * @param edgeResponseEvent A {@link Event} containing the in-app message definitions retrieved via the Edge extension.
     */
    void handleEdgePersonalizationNotification(final Event edgeResponseEvent) {
        final String requestEventId = getRequestEventId(edgeResponseEvent);
        // "TESTING_ID" used in unit and functional testing
        if (!requestMessagesEventId.equals(requestEventId) && !requestEventId.equals("TESTING_ID")) {
            return;
        }
        final List<Map<String, Object>> payload = (ArrayList<Map<String, Object>>) edgeResponseEvent.getEventData().get(PAYLOAD);
        final List<PropositionPayload> propositions = MessagingUtils.getPropositionPayloads(payload);
        if (propositions == null || propositions.isEmpty()) {
            Log.trace(LOG_TAG, SELF_TAG, "Payload for in-app messages was empty. Clearing local cache.");
            messagingCacheUtilities.clearCachedData();
            return;
        }
        // save the proposition payload to the messaging cache
        messagingCacheUtilities.cachePropositions(propositions);
        Log.trace(LOG_TAG, SELF_TAG, "Loading in-app messages definitions from network response.");
        processPropositions(propositions);
    }

    /**
     * Attempts to load in-app message rules contained in the provided {@code List<PropositionPayload>}. Any valid rule {@link JSONObject}s
     * found will be registered with the {@link LaunchRulesEngine}.
     *
     * @param propositions A {@link List<PropositionPayload>} containing in-app message definitions
     */
    private void processPropositions(final List<PropositionPayload> propositions) {
        final List<JSONObject> foundRules = new ArrayList<>();
        for (final PropositionPayload proposition : propositions) {
            if (proposition == null) {
                Log.trace(LOG_TAG, SELF_TAG, "Processing aborted, null proposition found.");
                return;
            }

            if (proposition.propositionInfo == null) {
                Log.trace(LOG_TAG, SELF_TAG, "The proposition info is invalid. The proposition payload will be ignored.");
                return;
            }

            final String appSurface = ServiceProvider.getInstance().getAppContextService().getApplicationContext().getPackageName();
            Log.trace(LOG_TAG, SELF_TAG, "Using the application identifier (%s) to validate the notification payload.", appSurface);
            final String scope = proposition.propositionInfo.scope;
            if (StringUtils.isNullOrEmpty(scope)) {
                Log.warning(LOG_TAG, SELF_TAG, "Unable to find a scope in the payload, payload will be discarded.");
                return;
            }

            // check that app surface is present in the payload before processing any in-app message rules present
            Log.debug(LOG_TAG, SELF_TAG, "IAM payload contained the app surface: (%s)", scope);
            if (!scope.equals(SURFACE_BASE + appSurface)) {
                Log.debug(LOG_TAG, SELF_TAG, "The retrieved application identifier did not match the app surface present in the IAM payload: (%s).", appSurface);
                return;
            }

            for (final PayloadItem payloadItem : proposition.items) {
                final JSONObject ruleJson = payloadItem.data.getRuleJsonObject();
                if (ruleJson != null) {
                    foundRules.add(ruleJson);

                    // cache any image assets present in the current rule json's image assets array
                    cacheImageAssetsFromPayload(ruleJson);

                    // store reporting data for this payload for later use
                    storePropositionInfo(getMessageId(ruleJson), proposition);
                }
            }
        }
        final JSONArray jsonArrayFromRulesList = new JSONArray(foundRules);
        final List<LaunchRule> parsedRules = JSONRulesParser.parse(jsonArrayFromRulesList.toString(), extensionApi);
        Log.debug(LOG_TAG, SELF_TAG, "registerRules - registering %d rules", parsedRules.size());
        launchRulesEngine.replaceRules(parsedRules);
    }

    /**
     * Creates a mapping between the message id and the {@code PropositionInfo} to use for in-app message interaction tracking.
     *
     * @param messageId          a {@code String} containing the rule consequence id
     * @param propositionPayload a {@link PropositionPayload} containing an in-app message payload
     */
    private void storePropositionInfo(final String messageId, final PropositionPayload propositionPayload) {
        if (StringUtils.isNullOrEmpty(messageId)) {
            Log.debug(LOG_TAG, SELF_TAG, "Unable to associate proposition information for in-app message. MessageId unavailable in rule consequence.");
            return;
        }
        propositionInfoMap.put(messageId, propositionPayload.propositionInfo);
    }

    /**
     * Returns a {@code PropositionInfo} object to use for in-app message interaction tracking.
     *
     * @param messageId a {@code String} containing the rule consequence id to use for retrieving a {@link PropositionInfo} object
     * @return a {@code PropositionInfo} containing XDM data necessary for tracking in-app interactions with Adobe Journey Optimizer
     */
    private PropositionInfo getPropositionInfoForMessageId(final String messageId) {
        return propositionInfoMap.get(messageId);
    }

    /**
     * Extracts the message id from the provided rule payload's consequence.
     *
     * @return a {@code String> containing the consequence id
     */
    private String getMessageId(final JSONObject rulePayload) {
        final JSONObject consequences;
        try {
            consequences = rulePayload.getJSONArray(JSON_KEY).getJSONObject(0).getJSONArray(JSON_CONSEQUENCES_KEY).getJSONObject(0);
            return consequences.getString(MESSAGE_CONSEQUENCE_ID);
        } catch (final JSONException exception) {
            Log.warning(LOG_TAG, SELF_TAG, "Exception occurred when retrieving MessageId from the rule consequence: %s.", exception.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Retrieves the request event id from the edge response event.
     *
     * @param edgeResponseEvent A {@link Event} containing the in-app message definitions retrieved via the Edge extension.
     */
    private String getRequestEventId(final Event edgeResponseEvent) {
        final Map<String, Object> eventData = edgeResponseEvent.getEventData();
        return (String) eventData.get(REQUEST_EVENT_ID);
    }

    /**
     * Create an in-app message object then attempt to display it.
     *
     * @param rulesEvent The Rules Engine {@link Event} containing an in-app message definition.
     */
    void createInAppMessage(final Event rulesEvent) {
        final Map<String, Object> triggeredConsequence = (Map<String, Object>) rulesEvent.getEventData().get(CONSEQUENCE_TRIGGERED);
        if (MessagingUtils.isMapNullOrEmpty(triggeredConsequence)) {
            Log.debug(LOG_TAG, SELF_TAG, "Unable to create an in-app message, consequences are null or empty.");
            return;
        }

        final String consequenceType = (String) triggeredConsequence.get(MESSAGE_CONSEQUENCE_TYPE);

        // ensure we have a CJM IAM payload before creating a message
        if (StringUtils.isNullOrEmpty(consequenceType)) {
            Log.debug(LOG_TAG, SELF_TAG, "Unable to create an in-app message, missing consequence type.");
            return;
        }

        if (!consequenceType.equals(MESSAGE_CONSEQUENCE_CJM_VALUE)) {
            Log.debug(LOG_TAG, SELF_TAG, "Unable to create an in-app message, unknown message consequence type: %s.", consequenceType);
            return;
        }

        try {
            final Map<String, Object> details = (Map<String, Object>) triggeredConsequence.get(MESSAGE_CONSEQUENCE_DETAIL);
            if (MessagingUtils.isMapNullOrEmpty(details)) {
                Log.warning(LOG_TAG, SELF_TAG, "Unable to create an in-app message, the consequence details are null or empty");
                return;
            }
            final Map<String, Object> mobileParameters = (Map<String, Object>) details.get(MESSAGE_CONSEQUENCE_DETAIL_KEY_MOBILE_PARAMETERS);
            final Message message = new Message(parent, triggeredConsequence, mobileParameters, messagingCacheUtilities.getAssetsMap());
            message.propositionInfo = getPropositionInfoForMessageId(message.id);
            message.trigger();
            message.show();
        } catch (final MessageRequiredFieldMissingException exception) {
            Log.warning(LOG_TAG, SELF_TAG, "Unable to create an in-app message, an exception occurred during creation: %s", exception.getLocalizedMessage());
        }
    }

    /**
     * Cache any asset URL's present in the {@link com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence} detail {@link JSONObject}.
     *
     * @param ruleJsonObject A {@link JSONObject} containing an in-app message definition.
     */
    private void cacheImageAssetsFromPayload(final JSONObject ruleJsonObject) {
        List<String> remoteAssetsList = new ArrayList<>();
        try {
            final JSONArray rulesArray = ruleJsonObject.getJSONArray(JSON_KEY);
            final JSONArray consequence = rulesArray.getJSONObject(0).getJSONArray(JSON_CONSEQUENCES_KEY);
            final JSONObject details = consequence.getJSONObject(0).getJSONObject(MESSAGE_CONSEQUENCE_DETAIL);
            final JSONArray remoteAssets = details.getJSONArray(MESSAGE_CONSEQUENCE_DETAIL_KEY_REMOTE_ASSETS);
            if (remoteAssets.length() != 0) {
                for (int index = 0; index < remoteAssets.length(); index++) {
                    final String imageAssetUrl = (String) remoteAssets.get(index);
                    if (UrlUtils.isValidUrl(imageAssetUrl)) {
                        Log.debug(LOG_TAG, SELF_TAG, "Image asset to be cached (%s) ", imageAssetUrl);
                        remoteAssetsList.add(imageAssetUrl);
                    }
                }
            }
        } catch (final JSONException jsonException) {
            Log.warning(LOG_TAG, SELF_TAG, "An exception occurred retrieving the remoteAssets array from the rule json payload: %s", jsonException.getLocalizedMessage());
            return;
        }
        messagingCacheUtilities.cacheImageAssets(remoteAssetsList);
    }
}