package cz.tmapy.android.iredoviewer;

import android.Manifest;
import android.content.pm.PackageManager;
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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlFeature;
import org.osmdroid.bonuspack.kml.KmlLineString;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.bonuspack.kml.KmlPoint;
import org.osmdroid.bonuspack.kml.KmlPolygon;
import org.osmdroid.bonuspack.kml.Style;
import org.osmdroid.bonuspack.overlays.FolderOverlay;
import org.osmdroid.bonuspack.overlays.InfoWindow;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.MarkerInfoWindow;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
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
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "IREDOViewerMap";
    private final String mSpojeUrl = "http://tabule.oredo.cz/geoserver/iredo/ows?service=WFS&version=1.0.0&request=GetFeature&typeName=iredo:service_currentposition&maxFeatures=1000&outputFormat=application/json";

    MapView map;
    MyLocationNewOverlay myLocationOverlay = null;
    RadiusMarkerClusterer vehiclesOverlay = null;
    private static int MYLOCATION_OVERLAY_INDEX = 1;
    private static int VEHICLES_OVERLAY_INDEX = 2;
    GpsMyLocationProvider locationProvider = null;

    private LocationManager mLocMgr;
    private Timer reloadDataTimer;
    private static int MAP_UPDATE_INTERVAL_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //INIT MAP
        if (android.os.Build.VERSION.SDK_INT < 23) {
            MapInit();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            MapInit();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Persmission is needed to write map cache", Toast.LENGTH_LONG).show();
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

        LoadMarkers();

        reloadDataTimer = new Timer();
        reloadDataTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                LoadMarkers();
            }

        }, 0, MAP_UPDATE_INTERVAL_MS);
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
        map.setTileSource(TileSourceFactory.MAPQUESTOSM);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(50.215512, 15.811845);
        final IMapController mapController = map.getController();
        mapController.setZoom(15);
        mapController.setCenter(startPoint);

        //Add Scale Bar
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(this);
        map.getOverlays().add(myScaleBarOverlay);

        CompassOverlay compassOverlay = new CompassOverlay(this, map);
        compassOverlay.enableCompass();
        map.getOverlays().add(compassOverlay);

        //GpsMyLocationProvider can be replaced by your own class. It provides the position information through GPS or Cell towers.
        locationProvider = new GpsMyLocationProvider(this.getBaseContext());
        //minimum distance for update
        locationProvider.setLocationUpdateMinDistance(100);
        //minimum time for update
        locationProvider.setLocationUpdateMinTime(30000);
        myLocationOverlay = new MyLocationNewOverlay(this.getBaseContext(), locationProvider, map);
        myLocationOverlay.setDrawAccuracyEnabled(true);
        myLocationOverlay.disableFollowLocation();
        myLocationOverlay.enableMyLocation();

        map.getOverlays().add(MYLOCATION_OVERLAY_INDEX, myLocationOverlay);

        //Init vehicles overlay
        vehiclesOverlay = new RadiusMarkerClusterer(getApplication());
        Drawable clusterIconD = ContextCompat.getDrawable(getBaseContext(), R.drawable.cluster_icon);
        Bitmap clusterIcon = ((BitmapDrawable) clusterIconD).getBitmap();
        vehiclesOverlay.setIcon(clusterIcon);
        vehiclesOverlay.getTextPaint().setTextSize(32.0f);
        vehiclesOverlay.getTextPaint().setFakeBoldText(true);
        vehiclesOverlay.getTextPaint().setColor(Color.DKGRAY);

        map.getOverlays().add(VEHICLES_OVERLAY_INDEX, vehiclesOverlay);

        map.postInvalidate();

        ImageButton gotoLocationButton = (ImageButton) findViewById(R.id.map_goto_location);
        gotoLocationButton.setAlpha(0.5f);
        gotoLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocateMe();
            }
        });

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
                Log.d(TAG,"Data loading...");
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
                Log.d(TAG,"Loading finished");
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
                                        marker.setSubDescription("Stav k: " + new SimpleDateFormat("dd.MM. HH:mm:ss").format(date));
                                    } catch (ParseException e) {
                                        Log.e(TAG,e.getLocalizedMessage(),e);
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

                        map.invalidate();
                        Log.d(TAG, "Map updated");
                        break;

                    case 2:
                        KmlDocument stationsDocument = new KmlDocument();
                        stationsDocument.parseGeoJSON(jsonString);
                        KmlFeature.Styler styler = new MyStyler(stationsDocument);
                        FolderOverlay stationsOverlay = (FolderOverlay) stationsDocument.mKmlRoot.buildOverlay(map, null, styler, stationsDocument);
                        map.getOverlays().add(stationsOverlay);
                        break;
                }
            }
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
        paint.setTextSize(32.0f);
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
     * Styler calss for buses and trains
     */
    private class MyStyler implements KmlFeature.Styler {

        KmlDocument mKmlDocument;

        Drawable busMarker = ContextCompat.getDrawable(getBaseContext(), R.drawable.bus);
        Bitmap busBitmap = ((BitmapDrawable) busMarker).getBitmap();
        Style busStyle = new Style(busBitmap, 0x901010AA, 3.0f, 0x20AA1010);

        Drawable trainMarker = ContextCompat.getDrawable(getBaseContext(), R.drawable.rail);
        Bitmap trainBitmap = ((BitmapDrawable) trainMarker).getBitmap();
        Style trainStyle = new Style(trainBitmap, 0x901010AA, 3.0f, 0x20AA1010);


        public MyStyler(KmlDocument mKmlDocument) {
            this.mKmlDocument = mKmlDocument;

            mKmlDocument.putStyle("bus_style", new Style(busBitmap, 0x901010AA, 3.0f, 0x2010AA10));
            mKmlDocument.putStyle("train_style", new Style(trainBitmap, 0x901010AA, 3.0f, 0x2010AA10));
        }

        @Override
        public void onFeature(Overlay overlay, KmlFeature kmlFeature) {
        }

        @Override
        public void onPoint(Marker marker, KmlPlacemark kmlPlacemark, KmlPoint kmlPoint) {

            String fromTo = kmlPlacemark.getExtendedData("dep_time") + " " + kmlPlacemark.getExtendedData("dep");
            fromTo = fromTo + " - " + kmlPlacemark.getExtendedData("dest_time") + " " + kmlPlacemark.getExtendedData("dest");
            marker.setSnippet(fromTo);
            marker.setSubDescription(kmlPlacemark.getExtendedData("time"));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            //marker.setAnchor(Marker.ANCHOR_LEFT, Marker.ANCHOR_BOTTOM); //for pins

            if ("b".equals(kmlPlacemark.getExtendedData("type"))) {
                String title = "Bus " + kmlPlacemark.getExtendedData("line_number") + " / " + kmlPlacemark.getExtendedData("service_number");
                marker.setTitle(title);
                marker.setIcon(busMarker);
                //kmlPlacemark.mStyle = "bus_style";
                //kmlPoint.applyDefaultStyling(marker, busStyle, kmlPlacemark, mKmlDocument, map);
            } else {
                marker.setIcon(trainMarker);
                //kmlPlacemark.mStyle = "train_style";
                //kmlPoint.applyDefaultStyling(marker, trainStyle, kmlPlacemark, mKmlDocument, map);
            }
        }

        @Override
        public void onLineString(Polyline polyline, KmlPlacemark kmlPlacemark, KmlLineString kmlLineString) {
        }

        @Override
        public void onPolygon(Polygon polygon, KmlPlacemark kmlPlacemark, KmlPolygon kmlPolygon) {
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
    protected void onResume() {
        super.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }
}
