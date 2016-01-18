package cz.tmapy.android.iredoviewer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.geojson.GeoJsonFeature;
import com.google.maps.android.geojson.GeoJsonLayer;
import com.google.maps.android.geojson.GeoJsonPoint;
import com.google.maps.android.geojson.GeoJsonPointStyle;
import com.google.maps.android.ui.IconGenerator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private final static String mLogTag = "IREDOViewerMap";
    private final String mSpojeUrl = "http://tabule.oredo.cz/geoserver/iredo/ows?service=WFS&version=1.0.0&request=GetFeature&typeName=iredo:service_currentposition&maxFeatures=1000&outputFormat=application/json";
    private GoogleMap mMap;
    private GeoJsonLayer mSpojeLayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        addMarkersToMap();
    }


    private void addMarkersToMap() {
        // Add a marker in Sydney and move the camera
        //LatLng tmapy = new LatLng(50.209709, 15.832632);
        //mMap.addMarker(new MarkerOptions().position(tmapy).title("T-MAPY spol. s r.o."));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(tmapy));
        //mMap.moveCamera(CameraUpdateFactory.zoomTo(5));

        DownloadGeoJsonFile downloadSpojeGeoJsonFile = new DownloadGeoJsonFile(1);
        downloadSpojeGeoJsonFile.execute(mSpojeUrl);
    }

    private void createMarkers(GeoJsonLayer layer) {

        IconGenerator iconFactory = new IconGenerator(this);
        // Iterate over all the features stored in the layer
        for (GeoJsonFeature feature : layer.getFeatures()) {
            // Check if the magnitude property exists
            if (feature.hasProperty("type") && feature.hasProperty("dep") && feature.hasProperty("dep_time") && feature.hasProperty("dest") && feature.hasProperty("dest_time") && feature.hasProperty("line_number") && feature.hasProperty("service_number")) {

                String type = feature.getProperty("type");
                String dep = feature.getProperty("dep");
                String dep_time = feature.getProperty("dep_time");
                String dest = feature.getProperty("dest");
                String dest_time = feature.getProperty("dest_time");
                String line = feature.getProperty("line_number");
                String service = feature.getProperty("service_number");

                // Create a new point style
                GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();

                // Set options for the point style
                if (!TextUtils.isEmpty(type) && type.equals("b")) {
                    iconFactory.setStyle(IconGenerator.STYLE_GREEN);
                    pointStyle.setTitle(line + "/" + service);
                } else if (!TextUtils.isEmpty(type) && type.equals("t")) {
                    iconFactory.setStyle(IconGenerator.STYLE_BLUE);
                    pointStyle.setTitle(service);
                }
                pointStyle.setIcon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(dest)));
                pointStyle.setAnchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());
                pointStyle.setSnippet(dep_time + " " + dep + " > " + dest_time + " " + dest);

                // Assign the point style to the feature
                feature.setPointStyle(pointStyle);
            }
        }
    }

    private void SetClustering(GeoJsonLayer layer) {
        ClusterManager<MyItem> mClusterManager = new ClusterManager<MyItem>(this, mMap);
        mMap.setOnCameraChangeListener(mClusterManager);
        for (GeoJsonFeature feature : layer.getFeatures()) {
            mClusterManager.addItem(new MyItem(feature));
        }
    }

    public class MyItem implements ClusterItem {
        private final GeoJsonFeature mFeature;

        public MyItem(GeoJsonFeature feature) {
            mFeature = feature;
        }

        @Override
        public LatLng getPosition() {
            return ((GeoJsonPoint) mFeature.getGeometry()).getCoordinates();
        }
    }

    private class DownloadGeoJsonFile extends AsyncTask<String, Void, JSONObject> {
        private int mLayerId = 0;

        public DownloadGeoJsonFile(int mLayerId) {
            super();
            this.mLayerId = mLayerId;
        }

        @Override
        protected JSONObject doInBackground(String... params) {
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

                // Convert result to JSONObject
                return new JSONObject(result.toString());
            } catch (IOException e) {
                Log.e(mLogTag, "GeoJSON file could not be read");
            } catch (JSONException e) {
                Log.e(mLogTag, "GeoJSON file could not be converted to a JSONObject");
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            if (jsonObject != null) {
                switch (mLayerId) {
                    case 1:
                        mSpojeLayer = new GeoJsonLayer(mMap, jsonObject);
                        createMarkers(mSpojeLayer);
                        mSpojeLayer.addLayerToMap();
                        //SetClustering(mSpojeLayer);
                        break;
                }
            }
        }
    }
}
