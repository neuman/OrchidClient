package neuman.orchidclient.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import neuman.orchidclient.authentication.AccountGeneral;
import neuman.orchidclient.content.ContentQueryMaker;
import neuman.orchidclient.content.Contract;
import neuman.orchidclient.content.ObjectTypes;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private String TAG = getClass().getSimpleName();
    private AccountManager mAccountManager;
    private ContentQueryMaker contentQueryMaker;
    /**
     * URL to fetch content from during a sync.
     *
     * <p>This points to the Android Developers Blog. (Side note: We highly recommend reading the
     * Android Developer Blog to stay up to date on the latest Android platform developments!)
     */
    private static final String LOCATIONS_URL = "/location/list/";

    private static final String INDICATORS_URL = "/indicator/list/";

    /**
     * Network connection timeout, in milliseconds.
     */
    private static final int NET_CONNECT_TIMEOUT_MILLIS = 15000;  // 15 seconds

    /**
     * Network read timeout, in milliseconds.
     */
    private static final int NET_READ_TIMEOUT_MILLIS = 10000;  // 10 seconds

    /**
     * Content resolver, for performing database operations.
     */
    // Define a variable to contain a content resolver instance
    ContentResolver mContentResolver;

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
        mAccountManager = AccountManager.get(context);
        contentQueryMaker = new ContentQueryMaker(context.getContentResolver());
        Log.d("SYNC","SyncAdapter autoInitializer");
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
        mAccountManager = AccountManager.get(context);
        Log.d("SYNC","SyncAdapter");

    }

    /*
 * Specify the code you want to run in the sync adapter. The entire
 * sync adapter runs in a background thread, so you don't have to set
 * up your own background processing.
 */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult){
        /*
         * Put the data transfer code here.
         */

        Log.i(TAG, "Beginning network synchronization");
        Log.d(TAG, "account.name: "+account.name);
        Log.d(TAG, "account.type: "+account.type);
        Log.d(TAG, "mAccountManager: "+mAccountManager.toString());
        Log.d(TAG, "AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS: "+AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS);


        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
        String hostname = settings.getString("example_text", "NO HOSTNAME");


        contentQueryMaker.drop_contentProvider_model(ObjectTypes.TYPE_LOCATION);
        contentQueryMaker.drop_contentProvider_model(ObjectTypes.TYPE_INDICATOR);

        String locationJSON = make_authenticated_request(account, hostname+LOCATIONS_URL);
        JSONObject locationObject = new JSONObject();
        JSONArray locationList = new JSONArray();
        try{
        locationObject = new JSONObject(locationJSON);
        locationList = locationObject.getJSONArray("locations");
        for(int i=0; i < locationList.length(); i++){
            String individual_location_json = locationList.getString(i);
            Log.d(TAG, "Attempting to insert: "+individual_location_json);
            JSONObject location_json = new JSONObject(individual_location_json);
            insert_into_provider(provider,syncResult,individual_location_json, ObjectTypes.TYPE_LOCATION,location_json.getInt("id"));
        }
        }catch(JSONException e){
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        String indicatorJSON = make_authenticated_request(account, hostname+INDICATORS_URL);
        JSONObject indicatorObject = new JSONObject();
        JSONArray indicatorList = new JSONArray();
        try{
            indicatorObject = new JSONObject(indicatorJSON);
            indicatorList = indicatorObject.getJSONArray("indicators");
            for(int x=0; x< indicatorList.length(); x++){
                String individual_indicator_json = indicatorList.getString(x);
                Log.d(TAG, "Attempting to insert: "+individual_indicator_json);
                JSONObject indicator_json = new JSONObject(individual_indicator_json);
                insert_into_provider(provider,syncResult,individual_indicator_json, ObjectTypes.TYPE_INDICATOR,indicator_json.getInt("id"));
            }
        }catch(JSONException e){
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        push_new_records(getContext().getContentResolver(),account);
        contentQueryMaker.drop_contentProvider_model(ObjectTypes.TYPE_RECORD);
        Log.i(TAG, "Network synchronization complete");



    }


    public void insert_into_provider(ContentProviderClient provider, SyncResult syncResult, String value, Integer objecttype, Integer model_id){
        try {
            // Defines a new Uri object that receives the result of the insertion
            Uri mNewUri;

            // Defines an object to contain the new values to insert
            ContentValues mNewValues = new ContentValues();

        /*
         * Sets the values of each column and inserts the word. The arguments to the "put"
         * method are "column name" and "value"
         */
            mNewValues.put(Contract.Entry.COLUMN_NAME_OBJECTTYPE, objecttype);
            mNewValues.put(Contract.Entry.COLUMN_NAME_JSON, value);
            mNewValues.put(Contract.Entry.COLUMN_NAME_MODEL_ID, model_id);


            mNewUri = provider.insert(
                    Contract.Entry.CONTENT_URI,   // the user dictionary content URI
                    mNewValues                          // the values to insert
            );

        } catch (RemoteException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
            syncResult.databaseError = true;
            return;
        }
    }

    private String make_authenticated_request(Account account, String url){
        String responseString = "no response";
        String authtoken = mAccountManager.peekAuthToken(account, AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS);
        try {

            AndroidHttpClient httpclient = AndroidHttpClient.newInstance("Android");
            HttpGet request = new HttpGet(url);
            request.setHeader("X_REQUESTED_WITH", "XMLHttpRequest");
            String cookiestring = "sessionid="+authtoken;
            Log.d(TAG,"Cookiestring: "+cookiestring);
            request.addHeader("Cookie", cookiestring);
            HttpResponse response = httpclient.execute(request);
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseString = out.toString();
                Log.d(TAG, responseString);
                //..more logic
            } else{
                //Closes the connection.
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }

        }catch(Exception e){
            Log.d("HTTP exception", e.toString());
            e.printStackTrace();
        }
        return responseString;
    }

    private void push_new_records(ContentResolver contentResolver, Account account){
        String authtoken = mAccountManager.peekAuthToken(account, AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS);
        Log.d(TAG, "Preparing to Push New Records");
        // A "projection" defines the columns that will be returned for each row
        String[] mProjection =
                {
                        Contract.Entry._ID,    // Contract class constant for the _ID column name
                        Contract.Entry.COLUMN_NAME_OBJECTTYPE,
                        Contract.Entry.COLUMN_NAME_JSON,   // Contract class constant for the word column name
                };

        // Defines a string to contain the selection clause
        String mSelectionClause =  Contract.Entry.COLUMN_NAME_OBJECTTYPE+" = "+ ObjectTypes.TYPE_RECORD;

        // Does a query against the table and returns a Cursor object

        Log.d(TAG, Contract.Entry.CONTENT_URI.toString());
        Log.d(TAG,"mProjection: "+mProjection.toString());
        Cursor mCursor = contentResolver.query(
                Contract.Entry.CONTENT_URI,  // The content URI of the words table
                mProjection,                       // The columns to return for each row
                mSelectionClause,                   // Either null, or the word the user entered
                null,                    // Either empty, or the string the user entered
                Contract.Entry.COLUMN_NAME_OBJECTTYPE);                       // The sort order for the returned rows

        // Some providers return null if an error occurs, others throw an exception
        if (null == mCursor) {
    /*
     * Insert code here to handle the error. Be sure not to use the cursor! You may want to
     * call android.util.Log.e() to log this error.
     *
     */
            // If the Cursor is empty, the provider found no matches
            Log.d(TAG,"Cursor Error");
        } else if (mCursor.getCount() < 1) {

    /*
     * Insert code here to notify the user that the search was unsuccessful. This isn't necessarily
     * an error. You may want to offer the user the option to insert a new row, or re-type the
     * search term.
     */
            Log.d(TAG,"No new records to push");

        } else {
            Log.d(TAG, "New records to push");
            // Insert code here to do something with the results
            while (mCursor.moveToNext()) {
                Log.d(TAG,"*****CURSOR MOVED*****");
                Log.d(TAG, mCursor.getColumnName(0)+": "+mCursor.getString(0));
                Log.d(TAG, mCursor.getColumnName(1)+": "+mCursor.getString(1));
                String jsonString = mCursor.getString(2);
                Log.d(TAG, mCursor.getColumnName(2)+": "+jsonString);
                try{
                    JSONObject record_data = new JSONObject(jsonString);
                    String responseString = "no response";

                    Log.d(TAG, "authtoken: "+authtoken);
                    try {

                        AndroidHttpClient httpclient = AndroidHttpClient.newInstance("Android");
                        HttpPost request = new HttpPost(record_data.getString("outgoing_url"));
                        request.setHeader("X_REQUESTED_WITH", "XMLHttpRequest");
                        String cookiestring = "sessionid="+authtoken;
                        Log.d(TAG,"Cookiestring: "+cookiestring);
                        request.addHeader("Cookie", cookiestring);
                        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                        JSONArray values_json_array = record_data.getJSONArray("values");
                        for(int i=0; i < values_json_array.length(); i++){
                            JSONObject val = (JSONObject) values_json_array.get(i);
                            nameValuePairs.add(new BasicNameValuePair(val.getString("name"), val.getString("value")));
                        }
                        request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                        HttpResponse response = httpclient.execute(request);
                        StatusLine statusLine = response.getStatusLine();
                        if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            response.getEntity().writeTo(out);
                            out.close();
                            responseString = out.toString();
                            Log.d(TAG, responseString);
                            //..more logic
                        } else{
                            //Closes the connection.
                            response.getEntity().getContent().close();
                            throw new IOException(statusLine.getReasonPhrase());
                        }

                    }catch(Exception e){
                        Log.d("HTTP exception", e.toString());
                        e.printStackTrace();
                    }
                }catch(JSONException e){
                    Log.d(TAG, e.toString());
                    e.printStackTrace();
                }
            }
        }

        Log.d(TAG, "New Record Push Done");
    }

}