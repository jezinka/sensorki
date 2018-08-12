package com.projects.jezinka.pogoda;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int SPAN_COUNT = 2;
    public static final String CHANNEL_ID = "S3N50R3K";

    private SensorsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();

        adapter = new SensorsAdapter();
        RecyclerView recyclerView = findViewById(R.id.list);

        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, SPAN_COUNT));
        recyclerView.setHasFixedSize(true);

        swipeRefreshLayout = findViewById(R.id.swiperefresh);
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        sendQueryForData();
                    }
                }
        );

        sendQueryForData();
    }

    private void sendQueryForData() {
        final Context mContext = this;
        WeatherService weatherService = WeatherService.retrofit.create(WeatherService.class);
        final Call<JsonObject> call = weatherService.loadData();

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {

                JsonObject body = response.body();
                if (body != null) {
                    List<Sensor> sensors = createSensorsFromJsonObject(body);
                    adapter.updateResults(sensors);
                    checkBatteryState(sensors);
                }
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                Log.e("LOAD_DATA", t.getLocalizedMessage());
                Toast.makeText(mContext, R.string.connection_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkBatteryState(List<Sensor> sensors) {
        for (Sensor sensor : sensors) {
            if (sensor.batteryNeedRecharge()) {
                sendNotification(sensor);
            }
        }
    }

    private void sendNotification(Sensor sensor) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_battery)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(sensor.getLabel() + getString(R.string.notification_text))
                .setAutoCancel(true)
                .setOngoing(false);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify((int) sensor.getId(), mBuilder.build());
    }

    private List<Sensor> createSensorsFromJsonObject(JsonObject body) {
        List<Sensor> sensors = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : body.getAsJsonObject(getString(R.string.readings)).entrySet()) {
            String label = getLabelFromJson(body, entry.getKey());
            sensors.add(new Sensor(entry, label));
        }

        return sensors;
    }

    private String getLabelFromJson(JsonObject body, String key) {
        return body
                .getAsJsonObject(getString(R.string.sensors))
                .getAsJsonObject(key)
                .get(getString(R.string.label))
                .toString()
                .replaceAll("\"", "");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_refresh:
                swipeRefreshLayout.setRefreshing(true);
                sendQueryForData();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.sensor_channel);
            String description = getString(R.string.sensor_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
