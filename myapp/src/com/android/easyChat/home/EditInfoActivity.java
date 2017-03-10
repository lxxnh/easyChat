
package com.android.easyChat.home;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.Person;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class EditInfoActivity extends Activity {
    private int[] headIconIds = {
            R.drawable.blue_bird,
            R.drawable.green_bird,
            R.drawable.green_pig,
            R.drawable.pig_egg,
            R.drawable.red_bird,
            R.drawable.white_bird,
            R.drawable.yellow_bird
    };

    private SpinAdapter adapter = null;
    private Spinner spin = null;
    private SharedPreferences pre = null;
    private SharedPreferences.Editor editor = null;
    private int headIconPos = 0;
    TextView myName;
    EditText editName;
    Boolean iconChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_info);
        MainService mService = ((MyAppApplication) getApplication()).getService();

        myName = (TextView) findViewById(R.id.myName);
        editName = (EditText) findViewById(R.id.edit_name);
        ImageView headIcon = (ImageView) findViewById(R.id.myHeadIcon);
        TextView myMac = (TextView) findViewById(R.id.myMac);
        TextView myIp = (TextView) findViewById((R.id.myIp));

        pre = PreferenceManager.getDefaultSharedPreferences(this);
        editor = pre.edit();
        myName.setText(pre.getString("nickeName", android.os.Build.MODEL));
        headIcon.setImageResource(pre.getInt("headIconId", R.drawable.white_bird));
        myMac.setText(mService.getMyMac());
        myIp.setText(mService.getMyIp());

        ImageView editNameBtn = (ImageView) findViewById(R.id.edit_name_btn);
        editNameBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                myName.setVisibility(View.GONE);
                editName.setVisibility(View.VISIBLE);

            }
        });

        spin = (Spinner) findViewById(R.id.icon_chooser);
        adapter = new SpinAdapter();
        spin.setAdapter(adapter);
        spin.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long arg3) {
                iconChange = true;
                headIconPos = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }
        });

        ImageView editIconBtn = (ImageView) findViewById(R.id.edit_icon_btn);
        editIconBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                spin.setVisibility(View.VISIBLE);
            }
        });

        TextView finish = (TextView) findViewById(R.id.finish);
        finish.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                saveSettings();
                finish();
            }
        });
        // =========取消按钮功能 add by chenlu================
        TextView back = (TextView) findViewById(R.id.back);
        back.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                finish();
            }
        });
    }

    private class SpinAdapter implements SpinnerAdapter {

        @Override
        public int getCount() {
            return headIconIds.length;
        }

        @Override
        public Object getItem(int pos) {
            return null;
        }

        @Override
        public long getItemId(int pos) {
            return 0;
        }

        @Override
        public int getItemViewType(int pos) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = getLayoutInflater().inflate(R.layout.head_image_spinner_layout, null);
            ImageView hicon = (ImageView) convertView.findViewById(R.id.headericon);
            hicon.setImageResource(headIconIds[position]);
            TextView hnote = (TextView) convertView.findViewById(R.id.headernote);
            hnote.setText(getString(R.string.header) + position);
            hnote.setTextColor(Color.BLACK);
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver arg0) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver arg0) {

        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent);
        }

    }

    private void saveSettings() {
        EditText nikeName = (EditText) findViewById(R.id.edit_name);
        if (nikeName.getText() != null && (!(nikeName.getText().toString().equals("")))) {
            Log.d("chenlu", "name = " + nikeName.getText().toString());
            editor.putString("nickeName", nikeName.getText().toString());
        }
        if (iconChange) {
            editor.putInt("headIconPos", headIconPos);
            editor.putInt("headIconId", headIconIds[headIconPos]);
        }
        editor.commit();
        Intent intent = new Intent();
        intent.setAction(Constant.updateMyInformationAction);
        sendBroadcast(intent);
    }
}
