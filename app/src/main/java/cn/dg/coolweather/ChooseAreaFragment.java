package cn.dg.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.dg.coolweather.db.City;
import cn.dg.coolweather.db.County;
import cn.dg.coolweather.db.Province;
import cn.dg.coolweather.gson.Weather;
import cn.dg.coolweather.util.HttpUtil;
import cn.dg.coolweather.util.Utility;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();
    //省列表
    private List<Province> provinceList;
    //市列表
    private List<City> cityList;
    //县列表
    private List<County> countyList;
    //选中的省份
    private Province selectedProvince;
    //选中的城市
    private City selectedCity;
    //当前选中的级别
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        //获取一些控件的实例
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        //初始化ArrayAdapter
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);
        //设置为ListView适配器
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //[2]开始执行这个判断
                if (currentLevel == LEVEL_PROVINCE) {
                    //[3]获取当前点击的item
                    selectedProvince = provinceList.get(position);
                    //[4]获取市数据
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                }else if (currentLevel == LEVEL_COUNTY){
                    String weatherId = countyList.get(position).getWeatherId();
                    //instanceof 可以用来判断一个对象是否属于某一类的实例
                    if (getActivity() instanceof MainActivity){
                        //启动WeatherActivity 并把当前选中县的天气id传递过去
                        Intent intent = new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id", weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if (getActivity() instanceof WeatherActivity){
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }

                }
            }
        });
        //返回
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    //获取市数据 显示到界面上
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    //获取省数据 显示到界面上
                    queryProvinces();
                }
            }
        });
        //[1]首先显示所有的省份  当调用这个方法的时候 currentLevel = LEVEL_PROVINCE；
        queryProvinces();
    }

    /*
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器查询
     * */
    private void queryProvinces() {
        //将标题设置成中国
        titleText.setText("中国");
        //将返回按钮隐藏起来
        backButton.setVisibility(View.GONE);
        //省级列表不能再返回了 调用LitePal的查询接口来从数据库中读取省级数据
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            //清除集合中所有的数据 来刷新
            dataList.clear();
            for (Province province : provinceList) {
                //获取数据让数据添加到去list集合中
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            //setSelection(int position)这个方法的作用就是将第position个item显示在listView的最上面一项
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");
        }
    }

    /*
     * 查询选中省内所有的市，优先从数据库查询， 如果没有查询到再去服务器上查询
     * */
    private void queryCities() {
        //点击选中的名字
        titleText.setText(selectedProvince.getProvinceName());
        //按钮设置为可见
        backButton.setVisibility(View.VISIBLE);
        //通过用LitePal来查询 通过provinceid 来查市级
        cityList = DataSupport.where("provinceid = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            //清除省级数据
            dataList.clear();
            for (City city : cityList) {
                //添加市级数据
                dataList.add(city.getCityName());
            }
            //适配器刷新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            //将currentLevel 设置为LEVELCITY;
            currentLevel = LEVEL_CITY;
        } else {
            //因为获取到listview的监听事件 获取了当前点击的item 可以获取ProvinceCode；
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address, "city");
        }
    }

    /*
     * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询
     * */
    private void queryCounties() {
        //设置点击item 设置为Title
        titleText.setText(selectedCity.getCityName());
        //设置button为可见
        backButton.setVisibility(View.VISIBLE);
        //通过listview监听事件 获取了市级的id 可以通过市级的id 查询县级
        countyList = DataSupport.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            //将省级和市级的数据清除
            dataList.clear();
            for (County county : countyList) {
                //将县级的数据添加进去
                dataList.add(county.getCountyName());
            }
            //适配器刷新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            Log.d("ChooseAreaFragment", "provinceCode: "+provinceCode);
            Log.d("ChooseAreaFragment", "cityCode: "+cityCode);
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    /*
     * 根据传入的地址和类型从服务器上查询省市县数据
     *
     * */
    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //通过runOnUiThread()方法回到主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals(type)) {
                    //来解析和处理服务器返回的数据，并存储到数据库中。 返回值为Boolean
                    result = Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                }
                //改变视图必须在主线程调用 这里借助了runOnUiThread() 方法来实现从子线程切换到主线程 现在数据库中已经存在了数据，因为调用用queryProvinces()就会直接将数据显示到界面上了
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }


    /*
     * 显示进度对话框
     * */
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }



    /*
     * 关闭进度对话框
     * */

    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}
