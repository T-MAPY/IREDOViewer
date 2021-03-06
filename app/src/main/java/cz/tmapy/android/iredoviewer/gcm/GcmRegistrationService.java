package cz.tmapy.android.iredoviewer.gcm;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.playlog.internal.LogEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import cz.tmapy.android.iredoviewer.MainActivity;
import cz.tmapy.android.iredoviewer.R;

/**
 * The class register application to Google Cloud Messaging
 * Created by Kamil Svoboda on 24.2.2016.
 */
public class GcmRegistrationService extends IntentService {

    private static final String TAG = "GcmRegistrationService";
    public static final String INTENT_ACTION_REGISTER_NOTIFICATIONS = "cz.tmapy.android.iredoviewer.gcm.REGISTER_NOTIFICATIONS";
    public static final String INTENT_ACTION_UNREGISTER_NOTIFICATIONS = "cz.tmapy.android.iredoviewer.gcm.UNREGISTER_NOTIFICATIONS";
    public static final String INTENT_ACTION_REGISTER_TOPIC = "cz.tmapy.android.iredoviewer.gcm.REGISTER_TOPIC";
    public static final String INTENT_EXTRA_TOPIC = "topic";
    private static final String GLOBAL_TOPIC_NAME = "global";

    public static final String GCM_TOKEN = "gcmToken";

    private static final String REGISTRATION_SERVER_URL = "http://trex.svobodovi.net/gcm/register.php";
    private static final String UNREGISTRATION_SERVER_URL = "http://trex.svobodovi.net/gcm/unregister.php";
    private final int CONNECTION_TIMEOUT = 3000;
    private final int READ_TIMEOUT = 3000;

    SharedPreferences sharedPreferences;

    public GcmRegistrationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (intent.getAction().equals(INTENT_ACTION_REGISTER_NOTIFICATIONS)) {
            try {
                // [START register_for_gcm]
                // Initially this call goes out to the network to retrieve the token, subsequent calls
                // are local.
                // R.string.gcm_defaultSenderId (the Sender ID) is typically derived from google-services.json.
                // See https://developers.google.com/cloud-messaging/android/start for details on this file.
                // [START get_token]
                InstanceID instanceID = InstanceID.getInstance(this);
                String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId),
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                // [END get_token]
                Log.i(TAG, "GCM Registration Token: " + token);

                // get users gmail account of the user
                String gmail = null;
                Pattern gmailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
                Account[] accounts = AccountManager.get(this).getAccounts();
                for (Account account : accounts) {
                    if (gmailPattern.matcher(account.name).matches()) {
                        gmail = account.name;
                    }
                }

                if (isNetworkOnline()) {
                    sendRegistrationToServer(android.os.Build.MODEL, gmail, token);
                }

                // Subscribe to global topic channel
                subscribeTopic(token, GLOBAL_TOPIC_NAME);

                sharedPreferences.edit().putString(GCM_TOKEN, token).apply();

                // [END register_for_gcm]
            } catch (Exception e) {
                Log.d(TAG, "Failed to get GCM token", e);
                sharedPreferences.edit().remove(GCM_TOKEN).apply();
            }

        } else if (intent.getAction().equals(INTENT_ACTION_UNREGISTER_NOTIFICATIONS)) {
            try {
                //InstanceID.getInstance(this).deleteToken(getString(R.string.gcm_defaultSenderId), null);
                InstanceID.getInstance(this).deleteInstanceID();

                String token = sharedPreferences.getString(GCM_TOKEN, null);
                if (token != null) {
                    if (isNetworkOnline()) {
                        sendUnRegistrationToServer(token);
                    }
                    sharedPreferences.edit().remove(GCM_TOKEN).apply();
                } else
                {
                    Log.e(TAG,"Při odregistraci notifikací nebyl nalezen TOKEN v SharedPreferences");
                }
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (intent.getAction().equals(INTENT_ACTION_REGISTER_TOPIC)) {
            try {
                subscribeTopic(sharedPreferences.getString(GCM_TOKEN, null), intent.getStringExtra(INTENT_EXTRA_TOPIC));
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Persist registration to third-party servers.
     * <p/>
     * Modify this method to associate the user's GCM registration token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    private void sendRegistrationToServer(String name, String email, String token) {
        String serverResponse = "";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(REGISTRATION_SERVER_URL).openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            HashMap<String, String> postDataParams = new HashMap<String, String>();
            postDataParams.put("regId", token);
            if (name != null) postDataParams.put("name", name);
            if (email != null) postDataParams.put("email", email);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(getPostDataString(postDataParams));
            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();

            //accept all Successful 2xx responses
            if (responseCode >= HttpsURLConnection.HTTP_OK && responseCode <= HttpsURLConnection.HTTP_PARTIAL) {
                String line;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = br.readLine()) != null) {
                    serverResponse += line;
                }
                Log.i(TAG, "Registration stored on server with result: " + serverResponse);
            } else {
                Log.w(TAG, "Server response: " + responseCode);
            }

        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Connection timeout!", e);
            serverResponse = "Connection timeout!";
        } catch (java.io.IOException e) {
            Log.e(TAG, "Cannot read server response", e);
            serverResponse = "Cannot read server response";
        } catch (Exception e) {
            Log.e(TAG, "HTTP connection error", e);
            serverResponse = e.getLocalizedMessage();
        }
    }

    /**
     * Remove registration
     */
    private void sendUnRegistrationToServer(String token) {
        String serverResponse = "";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(UNREGISTRATION_SERVER_URL).openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            HashMap<String, String> postDataParams = new HashMap<String, String>();
            postDataParams.put("regId", token);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(getPostDataString(postDataParams));
            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();

            //accept all Successful 2xx responses
            if (responseCode >= HttpsURLConnection.HTTP_OK && responseCode <= HttpsURLConnection.HTTP_PARTIAL) {
                String line;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = br.readLine()) != null) {
                    serverResponse += line;
                }
                Log.i(TAG, "Client unregistered with response: " + serverResponse);
            } else {
                Log.w(TAG, "Server response: " + responseCode);
            }

        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Connection timeout!", e);
            serverResponse = "Connection timeout!";
        } catch (java.io.IOException e) {
            Log.e(TAG, "Cannot read server response", e);
            serverResponse = "Cannot read server response";
        } catch (Exception e) {
            Log.e(TAG, "HTTP connection error", e);
            serverResponse = e.getLocalizedMessage();
        }
    }

    private void subscribeTopic(String token, String topic) throws IOException {
        if (topic != null && !"".equals(topic)) {
            GcmPubSub pubSub = GcmPubSub.getInstance(this);
            if (pubSub != null)
                pubSub.subscribe(token, "/topics/" + topic, null);
        }
    }

    /**
     * Checks network connectivity
     *
     * @return
     */
    public boolean isNetworkOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return result.toString();
    }
}