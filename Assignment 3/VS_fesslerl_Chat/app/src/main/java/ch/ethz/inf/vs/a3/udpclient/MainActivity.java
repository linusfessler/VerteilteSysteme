package ch.ethz.inf.vs.a3.udpclient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import ch.ethz.inf.vs.a3.message.Message;
import ch.ethz.inf.vs.a3.message.MessageTypes;

public class MainActivity extends AppCompatActivity {

    // Strings for identifying intent extras
    public static final String USERNAME = "USERNAME";
    public static final String UUID = "UUID";
    public static final String IP = "IP";
    public static final String PORT = "PORT";
    public static final int ATTEMPTS = 5;

    //private DatagramSocket sock;
    private static final String LOG_TAG = "MainActivity";
    private String username;
    private TextView status_textview;
    private EditText editText;
    private Button joinButton;
    private Button leaveButton;
    private String uuid;
    private InetAddress toAddr;
    private int port;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // create UUID
        uuid = java.util.UUID.randomUUID().toString();

        // Init TextView for status messages
        status_textview = (TextView) findViewById(R.id.status_textview);
        editText = (EditText) findViewById(R.id.uname_input);

        // Init button to change text and block
        joinButton = (Button) findViewById(R.id.button_join);
        leaveButton = (Button) findViewById(R.id.button_leave);
        DatagramSocket socket = UDPClient.getSocket();
        if (socket == null || socket.isClosed())
            leaveButton.setEnabled(false);
        else
            editText.setEnabled(false);

        // Set default values for settings
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return true;
    }

    // Execute when user clicks on Settings menu
    public void onSettingsClicked(MenuItem Item) {
        Intent toSettings = new Intent(this, SettingsActivity.class);
        startActivity(toSettings);
    }

    public void onJoinClicked(View v) {

        Log.d(LOG_TAG, "### Clicked Join");

        // Get username from text input
        EditText username_input = (EditText) findViewById(R.id.uname_input);
        username = username_input.getText().toString();

        // If username is invalid, use default username
        if (username.isEmpty())
            username = getString(R.string.default_username);

        Log.d(LOG_TAG, "### Username: " + username);

        // Only use first line as username
        String[] temp = username.split("\n", 2);
        username = temp[0];

        toAddr = getIpFromSettings();
        port = getPortFromSettings();

        // First: Register at server. If registering is successful: start ChatActivity. Else: display error message.
        DatagramSocket socket = UDPClient.getSocket();
        if (socket == null || socket.isClosed())
            connectToServer();
        else
            onSuccessfulRegistration(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromServer();
    }

    public void onLeaveClicked(View v){
        disconnectFromServer();
    }

    private void onSuccessfulRegistration(String ackMessage) {
        // Put username and uuid into Intent and start ChatActivity
        Intent toChat = new Intent(this, ChatActivity.class);
        toChat.putExtra(USERNAME, username);
        toChat.putExtra(UUID, uuid);
        toChat.putExtra(IP, toAddr.getHostAddress());
        toChat.putExtra(PORT, port);
        startActivity(toChat);
    }

    // Called to connect to server (by sending a registration packet)
    private void connectToServer(){

        Log.d(LOG_TAG, "###Starting registration process");

        // Create new UDP Socket
        try {
            UDPClient.setSocket(new DatagramSocket(port));
        } catch(SocketException e){
            errorDiag("Could not bind socket to port " + port);
            e.printStackTrace();
        }

        try {
            UDPClient.getSocket().setSoTimeout(NetworkConsts.SOCKET_TIMEOUT);
        } catch (SocketException e) {
            errorDiag("Could not set socket timeout " + NetworkConsts.SOCKET_TIMEOUT);
        }


        // Create Register Packet message
        byte[] buf = new byte[NetworkConsts.PAYLOAD_SIZE];
        try {
            buf = new Message(username, uuid, null, MessageTypes.REGISTER, null).json.getBytes();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Build packet
        DatagramPacket registerPacket = new DatagramPacket(buf, 0, buf.length, toAddr, port);


        new RegisterTask().execute(registerPacket);

    }

    // Called to disconnect from server
    private void disconnectFromServer(){

        // TODO: Check if it is necessary to get port and ip value from the settings again

        // Check if socket is null.
        if(UDPClient.getSocket() == null)
            errorDiag("Socket null");

        // Create deregistration packet
        byte[] buf = new byte[NetworkConsts.PAYLOAD_SIZE];
        try {
            buf = new Message(username, uuid, null, MessageTypes.DEREGISTER, null).json.getBytes();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Build packet
        DatagramPacket deregisterPacket = new DatagramPacket(buf, buf.length, toAddr, port);

        new DeregisterTask().execute(deregisterPacket);

    }

    private int getPortFromSettings(){

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Read portnumber as string
        String portString = sharedPref.getString(SettingsActivity.PREF_PORT, "");

        return Integer.parseInt(portString);
    }

    private InetAddress getIpFromSettings(){

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String ipString = sharedPref.getString(SettingsActivity.PREF_IP, "");

        Log.d(LOG_TAG, "### IP read from settings: " + ipString);

        if(ipString.isEmpty())
            errorDiag("Could not read IP from settings.");

        if(!SettingsActivity.checkIp(ipString))
            errorDiag("No valid IP address. Please change the IP in Settings to a valid IP. ");

        InetAddress addr = null;
        try {
            addr = InetAddress.getByName(ipString);
        } catch (UnknownHostException e) {
            errorDiag("Could not get IP Address by name " + ipString);
        }

        return addr;
    }


    // TODO: Implement dialog creation for displaying error messages
    // Used to print error messages as dialog.
    private void errorDiag(String message){
        Log.d(LOG_TAG, "#### ERROR: " + message);
    }



    //--------------------------------------------------------------------------------------------------------------------------------------------------------------

    // Nest this AsyncTask class so that it can access the GUI
    private class RegisterTask extends AsyncTask<DatagramPacket, String, String> {

        // Use this to somehow display that a connection is being established (change Join button to Connect... or use Status Textview, ...)
        @Override
        protected void onPreExecute(){
            status_textview.setText("\nStarting registration process...");

            // Change button text and disable leave button to prevent errors
            joinButton.setText(R.string.joining);
            joinButton.setEnabled(false);
            leaveButton.setEnabled(false);
            editText.setEnabled(true);
        }

        @Override
        protected String doInBackground(DatagramPacket... dps){

            // Message object for buffering received ack
            Message recAck = null;
            String ackString = null;
            boolean success = false;

            // Check if there is at least one DatagramPacket in the array
            if(dps.length <= 0){
                this.cancel(false);
            }

            // Check if socket is valid
            if(UDPClient.getSocket() == null){
                this.cancel(false);
            }

            // Create new packet for ack message
            byte[] ack_buf = new byte[NetworkConsts.PAYLOAD_SIZE];     // More than enough space for the ack message
            DatagramPacket ack = new DatagramPacket(ack_buf, ack_buf.length);

            // Try sending and receiving an answer
            for(int i = 0; i < ATTEMPTS && !success; i++)
            {
                // Try sending the packet
                try {
                    UDPClient.getSocket().send(dps[0]);
                } catch (IOException e) {
                    this.cancel(false);
                }

                // Try receiving the packet
                try {
                    publishProgress("Try # " + (i+1));
                    UDPClient.getSocket().receive(ack);
                } catch (SocketTimeoutException te) {
                    publishProgress("Timeout.");
                    if (i == 4)
                        cancel(false);
                } catch (IOException e) {
                    this.cancel(false);
                }

                // Abort loop if correct message has been received.
                ackString = new String(ack.getData());
                if(!ackString.isEmpty()){
                    try {
                        recAck = new Message(ackString);
                        if(recAck.type.equals(MessageTypes.ACK_MESSAGE)){
                            success = true;
                        }
                    } catch (JSONException e) {}
                }
            }

            return (new String(ack.getData()));
        }

        @Override
        protected void onProgressUpdate(String... strings){
            status_textview.append("\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(String result) {

            // Revert button text and reenable
            joinButton.setText(R.string.join);
            joinButton.setEnabled(true);
            leaveButton.setEnabled(true);
            editText.setEnabled(false);

            status_textview.append("\nReceived Ack from Server. Client is registered.");
            onSuccessfulRegistration(result);
        }

        // Cancel if socket is null or if no answer was received after 5 attempts
        @Override
        protected void onCancelled(){

            // Revert join button text and reenable leave button
            joinButton.setText(R.string.join);
            joinButton.setEnabled(true);
            leaveButton.setEnabled(false);
            editText.setEnabled(true);

            status_textview.append("\nERROR: Could not register to server.");
            UDPClient.getSocket().close();
            UDPClient.setSocket(null);
        }
    }

    // Nest this AsyncTask class so that it can access the GUI
    private class DeregisterTask extends AsyncTask<DatagramPacket, String, String> {

        // Use this to somehow display that a connection is being established (change Join button to Connect... or use Status Textview, ...)
        @Override
        protected void onPreExecute(){

            status_textview.setText("\nStarting deregistration Process...");

            // Change button text and disable to prevent errors
            leaveButton.setText(R.string.leaving);
            leaveButton.setEnabled(false);
            joinButton.setEnabled(false);
        }

        @Override
        protected String doInBackground(DatagramPacket... dps){

            // Message object for buffering received ack
            Message recAck = null;
            String ackString = null;
            boolean success = false;

            // Check if there is at least one DatagramPacket in the array
            if(dps.length <= 0){
                this.cancel(false);
            }

            // Check if socket is valid
            if(UDPClient.getSocket() == null){
                this.cancel(false);
            }

            // Create new packet for ack message
            byte[] ack_buf = new byte[NetworkConsts.PAYLOAD_SIZE];     // More than enough space for the ack message
            DatagramPacket ack = new DatagramPacket(ack_buf, ack_buf.length);

            // Try sending and receiving an answer
            for(int i = 0; i < ATTEMPTS && !success; i++)
            {
                // Try sending the packet
                try {
                    UDPClient.getSocket().send(dps[0]);
                } catch (IOException e) {
                    this.cancel(false);
                }

                // Try receiving the packet
                try {
                    publishProgress("Try # " + (i+1));
                    UDPClient.getSocket().receive(ack);
                } catch (SocketTimeoutException te) {
                    publishProgress("Timeout.");
                    if (i == 4)
                        cancel(false);
                } catch (IOException e) {
                    this.cancel(false);
                }

                // Abort loop if correct message has been received.
                ackString = new String(ack.getData());
                if(!ackString.isEmpty()){
                    try {
                        recAck = new Message(ackString);
                        if(recAck.type.equals(MessageTypes.ACK_MESSAGE)){
                            success = true;
                        }
                    } catch (JSONException e) {}
                }
            }

            return (new String(ack.getData()));
        }

        @Override
        protected void onProgressUpdate(String... strings){
            status_textview.append("\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            // Revert button text and reenable
            leaveButton.setText(R.string.leave);
            leaveButton.setEnabled(false);
            joinButton.setEnabled(true);
            editText.setEnabled(true);

            status_textview.append("\nReceived Ack from Server. Client is deregistered.");
            UDPClient.getSocket().close();
            UDPClient.setSocket(null);
        }

        // Cancel if socket is null or if no answer was received after 5 attempts
        @Override
        protected void onCancelled(){
            // Revert button text and reenable
            leaveButton.setText(R.string.leave);
            leaveButton.setEnabled(true);
            joinButton.setEnabled(true);

            status_textview.append("\nERROR: Could not deregister from server.");
        }
    }
}
