package com.example.windapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.layers.LegendInfo;
import com.esri.arcgisruntime.layers.WmsLayer;
import com.esri.arcgisruntime.layers.WmsSublayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.ogc.wms.WmsLayerInfo;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.security.UserCredential;
import com.esri.arcgisruntime.util.ListenableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {


    private MapView mMapView;
    private static final String TAG = MainActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TERRAIN_WITH_LABELS_VECTOR, 34.041327, -118.466147, 7);
        //add WMS layers
        List<String> layerNames = new ArrayList<>();
        layerNames.add("45");
        layerNames.add("47");
        final WmsLayer wmsLayer = new WmsLayer("https://nowcoast.noaa.gov/arcgis/services/nowcoast/forecast_meteoceanhydro_sfc_ndfd_windspeed_offsets/MapServer/WMSServer?request=GetCapabilities&service=WMS",layerNames);
        //add portal credentials
        UserCredential creds = new UserCredential("aallie_usctrojan", "bergamot2018");
        Portal portal = new Portal("https://usctrojan.maps.arcgis.com");
        portal.setCredential(creds);
        portal.loadAsync();
        //bring in site suitability layer
        PortalItem portalItem1 = new PortalItem(portal, "6f795dedc98b48798908ee03208f1595");
        FeatureLayer featureLayer1 = new FeatureLayer(portalItem1, 0);
        //bring in turbine layer
        PortalItem portalItem2 = new PortalItem(portal, "b84f0540b67f47c4ae0de6b37f3c753d");
        FeatureLayer turbineLayer = new FeatureLayer(portalItem2, 0);
        //bring in transmission line layer
        PortalItem portalItem3 = new PortalItem(portal, "355b7958be19432c9add4d0e3ee79b40");
        FeatureLayer lineLayer = new FeatureLayer(portalItem3, 0);
        //add layers to map
        map.getOperationalLayers().add(wmsLayer);
        map.getOperationalLayers().add(featureLayer1);
        map.getOperationalLayers().add(turbineLayer);
        map.getOperationalLayers().add(lineLayer);
        ServiceFeatureTable mServiceFeatureTable = new ServiceFeatureTable("https://services8.arcgis.com/H8zcmiVIQ7Ap2hNU/arcgis/rest/services/Wind_Farm_Placements/FeatureServer/0");
        FeatureLayer nfeaturelayer = new FeatureLayer(mServiceFeatureTable);
        map.getOperationalLayers().add(nfeaturelayer);
        //set callout for wms info
        wmsLayer.fetchLegendInfosAsync();
        //Point windpoint = new Point(34.659302, -118.776469);
        // get callout, set content and show
        // add a listener to detect taps on the map view
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(MainActivity.this, mMapView) {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                android.graphics.Point point = new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY()));
                Point mapPoint = mMapView.screenToLocation(point);
                // add a new feature to the service feature table
                addFeature(mapPoint, mServiceFeatureTable);
                return super.onSingleTapConfirmed(e);
            }
        });
        mMapView.setMap(map);
    }
    /**
     * Adds a new Feature to a ServiceFeatureTable and applies the changes to the
     * server.
     *
     * @param mapPoint     location to add feature
     * @param featureTable service feature table to add feature
     */
    private void addFeature(Point mapPoint, final ServiceFeatureTable featureTable) {

        // create default attributes for the feature
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("Site", "potential");

        // creates a new feature using default attributes and point
        Feature feature = featureTable.createFeature(attributes, mapPoint);
        // check if feature can be added to feature table
        if (featureTable.canAdd()) {
            // add the new feature to the feature table and to server
            featureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(featureTable));
        } else {
            runOnUiThread(() -> logToUser(true, "error_cannot_add_to_feature_table"));
        }
    }
    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable) {

        // apply the changes to the server
        final ListenableFuture<List<FeatureEditResult>> editResult = featureTable.applyEditsAsync();
        editResult.addDoneListener(() -> {
            try {
                List<FeatureEditResult> editResults = editResult.get();
                // check if the server edit was successful
                if (editResults != null && !editResults.isEmpty()) {
                    if (!editResults.get(0).hasCompletedWithErrors()) {
                        runOnUiThread(() -> logToUser(false, "potential sites added"));
                    } else {
                        throw editResults.get(0).getError();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                runOnUiThread(() -> logToUser(true, "error applying edits"));
            }
        });
    }

    private void logToUser(boolean isError, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (isError) {
            Log.e(TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }

    @Override
    protected void onPause(){
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }
}
