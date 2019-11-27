package com.example.android.pointpossample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<String>,SharedPreferences.OnSharedPreferenceChangeListener {

    private static int counter = 1;
    public TextView requestView;
    public TextView responseView;
    private static final int POSloader = 22;
    private String commandKey = null;
    private String commandValue = null;
    private String entryCode = null;
    public String address = null;
    //MAC_KEY is string mac_key data received from device
    private String MAC_KEY = null;
    private String MAC_LABEL = null;
    //HMAC is SHA256 with counter value of decoded and decrypted macKey
    private String HMAC = null;
    public int port;
    private static final String LIFECYCLE_RESPONSE_KEY = "Callback";
    private Key privateKey = null;
    //macKey is decoded and decrypted Mac_key received from the terminal
    private byte[] macKey = new byte[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestView = (TextView) findViewById(R.id.xml_request);
        responseView = (TextView) findViewById(R.id.xml_response);
        getSupportLoaderManager().initLoader(POSloader, null, this);
        setupSharedPreference();

        if(savedInstanceState != null){
            if(savedInstanceState.containsKey(LIFECYCLE_RESPONSE_KEY)){
                String ResponseCallback = savedInstanceState.getString(LIFECYCLE_RESPONSE_KEY);
                requestView.setText(ResponseCallback);
            }
            if(savedInstanceState.containsKey("macKey")){
                byte[]  HMACcallBack = savedInstanceState.getByteArray("macKey");
                macKey = HMACcallBack;
            }
            if(savedInstanceState.containsKey("MACLABEL")){
                String  HMACcallBack = savedInstanceState.getString("MACLABEL");
                MAC_LABEL = HMACcallBack;
            }
        }
    }
    private void setupSharedPreference(){

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        address = sharedPreferences.getString(getString(R.string.ipKey),getString(R.string.defaultIP));
        port = Integer.parseInt(sharedPreferences.getString(getString(R.string.portKey),"5015"));

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.ipKey))){
            address = sharedPreferences.getString(getString(R.string.ipKey),getString(R.string.defaultIP));
        } else if(key.equals(getString(R.string.portKey))){
            port = Integer.parseInt(sharedPreferences.getString(getString(R.string.portKey),"5015"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        String lifecycleDisplayTextViewContents = requestView.getText().toString();
        outState.putString(LIFECYCLE_RESPONSE_KEY,lifecycleDisplayTextViewContents);
        byte[] lifecycleHMAC = macKey;
        outState.putByteArray("macKey",lifecycleHMAC);
        String lifecycleMACLABEL = MAC_LABEL;
        outState.putString("MACLABEL",lifecycleMACLABEL);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pos_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int menuItem = item.getItemId();
        if (menuItem == R.id.register) {
            Random generator = new Random();
            entryCode = String.valueOf(generator.nextInt(9999));
            requestView.setText(Command.toString(Command.register(publicKeyGenerate(), entryCode)));
            responseView.setText("");
            commandKey = "Command";
            commandValue = "register";
        }
        if (menuItem == R.id.Test_Mac) {
            counter = ++counter;
            String strCounter = String.valueOf(counter);
            HMAC = printMacAsBase64(macKey,strCounter);
            requestView.setText(Command.toString(Command.test_mac(MAC_LABEL,HMAC, counter)));
            responseView.setText("");
            commandKey = "Command";
            commandValue = "test_mac";
        }
        if (menuItem == R.id.Capture) {
            counter = ++counter;
            String strCounter = String.valueOf(counter);
            HMAC = printMacAsBase64(macKey,strCounter);
            requestView.setText(Command.toString(Command.capture(MAC_LABEL,HMAC, counter)));
            responseView.setText("");
            commandKey = "Command";
            commandValue = "capture";
        }
        if (menuItem == R.id.Start) {
            counter = ++counter;
            String strCounter = String.valueOf(counter);
            HMAC = printMacAsBase64(macKey,strCounter);
            requestView.setText(Command.toString(Command.start(MAC_LABEL,HMAC, counter)));
            responseView.setText("");
            commandKey = "Command";
            commandValue = "start";
        }
        if (menuItem == R.id.Finish) {
            counter = ++counter;
            String strCounter = String.valueOf(counter);
            HMAC = printMacAsBase64(macKey,strCounter);
            requestView.setText(Command.toString(Command.finish(MAC_LABEL,HMAC, counter)));
            responseView.setText("");
            commandKey = "Command";
            commandValue = "finish";
        }
        if (menuItem == R.id.send) {
            startLoader(commandKey,commandValue);
        }
        if (menuItem == R.id.setting){
            Intent startSettingsActivity = new Intent(this,SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void startLoader(String key, String value){
        Bundle queryBundle = new Bundle();
        Log.d("POS","Starting background process");
        queryBundle.putString(key, value);
        LoaderManager loaderManager = getSupportLoaderManager();
        Loader<String> PosCommLoader = loaderManager.getLoader(POSloader);
        if (PosCommLoader == null) {
            loaderManager.initLoader(POSloader, queryBundle, this);
        } else {
            loaderManager.restartLoader(POSloader, queryBundle, this);
        }
    }


    @Override
    public Loader<String> onCreateLoader(int id, final Bundle args) {
        return new AsyncTaskLoader<String>(this) {

            String xmlData;

            @Override
            protected void onStartLoading() {

                /* If no arguments were passed, we don't have a query to perform. Simply return. */
                if (args == null) {
                    return;
                }

                if (xmlData != null) {
                    deliverResult(xmlData);
                } else {
                    forceLoad();
                }
            }

            @Override
            public String loadInBackground() {
                String returnedData = null;

                String commandKey = args.getString("Command");
                System.out.print(commandKey);
                if (commandKey=="register") {
                    returnedData = Command.sendDocument(Command.register(publicKeyGenerate(), entryCode), address, port);
                }
                if (commandKey=="test_mac"){
                    returnedData = Command.sendDocument(Command.test_mac(MAC_LABEL,HMAC,counter),address,port);
                }
                if (commandKey=="start"){
                    returnedData = Command.sendDocument(Command.start(MAC_LABEL,HMAC,counter),address,port);
                }
                if (commandKey=="capture"){
                    returnedData = Command.sendDocument(Command.capture(MAC_LABEL,HMAC,counter),address,port);
                }
                if (commandKey=="finish"){
                    returnedData = Command.sendDocument(Command.finish(MAC_LABEL,HMAC,counter),address,port);
                }
                return returnedData;
            }
            @Override
            public void deliverResult(String document) {
                xmlData = document;
                responseView.setText(document);

                super.deliverResult(document);
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<String> loader, String document) {
        responseView.setText(document);
        retrievecred(document);
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {

    }
    private String publicKeyGenerate() {

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.genKeyPair();
            Key publicKey = kp.getPublic();
            privateKey = kp.getPrivate();

            byte[] encodedPublicKey = publicKey.getEncoded();
            String b64PublicKey = Base64.encodeToString(encodedPublicKey, Base64.NO_WRAP);
            return b64PublicKey;
        }catch(Exception e){
            //e.printStackTrace();
            return null;
        }

    }
    private void retrievecred(String document){
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(new StringReader(document));
            int event = xpp.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if(xpp.getEventType()== XmlPullParser.START_TAG){
                    if(xpp.getName().equals("MAC_LABEL")){
                        MAC_LABEL = xpp.nextText();
                    }
                    if(xpp.getName().equals("MAC_KEY")){
                        MAC_KEY = xpp.nextText();
                        decrypt(MAC_KEY);
                    }
                }
                event = xpp.next();
            }

        }catch(Exception e){
            //e.printStackTrace();
        }
    }
    private void decrypt(String MAC_KEY){
        try {
            byte[] macKeybase64decode = Base64.decode(MAC_KEY, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE,privateKey);
            macKey = cipher.doFinal(macKeybase64decode);

        }catch(Exception e){
            //e.printStackTrace();
        }
    }
    private static String printMacAsBase64(byte[] macKey, String counter){
        //SecretKeySpec signingKey = new SecretKeySpec(macKey, "AES");

        try {
            SecretKeySpec signingKey = new SecretKeySpec(macKey, "AES");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] counterMac = mac.doFinal(counter.getBytes("UTF-8"));
            return Base64.encodeToString(counterMac, Base64.NO_WRAP);
        } catch (Exception e) {
            //e.printStackTrace();
            return null;
        }
    }
}
