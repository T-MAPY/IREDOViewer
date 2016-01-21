package cz.tmapy.android.iredoviewer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlFeature;
import org.osmdroid.bonuspack.kml.KmlLineString;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.bonuspack.kml.KmlPoint;
import org.osmdroid.bonuspack.kml.KmlPolygon;
import org.osmdroid.bonuspack.kml.Style;
import org.osmdroid.bonuspack.overlays.FolderOverlay;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.util.constants.MapViewConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements MapViewConstants {

    private final static String mLogTag = "IREDOViewerMap";
    private final String mSpojeUrl = "http://tabule.oredo.cz/geoserver/iredo/ows?service=WFS&version=1.0.0&request=GetFeature&typeName=iredo:service_currentposition&maxFeatures=1000&outputFormat=application/json";

    MapView map;
    MyLocationOverlay myLocationOverlay = null;

    private LocationManager mLocMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //check API version
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

        if (android.os.Build.VERSION.SDK_INT < 23) {
            LocateMe();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocateMe();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Persmission is needed to locate your position", Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }

        LoadMarkers();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permission, int[] grantResults) {
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
            Log.e(mLogTag, e.getLocalizedMessage(), e);
        }
    }

    private void MapInit() {
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPQUESTOSM);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(50.215512, 15.811845);
        IMapController mapController = map.getController();
        mapController.setZoom(15);
        mapController.setCenter(startPoint);

        //Add Scale Bar
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(this);
        map.getOverlays().add(myScaleBarOverlay);

        //Add MyLocationOverlay
        myLocationOverlay = new MyLocationOverlay(this, map);
        map.getOverlays().add(myLocationOverlay);
        map.postInvalidate();
    }

    private void LoadMarkers() {
        DownloadGeoJsonFile downloadSpojeGeoJsonFile = new DownloadGeoJsonFile(1);
        downloadSpojeGeoJsonFile.execute(mSpojeUrl);
    }

    private class DownloadGeoJsonFile extends AsyncTask<String, Void, String> {
        private int mLayerId = 0;

        public DownloadGeoJsonFile(int mLayerId) {
            super();
            this.mLayerId = mLayerId;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
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

                return result.toString();
            } catch (IOException e) {
                Log.e(mLogTag, "GeoJSON file could not be read");
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

                        KmlFeature.Styler styler = new MyStyler(kmlDocument);
                        FolderOverlay kmlOverlay = (FolderOverlay) kmlDocument.mKmlRoot.buildOverlay(map, null, styler, kmlDocument);

                        map.getOverlays().add(kmlOverlay);
                        map.invalidate();

                        //BoundingBoxE6 bb = kmlDocument.mKmlRoot.getBoundingBox();
                        //  map.zoomToBoundingBox(bb);
                        break;
                }
            }
        }
    }

    /**
     * Styler calss for buses and trains
     */
    private class MyStyler implements KmlFeature.Styler {

        KmlDocument mKmlDocument;

        Drawable busMarker = getResources().getDrawable(R.drawable.push_pin_red);
        Bitmap busBitmap = ((BitmapDrawable) busMarker).getBitmap();
        Style busStyle = new Style(busBitmap, 0x901010AA, 3.0f, 0x20AA1010);

        Drawable trainMarker = getResources().getDrawable(R.drawable.push_pin_blue);
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
            marker.setAnchor(Marker.ANCHOR_LEFT, Marker.ANCHOR_BOTTOM);

            if ("b".equals(kmlPlacemark.getExtendedData("type"))) {
                String title = kmlPlacemark.getExtendedData("line_number") + " / " + kmlPlacemark.getExtendedData("service_number");
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
     * Move map to user location
     */
    protected void LocateMe() {
        mLocMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location lastKnownLocation = null;
        try {
            lastKnownLocation = mLocMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException e) {
            Log.e(mLogTag, e.getLocalizedMessage(), e);
        }
        if (lastKnownLocation != null) {
            int lat = (int) (lastKnownLocation.getLatitude() * 1E6);
            int lng = (int) (lastKnownLocation.getLongitude() * 1E6);
            GeoPoint gpt = new GeoPoint(lat, lng);
            map.getController().setCenter(gpt);
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableCompass();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        myLocationOverlay.disableMyLocation();
        myLocationOverlay.disableCompass();
    }
}
