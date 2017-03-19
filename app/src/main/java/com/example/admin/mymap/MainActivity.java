package com.example.admin.mymap;

import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.Projection;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.animation.Animation;
import com.amap.api.maps.model.animation.ScaleAnimation;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.map)
    MapView mMapView;
    MyLocationStyle myLocationStyle;
    AMap aMap;
    AMapLocationClient mlocationClient;
    AMapLocationClientOption mLocationOption;//参数
    LocationSource.OnLocationChangedListener mListener;
    double loctionLat;
    double loctionLong;
    private UiSettings mUiSettings;
    boolean isFirstLoc = true;//首次定位
    private ArrayList<MarkerOptions> markerOptionsList = new ArrayList<MarkerOptions>();// 所有的marker
    private ArrayList<MarkerOptions> markerOptionsListInView = new ArrayList<MarkerOptions>();// 视野内的marker
    private int height;// 屏幕高度(px)
    private int width;// 屏幕宽度(px)
    private Boolean addMarkerFirst = false;
    Handler timeHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    // 更新markers
                    resetMarks();
                    break;
                case 1:
                    if (!addMarkerFirst) {
                        addMarkers();
                    }
                    addMarkerFirst = true;


                    break;
            }
        }
    };

    /**
     * 更新matket 点聚合
     */
    private void resetMarks() {
        System.out.println("markerOptionsList.size():"
                + markerOptionsList.size());
        // 开始刷新车辆
        Projection projection = aMap.getProjection();
        Point p = null;
        markerOptionsListInView.clear();
        // 获取在当前视野内的marker;提高效率
        for (MarkerOptions mp : markerOptionsList) {
            p = projection.toScreenLocation(mp.getPosition());
            if (p.x < 0 || p.y < 0 || p.x > width || p.y > height) {
                // 不添加到计算的列表中
            } else {
                markerOptionsListInView.add(mp);
            }
        }
        // 自定义的聚合类MyMarkerCluster
        ArrayList<MyMarkerCluster> clustersMarker = new ArrayList<MyMarkerCluster>();
        for (MarkerOptions mp : markerOptionsListInView) {
            if (clustersMarker.size() == 0) {
                clustersMarker.add(new MyMarkerCluster(MainActivity.this,
                        mp, projection, 60));// 100根据自己需求调整
            } else {
                boolean isIn = false;
                for (MyMarkerCluster cluster : clustersMarker) {
                    if (cluster.getBounds().contains(mp.getPosition())) {
                        cluster.addMarker(mp);
                        isIn = true;
                        break;
                    }
                }
                if (!isIn) {
                    clustersMarker.add(new MyMarkerCluster(
                            MainActivity.this, mp, projection, 60));
                }
            }
        }
        // 设置聚合点的位置和icon
        for (MyMarkerCluster mmc : clustersMarker) {
            mmc.setpositionAndIcon();
        }
        aMap.clear(true);
        // 重新添加
        for (MyMarkerCluster cluster : clustersMarker) {
            aMap.addMarker(cluster.getOptions());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mMapView.onCreate(savedInstanceState);
        initWh();
        initLocationStyle();
        initLocationClient();
        initAMap();
        initListener();
    }

    private void initListener() {


        /**
         * 地图缩放监听
         */
        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {

            }

            @Override
            public void onCameraChangeFinish(CameraPosition cameraPosition) {
                timeHandler.sendEmptyMessage(0);
            }
        });
        aMap.setOnMarkerClickListener(new AMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                LatLng latLng1 = new LatLng(loctionLat, loctionLong);

                float distance = AMapUtils.calculateLineDistance(latLng1, marker.getPosition());//位置与宝藏的距离
                Toast.makeText(MainActivity.this, "距离" + distance, Toast.LENGTH_SHORT).show();
                marker.showInfoWindow();

                return true;
            }
        });
        //设置定位回调监听
        mlocationClient.setLocationListener(new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation amapLocation) {
                //注意设置合适的定位时间的间隔，并且在合适时间调用removeUpdates()方法来取消定位请求
                if (mListener != null && amapLocation != null) {
                    if (amapLocation != null && amapLocation.getErrorCode() == 0) {
                        // 如果不设置标志位，此时再拖动地图时，它会不断将地图移动到当前的位置
                        if (isFirstLoc) {
                            //设置缩放级别
                            aMap.moveCamera(CameraUpdateFactory.zoomTo(17));

                            //将地图移动到定位点
                            aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude())));

                            //点击定位按钮 能够将地图的中心移动到定位点
                            mListener.onLocationChanged(amapLocation);// 显示系统小蓝点,好像要覆盖mylocationsType
// 设置定位的类型为定位模式，有定位、跟随或地图根据面向方向旋转几种
                            aMap.setMyLocationStyle(myLocationStyle);
                            isFirstLoc = false;

                            Toast.makeText(MainActivity.this, "当前地址：" + amapLocation.getAddress(), Toast.LENGTH_SHORT).show();
                        }


                        loctionLat = amapLocation.getLatitude();
                        loctionLong = amapLocation.getLongitude();
                        timeHandler.sendEmptyMessage(1);//加载marker


                    } else {
                        String errText = "定位失败," + amapLocation.getErrorCode() + ": " + amapLocation.getErrorInfo();
                        Log.e("AmapErr", errText);
                    }

                } else {

                }
            }
        });
    }


    private void addMarkers() {
        for (int i = 0; i < 200; i++) {
            LatLng latLng = new LatLng(Math.random() * 0.1 + loctionLat,
                    Math.random() * 0.1 + loctionLong);
            MarkerOptions m = new MarkerOptions();
            markerOptionsList.add(m.position(latLng)
                    .title("Marker" + i)
                    .icon(BitmapDescriptorFactory
                            .defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        }


    }

    /**
     * 地上生长的Marker
     */
    private void startGrowAnimation(Marker growMarker) {
        if (growMarker != null) {
            Animation animation = new ScaleAnimation(0, 1, 0, 1);
            animation.setInterpolator(new LinearInterpolator());
            //整个移动所需要的时间
            animation.setDuration(1000);
            //设置动画
            growMarker.setAnimation(animation);
            //开始动画
            growMarker.startAnimation();
        }
    }

    /**
     * 获取宽高
     */
    private void initWh() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
    }

    private void initAMap() {
        if (aMap == null) {
            aMap = mMapView.getMap();
        }
        // 设置定位监听
        aMap.setLocationSource(new LocationSource() {
            @Override
            public void activate(OnLocationChangedListener onLocationChangedListener) {
                mListener = onLocationChangedListener;
            }

            @Override
            public void deactivate() {

            }
        });

        //aMap.getUiSettings().setMyLocationButtonEnabled(true);设置默认定位按钮是否显示，非必需设置。

// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap.setMyLocationEnabled(true);

        //infowindow设置自定义样式
        aMap.setInfoWindowAdapter(new InfoWindowAdapter());


        mUiSettings = aMap.getUiSettings();
        mUiSettings.setTiltGesturesEnabled(false);// 禁用倾斜手势。
        mUiSettings.setRotateGesturesEnabled(false);// 禁用旋转手势。

    }

    private void initLocationClient() {
        //初始化定位
        mlocationClient = new AMapLocationClient(this);
        //初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        //是否一次定位
        mLocationOption.setOnceLocation(false);


        //设置为高精度定位模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption.setInterval(3000);

        //设置定位参数
        mlocationClient.setLocationOption(mLocationOption);
        // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
        // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
        // 在定位结束后，在合适的生命周期调用onDestroy()方法
        // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
        mlocationClient.startLocation();//启动定位

    }

    private void initLocationStyle() {
        myLocationStyle = new MyLocationStyle();//初始化定位蓝点样式类
        myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));// 设置圆形的边框颜色  
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));// 设置圆形的填充颜色  

        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);//

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mMapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mMapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mMapView.onSaveInstanceState(outState);
    }

    class InfoWindowAdapter implements AMap.InfoWindowAdapter {

        @BindView(R.id.infowindow_bn)
        Button infowindowBn;

        @Override
        public View getInfoWindow(Marker marker) {
            View infoWindow = getLayoutInflater().inflate(R.layout.my_infowindow, null);
            final TextView textView= (TextView) infoWindow.findViewById(R.id.infowindow_tv);
            textView.setText(marker.getTitle());
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    textView.setText("点过了");
                }
            });
            return infoWindow;
        }

        @Override
        public View getInfoContents(Marker marker) {
            View infoContents = getLayoutInflater().inflate(R.layout.my_infowindow_contents, null);
            //ButterKnife.bind(MainActivity.this, infoContents);

            return infoContents;
        }
        @OnClick({R.id.infowindow_bn,R.id.infowindow_tv})
        public void OnClick (View view){

            Toast.makeText(MainActivity.this, "我是那个bn", Toast.LENGTH_SHORT).show();
        }
    }
}
