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
package com.adobe.marketing.mobile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import org.junit.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Util class used by both Functional and Unit tests
 */
public class MessagingTestUtils {
    private static final String LOG_TAG = "MessagingTestUtils";
    private static final String REMOTE_URL = "https://www.adobe.com/adobe.png";
    private static final int STREAM_WRITE_BUFFER_SIZE = 4096;
    private static CacheManager cacheManager;

    /**
     * Serialize the given {@code map} to a JSON Object, then flattens to {@code Map<String, String>}.
     * For example, a JSON such as "{xdm: {stitchId: myID, eventType: myType}}" is flattened
     * to two map elements "xdm.stitchId" = "myID" and "xdm.eventType" = "myType".
     *
     * @param map map with JSON structure to flatten
     * @return new map with flattened structure
     */
    static Map<String, String> flattenMap(final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            JSONObject jsonObject = new JSONObject(map);
            Map<String, String> payloadMap = new HashMap<>();
            addKeys("", new ObjectMapper().readTree(jsonObject.toString()), payloadMap);
            return payloadMap;
        } catch (IOException e) {
            MobileCore.log(LoggingMode.ERROR, LOG_TAG, "Failed to parse JSON object to tree structure.");
        }

        return Collections.emptyMap();
    }

    /**
     * Deserialize {@code JsonNode} and flatten to provided {@code map}.
     * For example, a JSON such as "{xdm: {stitchId: myID, eventType: myType}}" is flattened
     * to two map elements "xdm.stitchId" = "myID" and "xdm.eventType" = "myType".
     * <p>
     * Method is called recursively. To use, call with an empty path such as
     * {@code addKeys("", new ObjectMapper().readTree(JsonNodeAsString), map);}
     *
     * @param currentPath the path in {@code JsonNode} to process
     * @param jsonNode    {@link JsonNode} to deserialize
     * @param map         {@code Map<String, String>} instance to store flattened JSON result
     * @see <a href="https://stackoverflow.com/a/24150263">Stack Overflow post</a>
     */
    private static void addKeys(String currentPath, JsonNode jsonNode, Map<String, String> map) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
            String pathPrefix = currentPath.isEmpty() ? "" : currentPath + ".";

            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                addKeys(pathPrefix + entry.getKey(), entry.getValue(), map);
            }
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;

            for (int i = 0; i < arrayNode.size(); i++) {
                addKeys(currentPath + "[" + i + "]", arrayNode.get(i), map);
            }
        } else if (jsonNode.isValueNode()) {
            ValueNode valueNode = (ValueNode) jsonNode;
            map.put(currentPath, valueNode.asText());
        }
    }

    /**
     * Dispatches a simulated edge response event containing a message payload. The message payload
     * is loaded from the resources directory using the passed in {@code String} as a filename.
     *
     * @param fileName the {@code String} name of a file located in the resource directory
     */
    public static void dispatchEdgePersonalizationEventWithMessagePayload(final String fileName) {
        final Map<String, Object> eventData = new HashMap();
        final List<Map<String, Object>> items = new ArrayList<>();
        items.add(getMapFromFile(fileName));
        eventData.put("payload", items);
        final Event event = new Event.Builder("edge response testing", MessagingTestConstants.EventType.EDGE, MessagingTestConstants.EventSource.PERSONALIZATION_DECISIONS)
                .setEventData(eventData)
                .build();
        MobileCore.dispatchEvent(event, new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {
                Log.debug(LOG_TAG, "exception occurred in dispatching edge personalization event: %s", extensionError.getErrorName());
            }
        });
    }

    /**
     * Converts a file containing a JSON into a {@link Map<String, Variant>}.
     *
     * @param fileName the {@code String} name of a file located in the resource directory
     * @return a {@code Map<String, Variant>} containing the JSON's contents
     */
    static Map<String, Variant> getVariantMapFromFile(final String fileName) {
        try {
            final JSONObject json = new JSONObject(loadStringFromFile(fileName));
            return toVariantMap(json);
        } catch (final JSONException jsonException) {
            Log.warning(LOG_TAG, "getVariantMapFromFile() - Exception occurred when creating the JSONObject: %s", jsonException.getMessage());
            return null;
        }
    }

    /**
     * Converts a file containing a JSON into a {@link Map<String, Object>}.
     *
     * @param fileName the {@code String} name of a file located in the resource directory
     * @return a {@code Map<String, Object>} containing the file's contents
     */
    static Map<String, Object> getMapFromFile(final String fileName) {
        try {
            final JSONObject json = new JSONObject(loadStringFromFile(fileName));
            return toMap(json);
        } catch (final JSONException jsonException) {
            Log.warning(LOG_TAG, "getMapFromFile() - Exception occurred when creating the JSONObject: %s", jsonException.getMessage());
            return null;
        }
    }

    /**
     * Converts a file into a {@code String}.
     *
     * @param fileName the {@code String} name of a file located in the resource directory
     * @return a {@code String} containing the file's contents
     */
    public static String loadStringFromFile(final String fileName) {
        final InputStream inputStream = convertResourceFileToInputStream(fileName);
        try {
            if (inputStream != null) {
                final String streamContents = StringUtils.streamToString(inputStream);
                return streamContents;
            } else {
                return null;
            }
        } finally {
            try {
                inputStream.close();
            } catch (final IOException ioException) {
                Log.warning(LOG_TAG, "Exception occurred when closing the input stream: %s", ioException.getMessage());
                return null;
            }
        }
    }

    /**
     * Cleans Messaging extension payload and image asset cache files.
     */
    static void cleanCache() {
        final SystemInfoService systemInfoService = MessagingTestUtils.getPlatformServices().getSystemInfoService();
        try {
            cacheManager = new CacheManager(systemInfoService);
        } catch (final MissingPlatformServicesException exception) {
            Log.warning(LOG_TAG, "Error clearing cache: %s", exception.getMessage());
        }
        cacheManager.deleteFilesNotInList(null, MessagingTestConstants.IMAGES_CACHE_SUBDIRECTORY);
        cacheManager.deleteFilesNotInList(null, MessagingTestConstants.PROPOSITIONS_CACHE_SUBDIRECTORY);
    }

    /**
     * Adds a test image to the Messaging extension image asset cache.
     */
    static void addImageAssetToCache() {
        final File mockCachedImage = cacheManager.createNewCacheFile(REMOTE_URL, MessagingTestConstants.IMAGES_CACHE_SUBDIRECTORY, new Date());
        writeInputStreamIntoFile(mockCachedImage, convertResourceFileToInputStream("adobe.png"), false);
    }

    /**
     * Converts a file in the resources directory into an {@link InputStream}.
     *
     * @param filename the {@code String} filename of a file located in the resource directory
     * @return a {@code InputStream} of the specified file
     */
    static InputStream convertResourceFileToInputStream(final String filename) {
        return MessagingTestUtils.class.getClassLoader().getResourceAsStream(filename);
    }

    /**
     * Writes the contents of an {@link InputStream} into a file.
     *
     * @param cachedFile  the {@code File} to be written to
     * @param inputStream a {@code InputStream} containing the data to be written
     * @return a {@code boolean} if the write to file was successful
     */
    static boolean writeInputStreamIntoFile(final File cachedFile, final InputStream inputStream, final boolean append) {
        boolean result = false;

        if (cachedFile == null || inputStream == null) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException ioException) {
                    Log.debug(LOG_TAG, "Exception occurred when closing input stream: %s", ioException.getMessage());
                }
            }
            return result;
        }

        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(cachedFile, append);
            final byte[] data = new byte[STREAM_WRITE_BUFFER_SIZE];
            int count;

            while ((count = inputStream.read(data)) != -1) {
                outputStream.write(data, 0, count);
                outputStream.flush();
            }
            result = true;
        } catch (final IOException e) {
            Log.error(LOG_TAG, "IOException while attempting to write to file (%s)", e);
        } catch (final Exception e) {
            Log.error(LOG_TAG, "Unexpected exception while attempting to write to file (%s)", e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }

            } catch (final Exception e) {
                Log.error(LOG_TAG, "Unable to close the OutputStream (%s) ", e);
            }
        }

        return result;
    }

    /**
     * Set the persistence data for Edge Identity extension.
     */
    static void setEdgeIdentityPersistence(final Map<String, Object> persistedData, final Application application) {
        if (persistedData != null) {
            final JSONObject persistedJSON = new JSONObject(persistedData);
            updatePersistence("com.adobe.edge.identity",
                    "identity.properties", persistedJSON.toString(), application);
        }
    }

    /**
     * Helper method to update the {@link SharedPreferences} data.
     *
     * @param datastore   the name of the datastore to be updated
     * @param key         the persisted data key that has to be updated
     * @param value       the new value
     * @param application the current test application
     */
    public static void updatePersistence(final String datastore, final String key, final String value, final Application application) {
        if (application == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. Application is null, fast failing the test case.");
        }

        final Context context = application.getApplicationContext();

        if (context == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. Context is null, fast failing the test case.");
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(datastore, Context.MODE_PRIVATE);

        if (sharedPreferences == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. sharedPreferences is null, fast failing the test case.");
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    static Map<String, Object> createIdentityMap(final String namespace, final String id) {
        Map<String, Object> namespaceObj = new HashMap<>();
        namespaceObj.put("authenticationState", "ambiguous");
        namespaceObj.put("id", id);
        namespaceObj.put("primary", false);

        List<Map<String, Object>> namespaceIds = new ArrayList<>();
        namespaceIds.add(namespaceObj);

        Map<String, List<Map<String, Object>>> identityMap = new HashMap<>();
        identityMap.put(namespace, namespaceIds);

        Map<String, Object> xdmMap = new HashMap<>();
        xdmMap.put("identityMap", identityMap);

        return xdmMap;
    }

    static void waitForExecutor(ExecutorService executor, int executorTime) {
        Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                // Fake task to check the execution termination
            }
        });

        try {
            future.get(executorTime, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assert.fail(String.format("Executor took longer than %d (sec)", executorTime));
        }
    }

    static List<Map> generateMessagePayload(final MessageTestConfig config) {
        if (config.count <= 0) {
            return null;
        }
        ArrayList<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> scopeDetails = new HashMap<>();
        int count;
        for (count = 0; count < config.count; count++) {
            Map<String, Object> item = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> characteristics = new HashMap<>();
            Map<String, Object> cjmEvent = new HashMap<>();
            Map<String, Object> messageExecution = new HashMap<>();
            item.put("schema", "https://ns.adobe.com/experience/personalization/json-content-item");
            item.put("id", "testItemId" + count);
            messageExecution.put("messageExecutionID", "testExecutionId");
            cjmEvent.put("messageExecution", messageExecution);
            characteristics.put("cjmEvent", cjmEvent);
            scopeDetails.put("scopeDetails", characteristics);
            data.put("content","{\"version\": 1 , " + (config.isMissingRulesKey ? "\"invalid\"" : "\"rules\"") + ": [{\"condition\":{\"type\":\"matcher\",\"definition\":{\"key\":\"isLoggedIn" + count + "\",\"matcher\":\"eq\",\"values\":[\"true\"]}},\"consequences\":[{" + (config.isMissingMessageId ? "" : "\"id\":\"fa99415e-dc8b-478a-84d2-21f67d13e866\",") + (config.isMissingMessageType ? "" : "\"type\":\"cjmiam\",") + (config.isMissingMessageDetail ? "" : "\"detail\":{\"mobileParameters\":{\"schemaVersion\":\"0.0.1\",\"width\":100,\"height\":100,\"verticalAlign\":\"center\",\"verticalInset\":0,\"horizontalAlign\":\"center\",\"horizontalInset\":0,\"uiTakeover\":true,\"displayAnimation\":\"bottom\",\"dismissAnimation\":\"bottom\",\"gestures\":{\"swipeDown\":\"adbinapp://dismiss?interaction=swipeDown\",\"swipeUp\":\"adbinapp://dismiss?interaction=swipeUp\"}},") + (config.hasHtmlPayloadMissing ? "" : "\"html\":\"<html>\\n<head>\\n\\t<meta name=\\\"viewport\\\" content=\\\"width=device-width, initial-scale=1.0\\\">\\n\\t<style>\\n\\t\\thtml,\\n\\t\\tbody {\\n\\t\\t\\tmargin: 0;\\n\\t\\t\\tpadding: 0;\\n\\t\\t\\ttext-align: center;\\n\\t\\t\\twidth: 100%;\\n\\t\\t\\theight: 100%;\\n\\t\\t\\tfont-family: adobe-clean, \\\"Source Sans Pro\\\", -apple-system, BlinkMacSystemFont, \\\"Segoe UI\\\", Roboto, sans-serif;\\n\\t\\t}\\n\\n\\t\\t.body {\\n\\t\\t\\tdisplay: flex;\\n\\t\\t\\tflex-direction: column;\\n\\t\\t\\tbackground-color: #121c3e;\\n\\t\\t\\tborder-radius: 5px;\\n\\t\\t\\tcolor: #333333;\\n\\t\\t\\twidth: 100vw;\\n\\t\\t\\theight: 100vh;\\n\\t\\t\\ttext-align: center;\\n\\t\\t\\talign-items: center;\\n\\t\\t\\tbackground-size: 'cover';\\n\\t\\t}\\n\\n\\t\\t.content {\\n\\t\\t\\twidth: 100%;\\n\\t\\t\\theight: 100%;\\n\\t\\t\\tdisplay: flex;\\n\\t\\t\\tjustify-content: center;\\n\\t\\t\\tflex-direction: column;\\n\\t\\t\\tposition: relative;\\n\\t\\t}\\n\\n\\t\\ta {\\n\\t\\t\\ttext-decoration: none;\\n\\t\\t}\\n\\n\\t\\t.image {\\n\\t\\t  height: 1rem;\\n\\t\\t  flex-grow: 4;\\n\\t\\t  flex-shrink: 1;\\n\\t\\t  display: flex;\\n\\t\\t  justify-content: center;\\n\\t\\t  width: 90%;\\n      flex-direction: column;\\n      align-items: center;\\n\\t\\t}\\n    .image img {\\n      max-height: 100%;\\n      max-width: 100%;\\n    }\\n\\n\\t\\t.btnClose {\\n\\t\\t\\tcolor: #000000;\\n\\t\\t}\\n\\n\\t\\t.closeBtn {\\n\\t\\t\\talign-self: flex-end;\\n\\t\\t\\twidth: 1.8rem;\\n\\t\\t\\theight: 1.8rem;\\n\\t\\t\\tmargin-top: 1rem;\\n\\t\\t\\tmargin-right: .3rem;\\n\\t\\t}\\n\\t</style>\\n</head>\\n\\n<body>\\n\\t<div class=\\\"body\\\">\\n    <div class=\\\"closeBtn\\\" data-btn-style=\\\"plain\\\" data-uuid=\\\"3de6f6ef-f98b-4981-9530-b3c47ae6984d\\\">\\n  <a class=\\\"btnClose\\\" href=\\\"adbinapp://dismiss?interaction=cancel\\\">\\n    <svg xmlns=\\\"http://www.w3.org/2000/svg\\\" height=\\\"18\\\" viewbox=\\\"0 0 18 18\\\" width=\\\"18\\\" class=\\\"close\\\">\\n  <rect id=\\\"Canvas\\\" fill=\\\"#ffffff\\\" opacity=\\\"0\\\" width=\\\"18\\\" height=\\\"18\\\" />\\n  <path fill=\\\"currentColor\\\" xmlns=\\\"http://www.w3.org/2000/svg\\\" d=\\\"M13.2425,3.343,9,7.586,4.7575,3.343a.5.5,0,0,0-.707,0L3.343,4.05a.5.5,0,0,0,0,.707L7.586,9,3.343,13.2425a.5.5,0,0,0,0,.707l.707.7075a.5.5,0,0,0,.707,0L9,10.414l4.2425,4.243a.5.5,0,0,0,.707,0l.7075-.707a.5.5,0,0,0,0-.707L10.414,9l4.243-4.2425a.5.5,0,0,0,0-.707L13.95,3.343a.5.5,0,0,0-.70711-.00039Z\\\" />\\n</svg>\\n  </a>\\n</div><div class=\\\"image\\\" data-uuid=\\\"46514c31-b883-4d1f-8f97-26f054309646\\\">\\n  <img src=\\\"https://www.adobe.com/adobe.png\\\" data-mediarepo-id=\\\"author-p16854-e23341-cmstg.adobeaemcloud.com\\\" alt=\\\"\\\">\\n</div>\\n\\n\\n</div></body></html>\",") + "\"_xdm\":{\"mixins\":{\"_experience\":{\"customerJourneyManagement\":{\"messageExecution\":{\"messageExecutionID\":\"UIA-65098551\",\"messageID\":\"6195c1e5-f92c-4fe4-b20d-0f3b175ff01b\",\"messagePublicationID\":\"b3c204db-fce6-4ba6-92b0-0c9da490be05\",\"ajoCampaignID\":\"d9dd1e85-173b-4aa2-aa7e-9c242e15f9da\",\"ajoCampaignVersionID\":\"84b9430a-3ac1-49d5-a687-98e2f6d03437\"},\"messageProfile\":{\"channel\":{\"_id\":\"https://ns.adobe.com/xdm/channels/inapp\"}}}}}}}}]}]}");
            item.put("data", data);
            items.add(item);
        }
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("scopeDetails", scopeDetails);
        if (!config.noValidAppSurfaceInPayload) {
            messagePayload.put("scope", "mobileapp://mock_applicationId");
        } else if (config.nonMatchingAppSurfaceInPayload) {
            messagePayload.put("scope", "mobileapp://invalidId");
        }
        messagePayload.put("items", items);
        messagePayload.put("id", "testResponseId" + count);
        List<Map> payload = new ArrayList<>();
        if (config.hasEmptyPayload) {
            payload.add(new HashMap<>());
        } else {
            payload.add(messagePayload);
        }
        return payload;
    }

    /* JSON conversion helpers */

    /**
     * Converts provided {@link org.json.JSONObject} into {@link java.util.Map} for any number of levels, which can be used as event data
     * This method is recursive.
     * The elements for which the conversion fails will be skipped.
     *
     * @param jsonObject to be converted
     * @return {@link java.util.Map} containing the elements from the provided json, null if {@code jsonObject} is null
     */
    static Map<String, Object> toMap(final JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            Log.debug(LOG_TAG, "toMap - will not convert to map, the passed in json is null.");
            return null;
        }

        Map<String, Object> jsonAsMap = new HashMap<>();
        Iterator<String> keysIterator = jsonObject.keys();

        if (keysIterator == null) return null;

        while (keysIterator.hasNext()) {
            String nextKey = keysIterator.next();
            jsonAsMap.put(nextKey, fromJson(jsonObject.get(nextKey)));
        }

        return jsonAsMap;
    }

    /**
     * Converts provided {@link org.json.JSONObject} into a {@link Map<String,  Variant >} for any number of levels, which can be used as event data.
     * This method is recursive.
     * The elements for which the conversion fails will be skipped.
     *
     * @param jsonObject to be converted
     * @return {@link Map<String, Variant>} containing the elements from the provided json, null if {@code jsonObject} is null
     */
    static Map<String, Variant> toVariantMap(final JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            Log.debug(LOG_TAG, "toVariantMap - will not convert to variant map, the passed in json is null.");
            return null;
        }

        final Map<String, Variant> jsonAsVariantMap = new HashMap<>();
        final Iterator<String> keysIterator = jsonObject.keys();

        while (keysIterator.hasNext()) {
            final String nextKey = keysIterator.next();
            final Object value = fromJson(jsonObject.get(nextKey));
            jsonAsVariantMap.put(nextKey, getVariantValue(value));
        }

        return jsonAsVariantMap;
    }

    /**
     * Converts provided {@link JSONArray} into {@link List} for any number of levels which can be used as event data
     * This method is recursive.
     * The elements for which the conversion fails will be skipped.
     *
     * @param jsonArray to be converted
     * @return {@link List} containing the elements from the provided json, null if {@code jsonArray} is null
     */
    static List<Object> toList(final JSONArray jsonArray) throws JSONException {
        if (jsonArray == null) {
            Log.debug(LOG_TAG, "toList - will not convert to list, the passed in json array is null.");
            return null;
        }

        List<Object> jsonArrayAsList = new ArrayList<>();
        int size = jsonArray.length();

        for (int i = 0; i < size; i++) {
            jsonArrayAsList.add(fromJson(jsonArray.get(i)));
        }

        return jsonArrayAsList;
    }

    /**
     * Converts provided {@link Object} to a {@link JSONObject} or {@link JSONArray}.
     *
     * @param object to be converted to jSON
     * @return {@link Object} containing a json object or json array
     */
    static Object toJSON(final Object object) throws JSONException {
        if (object instanceof HashMap) {
            JSONObject jsonObject = new JSONObject();
            final Map map = (HashMap) object;
            for (final Object key : map.keySet()) {
                jsonObject.put(key.toString(), toJSON(map.get(key)));
            }
            return jsonObject;
        } else if (object instanceof Iterable) {
            JSONArray jsonArray = new JSONArray();
            final Iterator iterator = ((Iterable<?>) object).iterator();
            while (iterator.hasNext()) {
                jsonArray.put(toJSON(iterator.next()));
            }
            return jsonArray;
        } else {
            return object;
        }
    }

    /**
     * Converts provided {@link JSONObject} to a {@link Map} or {@link JSONArray} into a {@link List}.
     *
     * @param json to be converted
     * @return {@link Object} converted from the provided json object.
     */
    private static Object fromJson(final Object json) throws JSONException {
        if (json == JSONObject.NULL) {
            return null;
        } else if (json instanceof JSONObject) {
            return toMap((JSONObject) json);
        } else if (json instanceof JSONArray) {
            return toList((JSONArray) json);
        } else {
            return json;
        }
    }

    /**
     * Converts the provided {@link Object} into a {@link Variant}.
     * This method is recursive if the passed in {@code Object} is a {@link Map} or {@link List}.
     *
     * @param value to be converted to a variant
     * @return {@code Variant} value of the passed in {@code Object}
     */
    private static Variant getVariantValue(final Object value) {
        final Variant convertedValue;
        if (value instanceof String) {
            convertedValue = StringVariant.fromString((String) value);
        } else if (value instanceof Double) {
            convertedValue = DoubleVariant.fromDouble((Double) value);
        } else if (value instanceof Integer) {
            convertedValue = IntegerVariant.fromInteger((int) value);
        } else if (value instanceof Boolean) {
            convertedValue = BooleanVariant.fromBoolean((boolean) value);
        } else if (value instanceof Long) {
            convertedValue = LongVariant.fromLong((long) value);
        } else if (value instanceof Map) {
            final Map<String, Variant> map = new HashMap<>();
            for (final Map.Entry entry : ((Map<String, Object>) value).entrySet()) {
                map.put((String) entry.getKey(), getVariantValue(entry.getValue()));
            }
            convertedValue = Variant.fromVariantMap(map);
        } else if (value instanceof List) {
            final List<Variant> list = new ArrayList<>();
            for (final Object element : (List) value) {
                list.add(getVariantValue(element));
            }
            convertedValue = Variant.fromVariantList(list);
        } else {
            convertedValue = (Variant) value;
        }
        return convertedValue;
    }

    // ========================================================================================
    // PlatformServices getters
    // ========================================================================================

    /**
     * Returns the {@link PlatformServices} instance.
     *
     * @return {@code PlatformServices} or null if {@code PlatformServices} are unavailable
     */
    static PlatformServices getPlatformServices() {
        final PlatformServices platformServices = MobileCore.getCore().eventHub.getPlatformServices();

        if (platformServices == null) {
            Log.debug(LOG_TAG,
                    "getPlatformServices - Platform services are not available.");
        }

        return platformServices;
    }

    /**
     * Returns platform {@link JsonUtilityService} instance.
     *
     * @return {@code JsonUtilityService} or null if {@link PlatformServices} are unavailable
     */
    static JsonUtilityService getJsonUtilityService() {
        final PlatformServices platformServices = getPlatformServices();

        if (platformServices == null) {
            Log.debug(LOG_TAG,
                    "getJsonUtilityService -  Cannot get JsonUtility Service, Platform services are not available.");
            return null;
        }

        final JsonUtilityService jsonUtilityService = platformServices.getJsonUtilityService();
        if (jsonUtilityService == null) {
            Log.debug(LOG_TAG,
                    "getJsonUtilityService - JsonUtility services are not available.");
        }

        return jsonUtilityService;
    }
}
