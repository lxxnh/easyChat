
package com.android.easyChat.home;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Person;

public class SetNickNameActivity extends Activity {

    private SharedPreferences pre = null;
    private SharedPreferences.Editor editor = null;
    TextView myName;
    EditText editName;
    MainService mService;
    Person person;
    Map<String, String> nickNameMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_nickname);
        person = (Person) getIntent().getExtras().getSerializable("person");
        mService = ((MyAppApplication) getApplication()).getService();

        myName = (TextView) findViewById(R.id.myName);
        editName = (EditText) findViewById(R.id.edit_name);
        ImageView headIcon = (ImageView) findViewById(R.id.headIcon);
        TextView myMac = (TextView) findViewById(R.id.myMac);
        TextView myIp = (TextView) findViewById((R.id.myIp));

        pre = PreferenceManager.getDefaultSharedPreferences(this);
        editor = pre.edit();
        myName.setText(person.personNickeName);
        myMac.setText(person.macAddress);
        myIp.setText(person.ipAddress);

        ImageView editNameBtn = (ImageView) findViewById(R.id.edit_name_btn);
        editNameBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                myName.setVisibility(View.GONE);
                editName.setVisibility(View.VISIBLE);

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

    private void saveSettings() {
        EditText nikeName = (EditText) findViewById(R.id.edit_name);
        if (nikeName.getText() != null && (!(nikeName.getText().toString().equals("")))) {
            nickNameMap = mService.getNickNameMap();
            nickNameMap.put(person.macAddress, nikeName.getText().toString());
            Set<String> nickNameSet = new HashSet<String>();
            Set<String> nickNameSet1 = new HashSet<String>();
            nickNameSet = nickNameMap.keySet();
            Iterator<String> it = nickNameSet.iterator();
            while (it.hasNext()) {
                String apply = it.next();
                apply = apply + "," + nickNameMap.get(apply);
                nickNameSet1.add(apply);
            }
            editor.putStringSet("nickNameMap", nickNameSet1);
        }
        editor.commit();
    }
}
