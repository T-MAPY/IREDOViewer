package cz.tmapy.android.iredoviewer;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlFeature;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.bonuspack.kml.KmlPoint;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import cz.tmapy.android.iredoviewer.gcm.GcmRegistrationService;
import cz.tmapy.android.iredoviewer.utils.PlayServicesUtils;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String TAG = "IREDOViewerMap";
    private final String mServiceUrlBase = "http://tabule.oredo.cz/geoserver/iredo/ows?service=WFS&version=1.0.0&request=GetFeature&outputFormat=application/json&";
    private final String mSpojeUrl = "&typeName=iredo:service_currentposition&maxFeatures=1000";
    private final String mDealyUrl = "&typeName=iredo:station_serviceschedule&viewparams=ident:"; //&viewparams=ident:b-2046234

    //https://guides.codepath.com/android/Fragment-Navigation-Drawer
    private DrawerLayout mDrawer;
    private Toolbar toolbar;
    private NavigationView nvDrawer;
    private ActionBarDrawerToggle drawerToggle;

    MapView map;
    ITileSource tmapyOsmTiles;
    TilesOverlay hillShade;
    MyLocationNewOverlay myLocationOverlay;
    RadiusMarkerClusterer vehiclesOverlay;

    GpsMyLocationProvider locationProvider;

    private LocationManager mLocMgr;
    private Timer reloadDataTimer;
    private static final int MAP_UPDATE_INTERVAL_MS = 10000;
    private static final String RELOAD_ENABLED = "reloadEnabled";

    private static int TEXT_SIZE_DIP = 12; //size of text over icons

    private ProgressDialog progress = null;
    private TextView mVehiclesTextView;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        if (!sharedPreferences.contains(RELOAD_ENABLED))
            sharedPreferences.edit().putBoolean(RELOAD_ENABLED, true).apply();

        // Set a Toolbar to replace the ActionBar. In order to slide our navigation drawer over the ActionBar,
        // we need to use the new Toolbar widget as defined in the AppCompat v21 library.
        // The Toolbar can be embedded into your view hierarchy
        // which makes sure that the drawer slides over the ActionBar.
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Find our drawer view
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        // Find our drawer view
        nvDrawer = (NavigationView) findViewById(R.id.nvView);
        // Setup drawer view
        setupDrawerContent(nvDrawer);

        drawerToggle = new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        // Tie DrawerLayout events to the ActionBarToggle
        mDrawer.setDrawerListener(drawerToggle);

        // Setup listener for checkbox
        Menu menu = nvDrawer.getMenu();

        MenuItem reloadMenuItem = menu.findItem(R.id.reload_menu_item);
        LinearLayout reloadLinearLayout = (LinearLayout) MenuItemCompat.getActionView(reloadMenuItem);
        SwitchCompat relaodSwitchCompat = (SwitchCompat) reloadLinearLayout.findViewById(R.id.reload_switch);
        relaodSwitchCompat.setChecked(sharedPreferences.getBoolean(RELOAD_ENABLED, false));
        relaodSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                if (isChecked) {
                    ScheduleLoadMarkers();
                    sharedPreferences.edit().putBoolean(RELOAD_ENABLED, true).apply();
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.menu_reloading_enabled), Toast.LENGTH_SHORT).show();
                } else {
                    if (reloadDataTimer != null) reloadDataTimer.cancel();
                    sharedPreferences.edit().putBoolean(RELOAD_ENABLED, false).apply();
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.menu_reloading_disabled), Toast.LENGTH_SHORT).show();
                }
            }
        });

        tmapyOsmTiles = new OnlineTileSourceBase("T-MAPY OSM", 0, 18, 256, "",
                new String[]{"http://services6.tmapserver.cz/geoserver/gwc/service/gmaps?layers=services6:osm_bing&zoom="}) {
            @Override
            public String getTileURLString(MapTile aTile) {
                return getBaseUrl() + aTile.getZoomLevel() + "&y=" + aTile.getY() + "&x=" + aTile.getX()
                        + mImageFilenameEnding;
            }
        };

        //INIT MAP
        if (android.os.Build.VERSION.SDK_INT < 23) {
            MapInit();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            MapInit();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, getResources().getString(R.string.perm_write_cache), Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        //TRY TO LOCATE USER
        if (android.os.Build.VERSION.SDK_INT < 23) {
            LocateMe();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocateMe();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
    }

    /**
     * Start service to register for notifications
     */
    private void registerForNotifications() {
        if (PlayServicesUtils.checkPlayServices(this)) {
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, GcmRegistrationService.class);
            intent.setAction(GcmRegistrationService.INTENT_ACTION_REGISTER_NOTIFICATIONS);
            startService(intent);
        }
    }

    /**
     * Unregister notifications
     */
    private void unRegisterForNotifications() {
        if (PlayServicesUtils.checkPlayServices(this)) {
            sharedPreferences.edit().putString("pref_topic1", "").apply();
            sharedPreferences.edit().putString("pref_topic2", "").apply();
            sharedPreferences.edit().putString("pref_topic3", "").apply();
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, GcmRegistrationService.class);
            intent.setAction(GcmRegistrationService.INTENT_ACTION_UNREGISTER_NOTIFICATIONS);
            startService(intent);
        }
    }

    /**
     * Register topic
     * @param topic
     */
    private void registerTopic(String topic){
        if (PlayServicesUtils.checkPlayServices(this)){
            Intent intent = new Intent(this, GcmRegistrationService.class);
            intent.setAction(GcmRegistrationService.INTENT_ACTION_REGISTER_TOPIC);
            intent.putExtra(GcmRegistrationService.INTENT_EXTRA_TOPIC, topic);
            startService(intent);
        }
    }
    /**
     * Nastavení reakcí na nabídky v navigation drawer
     *
     * @param navigationView
     */
    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.nav_tm_osm:
                                map.setTileSource(tmapyOsmTiles);
                                Toast.makeText(MainActivity.this, "T-MAPY OSM", Toast.LENGTH_SHORT).show();
                                break;
                            case R.id.nav_mapnik:
                                map.setTileSource(TileSourceFactory.MAPNIK);
                                Toast.makeText(MainActivity.this, "Mapnik", Toast.LENGTH_SHORT).show();
                                break;
                            case R.id.settings_menu_item:
                                Intent intent = new Intent(getApplicationContext(), Settings.class);
                                startActivity(intent);
                                break;
                            case R.id.github_item:
                                Uri uri = Uri.parse("https://github.com/T-MAPY/IREDOViewer");
                                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                                break;
                            default:
                                //Toast.makeText(MainActivity.this, "not implemented", Toast.LENGTH_SHORT).show();
                        }

                        // Highlight the selected item, update the title, and close the drawer
                        menuItem.setChecked(true);
                        //setTitle(menuItem.getTitle());
                        mDrawer.closeDrawers();
                        return true;
                    }
                });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // Aktualizuje lokální proměnné při změně konfigurace
        switch (key) {
            case "pref_enable_notifications":
                if (prefs.getBoolean(key, true)) {
                    if (prefs.getString(GcmRegistrationService.GCM_TOKEN, null) == null) {
                        //TRY TO LOCATE USER
                        if (android.os.Build.VERSION.SDK_INT < 23) {
                            registerForNotifications();
                        } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
                            registerForNotifications();
                        } else {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.GET_ACCOUNTS)) {
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.perm_get_accounts), Toast.LENGTH_LONG).show();
                            }
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.GET_ACCOUNTS}, 3);
                        }
                    }
                } else {
                    unRegisterForNotifications();
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.menu_notif_disabled), Toast.LENGTH_SHORT).show();
                }
                break;
            case "pref_topic1":
            case "pref_topic2":
            case "pref_topic3":
                String topic = prefs.getString(key, null);
                registerTopic(topic);
                Toast.makeText(MainActivity.this, topic + " " + getResources().getString(R.string.registered_topic), Toast.LENGTH_SHORT).show();
                break;
            case "pref_favorite1":
                String linefirst = prefs.getString(key, null);
                Button firstFavoriteButton = (Button) findViewById(R.id.map_favorite1);
                firstFavoriteButton.setText(linefirst);
                if (!"".equals(linefirst)) {
                    firstFavoriteButton.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.registered_favorite) + " " + linefirst, Toast.LENGTH_SHORT).show();
                } else
                    firstFavoriteButton.setVisibility(View.INVISIBLE);
                break;
            case "pref_favorite2":
                String linesecond = prefs.getString(key, null);
                Button secondFavoriteButton = (Button) findViewById(R.id.map_favorite2);
                secondFavoriteButton.setText(linesecond);
                if (!"".equals(linesecond)) {
                    secondFavoriteButton.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.registered_favorite) + " " + linesecond, Toast.LENGTH_SHORT).show();
                } else
                    secondFavoriteButton.setVisibility(View.INVISIBLE);
                break;
            case "pref_favorite3":
                String linethird = prefs.getString(key, null);
                Button thirdFavoriteButton = (Button) findViewById(R.id.map_favorite3);
                thirdFavoriteButton.setText(linethird);
                if (!"".equals(linethird)) {
                    thirdFavoriteButton.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.registered_favorite) + " " + linethird, Toast.LENGTH_SHORT).show();
                } else
                    thirdFavoriteButton.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permission, int[] grantResults) {
        if (grantResults != null && grantResults.length > 0)
            try {
                switch (requestCode) {
                    case 1:
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            MapInit();
                        } else {
                            Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 2:
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            LocateMe();
                        } else {
                            Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 3:
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            registerForNotifications();
                        } else {
                            Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        super.onRequestPermissionsResult(requestCode, permission, grantResults);
                        break;
                }
            } catch (SecurityException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
    }

    /**
     * Initialize map
     */
    private void MapInit() {
        map = (MapView) findViewById(R.id.map);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        map.setTileSource(tmapyOsmTiles);

        GeoPoint startPoint = new GeoPoint(50.215512, 15.811845);
        final IMapController mapController = map.getController();
        mapController.setZoom(15);
        mapController.setCenter(startPoint);

        final MapTileProviderBasic tileProvider = new MapTileProviderBasic(getApplicationContext());
        final ITileSource tileSource = new OnlineTileSourceBase("T-MAPY HillShade", 0, 18, 256, "",
                new String[]{"http://services6.tmapserver.cz/geoserver/gwc/service/gmaps?layers=services6:hillshade&zoom="}) {
            @Override
            public String getTileURLString(MapTile aTile) {
                return getBaseUrl() + aTile.getZoomLevel() + "&y=" + aTile.getY() + "&x=" + aTile.getX()
                        + mImageFilenameEnding;
            }
        };
        tileProvider.setTileSource(tileSource);
        hillShade = new TilesOverlay(tileProvider, this.getBaseContext());
        hillShade.setLoadingBackgroundColor(Color.TRANSPARENT);
        map.getOverlays().add(hillShade);

        //Init vehicles overlay
        mVehiclesTextView = (TextView) findViewById(R.id.map_vehicles_count);
        vehiclesOverlay = new RadiusMarkerClusterer(getApplication());
        Drawable clusterIconD = ContextCompat.getDrawable(getBaseContext(), R.drawable.cluster_icon);
        Bitmap clusterIcon = ((BitmapDrawable) clusterIconD).getBitmap();
        vehiclesOverlay.setIcon(clusterIcon);
        vehiclesOverlay.getTextPaint().setTextSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE_DIP, getResources().getDisplayMetrics()));
        vehiclesOverlay.getTextPaint().setFakeBoldText(true);
        vehiclesOverlay.getTextPaint().setColor(Color.DKGRAY);
        map.getOverlays().add(vehiclesOverlay);

        //Add Scale Bar
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(this);
        map.getOverlays().add(myScaleBarOverlay);

        //Add Compass
        CompassOverlay compassOverlay = new CompassOverlay(this, map);
        compassOverlay.enableCompass();
        map.getOverlays().add(compassOverlay);

        //Add Minimap
        //MinimapOverlay minimapOverlay = new MinimapOverlay(getBaseContext(), map.getTileRequestCompleteHandler());
        //minimapOverlay.setOptionsMenuEnabled(true);
        //map.getOverlays().add(minimapOverlay);

        //GpsMyLocationProvider can be replaced by your own class. It provides the position information through GPS or Cell towers.
        locationProvider = new GpsMyLocationProvider(getBaseContext());
        //minimum distance for update
        locationProvider.setLocationUpdateMinDistance(100);
        //minimum time for update
        locationProvider.setLocationUpdateMinTime(30000);
        myLocationOverlay = new MyLocationNewOverlay(getBaseContext(), locationProvider, map);
        myLocationOverlay.setDrawAccuracyEnabled(true);
        myLocationOverlay.disableFollowLocation();
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        map.postInvalidate();

        final Button firstFavoriteButton = (Button) findViewById(R.id.map_favorite1);
        firstFavoriteButton.setAlpha(0.5f);
        firstFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean found = false;
                Iterator itr = vehiclesOverlay.getItems().iterator();
                while (itr.hasNext()) {
                    Marker element = (Marker) itr.next();
                    if (element.getTitle().contains(sharedPreferences.getString("pref_favorite1", ""))) {
                        map.getController().animateTo(element.getPosition());
                        found = true;
                        Toast.makeText(MainActivity.this, "Posun na linku " + firstFavoriteButton.getText(), Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                if (!found)
                    Toast.makeText(MainActivity.this, "Linka " + firstFavoriteButton.getText() + " momentálně nejede", Toast.LENGTH_SHORT).show();
            }
        });
        String f1 = sharedPreferences.getString("pref_favorite1", null);
        if (f1 != null && !"".equals(f1)) {
            firstFavoriteButton.setText(sharedPreferences.getString("pref_favorite1", null));
            firstFavoriteButton.setVisibility(View.VISIBLE);
        }

        final Button secondFavoriteButton = (Button) findViewById(R.id.map_favorite2);
        secondFavoriteButton.setAlpha(0.5f);
        secondFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean found = false;
                Iterator itr = vehiclesOverlay.getItems().iterator();
                while (itr.hasNext()) {
                    Marker element = (Marker) itr.next();
                    if (element.getTitle().contains(sharedPreferences.getString("pref_favorite2", ""))) {
                        map.getController().animateTo(element.getPosition());
                        found = true;
                        Toast.makeText(MainActivity.this, "Posun na linku " + secondFavoriteButton.getText(), Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                if (!found)
                    Toast.makeText(MainActivity.this, "Linka " + secondFavoriteButton.getText() + " momentálně nejede", Toast.LENGTH_SHORT).show();
            }
        });
        String f2 = sharedPreferences.getString("pref_favorite2", null);
        if (f2 != null && !"".equals(f2)) {
            secondFavoriteButton.setText(sharedPreferences.getString("pref_favorite2", null));
            secondFavoriteButton.setVisibility(View.VISIBLE);
        }

        final Button thirdFavoriteButton = (Button) findViewById(R.id.map_favorite3);
        thirdFavoriteButton.setAlpha(0.5f);
        thirdFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean found = false;
                Iterator itr = vehiclesOverlay.getItems().iterator();
                while (itr.hasNext()) {
                    Marker element = (Marker) itr.next();
                    if (element.getTitle().contains(sharedPreferences.getString("pref_favorite3", ""))) {
                        map.getController().animateTo(element.getPosition());
                        found = true;
                        Toast.makeText(MainActivity.this, "Posun na linku " + thirdFavoriteButton.getText(), Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                if (!found)
                    Toast.makeText(MainActivity.this, "Linka " + thirdFavoriteButton.getText() + " momentálně nejede", Toast.LENGTH_SHORT).show();
            }
        });
        String f3 = sharedPreferences.getString("pref_favorite3", null);
        if (f3 != null && !"".equals(f3)) {
            thirdFavoriteButton.setText(sharedPreferences.getString("pref_favorite3", null));
            thirdFavoriteButton.setVisibility(View.VISIBLE);
        }

        ImageButton gotoLocationButton = (ImageButton) findViewById(R.id.map_goto_location);
        gotoLocationButton.setAlpha(0.5f);
        gotoLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocateMe();
            }
        });

        if (sharedPreferences.getBoolean(RELOAD_ENABLED, false)) {
            ScheduleLoadMarkers();
        }
    }

    /**
     * Schedules timer for data reloading
     */
    private void ScheduleLoadMarkers() {
        if (reloadDataTimer != null) //to prevent schedule timer multipletimes
            reloadDataTimer.cancel();

        progress = ProgressDialog.show(this, getResources().getString(R.string.data_loading_title),
                getResources().getString(R.string.data_loading_message), true);

        reloadDataTimer = new Timer();
        reloadDataTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                LoadMarkers();
            }

        }, 0, MAP_UPDATE_INTERVAL_MS);
    }

    /**
     * Load markers of buses and trains to the map
     */
    private void LoadMarkers() {
        if (isNetworkOnline()) {
            DownloadGeoJsonFile downloadSpojeGeoJsonFile = new DownloadGeoJsonFile(1);
            downloadSpojeGeoJsonFile.execute(mServiceUrlBase + mSpojeUrl);
        } else
        {
            if (progress != null) progress.dismiss();
            Toast.makeText(MainActivity.this, getResources().getString(R.string.no_connection), Toast.LENGTH_SHORT).show();
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

    /**
     * Async task to load GeoJson data
     */
    private class DownloadGeoJsonFile extends AsyncTask<String, Void, String> {
        private int mLayerId = 0;

        public DownloadGeoJsonFile(int mLayerId) {
            super();
            this.mLayerId = mLayerId;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                Log.d(TAG, "Data loading...");
                URL url = new URL(params[0]);
                // Open a stream from the URL
                InputStream stream = url.openStream();

                String line;
                StringBuilder result = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                while ((line = reader.readLine()) != null) {
                    // Read and save each line of the stream
                    result.append(line);
                }

                // Close the stream
                reader.close();
                stream.close();
                Log.d(TAG, "Loading finished");
                return result.toString();
            } catch (IOException e) {
                Log.e(TAG, "GeoJSON file could not be read");
            }
            return null;
        }

        @Override
        protected void onPostExecute(String jsonString) {
            if (jsonString != null) {
                switch (mLayerId) {
                    case 1:
                        KmlDocument kmlDocument = new KmlDocument();
                        kmlDocument.parseGeoJSON(jsonString);

                        vehiclesOverlay.getItems().clear();
                        vehiclesOverlay.invalidate();
                        if (kmlDocument != null) {
                            vehiclesOverlay.getItems().clear();

                            for (KmlFeature feature : kmlDocument.mKmlRoot.mItems) {
                                if (feature.hasGeometry(KmlPoint.class)) {
                                    final KmlFeature feature1 = feature;
                                    final Marker marker = new Marker(map);
                                    KmlPlacemark placemark = (KmlPlacemark) feature;
                                    KmlPoint geometry = (KmlPoint) placemark.mGeometry;
                                    marker.setPosition(geometry.getPosition());

                                    String fromTo = feature.getExtendedData("dep_time") + " " + feature.getExtendedData("dep");
                                    fromTo = fromTo + "<br>" + feature.getExtendedData("dest_time") + " " + feature.getExtendedData("dest");
                                    marker.setSnippet(fromTo);

                                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    try {
                                        Date date = format.parse(feature.getExtendedData("time"));
                                        marker.setSubDescription(getResources().getString(R.string.tooltip_state) + " " + new SimpleDateFormat("dd.MM. HH:mm:ss").format(date));
                                    } catch (ParseException e) {
                                        Log.e(TAG, e.getLocalizedMessage(), e);
                                        marker.setSubDescription(feature.getExtendedData("time"));
                                    }
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                                    //marker.setAnchor(Marker.ANCHOR_LEFT, Marker.ANCHOR_BOTTOM); //for pins

                                    String[] parts = feature.getExtendedData("dest").split(",");
                                    String textToIcon = parts[0];
                                    if ("b".equals(feature.getExtendedData("type"))) {
                                        marker.setTitle("Bus " + feature.getExtendedData("line_number") + " / " + feature.getExtendedData("service_number"));
                                        //marker.setIcon(writeOnDrawable(R.drawable.bus48x48, textToIcon));
                                        marker.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.bus48x48));
                                    } else {
                                        marker.setTitle(feature.mName);
                                        //marker.setIcon(writeOnDrawable(R.drawable.rail48x48, textToIcon));
                                        marker.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.rail48x48));
                                    }

                                    marker.setRelatedObject(feature);
                                    vehiclesOverlay.add(marker);
                                }
                            }
                        }

                        if (mVehiclesTextView != null)
                            mVehiclesTextView.setText(String.valueOf(vehiclesOverlay.getItems().size()) + " " + getResources().getString(R.string.map_vehicles));

                        map.invalidate();
                        Log.d(TAG, "Map updated");
                        break;
                }
            }
            if (progress != null) progress.dismiss();
        }
    }

    /**
     * Draw text onto icon
     *
     * @param drawableId
     * @param text
     * @return
     */
    public BitmapDrawable writeOnDrawable(int drawableId, String text) {

        Bitmap originalIcon = BitmapFactory.decodeResource(getResources(), drawableId).copy(Bitmap.Config.ARGB_8888, true);

        Paint paint = new Paint();
        paint.setTextSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics())); //http://stackoverflow.com/questions/3061930/how-to-set-unit-for-paint-settextsize
        paint.setColor(Color.BLACK);
        float textLength = paint.measureText(text);

        if (originalIcon.getWidth() < textLength) {
            Bitmap newBitmap = Bitmap.createBitmap(Math.round(textLength), originalIcon.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(newBitmap);
            canvas.drawBitmap(originalIcon, (newBitmap.getWidth() - originalIcon.getWidth()) / 2, 0, null);
            canvas.drawText(text, 0, originalIcon.getHeight() / 2, paint);

            return new BitmapDrawable(getResources(), newBitmap);
        } else {
            Canvas canvas = new Canvas(originalIcon);
            canvas.drawText(text, (originalIcon.getWidth() - textLength) / 2, originalIcon.getHeight() / 2, paint);

            return new BitmapDrawable(getResources(), originalIcon);
        }
    }

    /**
     * Accureate location
     */
    protected void LocateMe() {
        Location lastKnownLocation = null;
        if (locationProvider != null)
            try {
                lastKnownLocation = locationProvider.getLastKnownLocation();
            } catch (SecurityException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
        if (lastKnownLocation != null) {
            int lat = (int) (lastKnownLocation.getLatitude() * 1E6);
            int lng = (int) (lastKnownLocation.getLongitude() * 1E6);
            GeoPoint gpt = new GeoPoint(lat, lng);
            map.getController().animateTo(gpt);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        if (reloadDataTimer != null) reloadDataTimer.cancel();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        if (sharedPreferences.getBoolean(RELOAD_ENABLED, false)) ScheduleLoadMarkers();
    }
}
