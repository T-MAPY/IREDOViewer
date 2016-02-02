package cz.tmapy.android.iredoviewer;

import android.Manifest;
import android.app.ProgressDialog;
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
import android.os.AsyncTask;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "IREDOViewerMap";
    private final String mSpojeUrl = "http://tabule.oredo.cz/geoserver/iredo/ows?service=WFS&version=1.0.0&request=GetFeature&typeName=iredo:service_currentposition&maxFeatures=1000&outputFormat=application/json";

    private DrawerLayout mDrawer;
    private Toolbar toolbar;
    private NavigationView nvDrawer;
    ActionBarDrawerToggle drawerToggle;

    MapView map;
    MyLocationNewOverlay myLocationOverlay = null;
    RadiusMarkerClusterer vehiclesOverlay = null;

    GpsMyLocationProvider locationProvider = null;

    private LocationManager mLocMgr;
    private Timer reloadDataTimer;
    private static int MAP_UPDATE_INTERVAL_MS = 10000;

    private static int TEXT_SIZE_DIP = 12; //size of text over icons

    private ProgressDialog progress = null;
    private TextView mVehiclesTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set a Toolbar to replace the ActionBar.
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
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawer.openDrawer(GravityCompat.START);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.nav_first_fragment:
                                //fragmentClass = FirstFragment.class;
                                break;
                            case R.id.nav_second_fragment:
                                //fragmentClass = SecondFragment.class;
                                break;
                            default:
                                //fragmentClass = FirstFragment.class;
                        }

                        // Highlight the selected item, update the title, and close the drawer
                        menuItem.setChecked(true);
                        setTitle(menuItem.getTitle());
                        mDrawer.closeDrawers();
                        return true;
                    }
                });
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
        //map.setTileSource(TileSourceFactory.MAPQUESTOSM);
        map.setTileSource(new OnlineTileSourceBase("T-MAPY OSM", 0, 18, 256, "",
                new String[]{"http://services6.tmapserver.cz/geoserver/gwc/service/gmaps?layers=services6:osm_bing&zoom="}) {
            @Override
            public String getTileURLString(MapTile aTile) {
                return getBaseUrl() + aTile.getZoomLevel() + "&y=" + aTile.getY() + "&x=" + aTile.getX()
                        + mImageFilenameEnding;
            }
        });

        GeoPoint startPoint = new GeoPoint(50.215512, 15.811845);
        final IMapController mapController = map.getController();
        mapController.setZoom(15);
        mapController.setCenter(startPoint);

        mVehiclesTextView = (TextView) findViewById(R.id.map_vehicles_count);

        //Init vehicles overlay
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

        ImageButton gotoLocationButton = (ImageButton) findViewById(R.id.map_goto_location);
        gotoLocationButton.setAlpha(0.5f);
        gotoLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocateMe();
            }
        });

        progress = ProgressDialog.show(this, getResources().getString(R.string.data_loading_title),
                getResources().getString(R.string.data_loading_message), true);
        ScheduleLoadMarkers();
    }

    /**
     * Schedules timer for data reloading
     */
    private void ScheduleLoadMarkers() {
        if (reloadDataTimer != null) //to prevent schedule timer multipletimes
            reloadDataTimer.cancel();

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
        DownloadGeoJsonFile downloadSpojeGeoJsonFile = new DownloadGeoJsonFile(1);
        downloadSpojeGeoJsonFile.execute(mSpojeUrl);
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
                                        marker.setIcon(writeOnDrawable(R.drawable.bus48x48, textToIcon));
                                    } else {
                                        marker.setTitle(feature.mName);
                                        marker.setIcon(writeOnDrawable(R.drawable.rail48x48, textToIcon));
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
    protected void onStop(){
        super.onStop();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        if (reloadDataTimer != null) reloadDataTimer.cancel();
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        ScheduleLoadMarkers();
    }
}
