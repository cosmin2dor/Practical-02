package ro.pub.cs.systems.eim.practicaltest02;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;

public class MainActivity extends AppCompatActivity {

    private Integer serverPort;
    private Integer clientPort;
    private String clientAddress;
    private String currency;

    EditText serverPortText;
    ServerThread serverThread;
    EditText addressText;
    EditText clientPortText;
    Spinner spinner;
    TextView responseText;
    Button getButton;
    Button listenButton;

    public BitcoinPrice cache;

    private class ServerThread extends Thread {
        private boolean isRunning = false;
        private ServerSocket serverSocket;

        public void startServer() {
            isRunning = true;
            start();
        }

        public void stopServer() {
            isRunning = false;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (serverSocket != null) {
                            serverSocket.close();
                        }
                        Log.v(Constants.TAG, "stopServer() method invoked "+serverSocket);
                    } catch(IOException ioException) {
                        Log.e(Constants.TAG, "An exception has occurred: "+ioException.getMessage());
                        ioException.printStackTrace();
                    }
                }
            }).start();
        }

        private BitcoinPrice updatePrice() {
            try {
                Log.v(Constants.TAG, "Updating the price...");

                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpGet = new HttpGet(Constants.API_URL);

                HttpResponse httpResponse = httpClient.execute(httpGet);
                HttpEntity httpEntity = httpResponse.getEntity();

                if (httpEntity != null) {
                    String respData = EntityUtils.toString(httpEntity);
                    Log.v(Constants.TAG, respData);

                    JSONObject jsonObject = new JSONObject(respData);

                    String timestamp = jsonObject.getJSONObject("time").getString("updated");
                    String eurValue = jsonObject.getJSONObject("bpi").getJSONObject("EUR").getString("rate");
                    String usdValue = jsonObject.getJSONObject("bpi").getJSONObject("USD").getString("rate");

                    BitcoinPrice bitcoinPrice = new BitcoinPrice(eurValue, usdValue, timestamp);

                    Log.v(Constants.TAG, "Updated price: " + bitcoinPrice.toString());

                    cache = bitcoinPrice;

                    return bitcoinPrice;
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        private String getPriceOf(String currency, BitcoinPrice bitcoinPrice) {
            if (bitcoinPrice == null) {
                return null;
            }

            switch (currency) {
                case "EUR":
                    return bitcoinPrice.getEurValue();
                case "USD":
                    return bitcoinPrice.getUsdValue();
                default:
                    return null;
            }
        }



        @RequiresApi(api = Build.VERSION_CODES.O)
        private String getPriceData(String currency) {

            String data;

            if (cache == null) {
                // There is no local data, pull it
                Log.v(Constants.TAG, "No local cache");
                data = getPriceOf(currency, updatePrice());
            } else {
                // There is local data, check if still valid
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                LocalTime cacheUpdatedAt = LocalTime.parse(cache.getUpdated(), formatter).plusHours(3);

                Log.v(Constants.TAG, cacheUpdatedAt.toString());
                Log.v(Constants.TAG, LocalTime.now().toString());

                if (LocalTime.now().minusMinutes(1).isAfter(cacheUpdatedAt)) {
                    // Older than one minute, update it
                    Log.v(Constants.TAG, "Older cache");
                    data = getPriceOf(currency, updatePrice());
                } else {
                    // Get from cache
                    Log.v(Constants.TAG, "Cache is fine");
                    data = getPriceOf(currency, cache);
                }
            }

            return data;
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(serverPort);

                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    Log.v(Constants.TAG, "Connection opened with " + socket.getInetAddress() + ":" + socket.getLocalPort());

                    BufferedReader bufferedReader = Utilities.getReader(socket);
                    String currency = bufferedReader.readLine();

                    String data = getPriceData(currency);

                    PrintWriter printWriter = Utilities.getWriter(socket);
                    printWriter.println(data);

                    socket.close();

                    Log.v(Constants.TAG, "Connection closed");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class BitCoinClientAsync extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            String data = null;

            try {
                Socket socket = new Socket(clientAddress, clientPort);

                PrintWriter printWriter = Utilities.getWriter(socket);
                printWriter.println(currency);

                BufferedReader bufferedReader = Utilities.getReader(socket);

                data = bufferedReader.readLine();

                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            responseText.setText(s);
        }
    }

    private GetButtonOnClickListener getButtonOnClickListener = new GetButtonOnClickListener();
    private class GetButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            clientAddress = addressText.getText().toString();
            clientPort = Integer.parseInt(clientPortText.getText().toString());
            currency = spinner.getSelectedItem().toString();

            BitCoinClientAsync bitCoinClientAsync = new BitCoinClientAsync();
            bitCoinClientAsync.execute();
        }
    }

    private ListenButtonOnClickListener listenButtonOnClickListener = new ListenButtonOnClickListener();
    private class ListenButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            serverPort = Integer.parseInt(serverPortText.getText().toString());
            if (serverPort == null) {
                Toast.makeText(getApplicationContext(), "[MAIN ACTIVITY] Server port should be filled!", Toast.LENGTH_SHORT).show();
                return;
            }

            serverThread = new ServerThread();
            serverThread.startServer();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverPortText = (EditText)findViewById(R.id.serverPortText);
        listenButton = (Button)findViewById(R.id.serverButton);
        addressText = (EditText)findViewById(R.id.addressText);
        clientPortText = (EditText)findViewById(R.id.clientPortText);
        spinner = (Spinner) findViewById(R.id.spinner);
        responseText = (TextView) findViewById(R.id.responseText);
        getButton = (Button)findViewById(R.id.getButton);

        listenButton.setOnClickListener(listenButtonOnClickListener);
        getButton.setOnClickListener(getButtonOnClickListener);
    }

    @Override
    protected void onDestroy() {
        Log.i(Constants.TAG, "[MAIN ACTIVITY] onDestroy() callback method has been invoked");
        if (serverThread != null) {
            serverThread.stopServer();
        }
        super.onDestroy();
    }
}
