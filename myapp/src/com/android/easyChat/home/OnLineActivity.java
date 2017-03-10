
package com.android.easyChat.home;

import java.util.ArrayList;
import java.util.Map;

import javax.security.auth.PrivateCredentialPermission;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.Person;

import android.os.Bundle;
import android.R.integer;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class OnLineActivity extends Activity {
    private static Map<String, Person> usersMap = null;// 在线非好友
    private static ArrayList<String> userKeys = null;
    private ListView userList;
    private MainService mService = null;
    private MyAppApplication myAppApplication;
    private TextView back;
    private TextView count;
    OnlineBroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_on_line);
        myAppApplication = (MyAppApplication) getApplication();
        mService = myAppApplication.getService();
        regBroadcastRecv();// 注册广播接收器
        userList = (ListView) findViewById(R.id.userList);
        count = (TextView) findViewById(R.id.count);
        back = (TextView) findViewById(R.id.back);
        back.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                finish();
            }
        });
        upadteUsersAapter();// 绘制当前在线非好友列表
    }

    // ==============注册广播接收器===================
    private void regBroadcastRecv() {
        receiver = new OnlineBroadcastReceiver();
        IntentFilter bFilter = new IntentFilter();
        bFilter.addAction(Constant.personHasChangedAction);
        registerReceiver(receiver, bFilter);
    }

    private class OnlineBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            upadteUsersAapter();// 绘制当前在线非好友列表
        }
    }

    private void upadteUsersAapter() {
        usersMap = mService.getUsersMap();
        userKeys = mService.getUserKeys();
        Log.d("lxx1", "**************in onlineactivity---" + usersMap.toString() + "userkeys:"
                + userKeys.toString() + "--co=");
        UserAdapter userAadpter = new UserAdapter(OnLineActivity.this,
                userKeys, usersMap);
        userList.setAdapter(userAadpter);
        if (usersMap != null) {
            count.setText(usersMap.size() + "");
        } else {
            count.setText("0");
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    private class UserAdapter extends BaseAdapter {
        private Map<String, Person> usersMap = null;
        private ArrayList<String> userKeys = null;
        private LayoutInflater mInflater;

        public UserAdapter(Context context, ArrayList<String> userKeys,
                Map<String, Person> usersMap) {
            this.mInflater = LayoutInflater.from(context);
            this.usersMap = usersMap;
            this.userKeys = userKeys;
        }

        @Override
        public int getCount() {
            if (userKeys != null) {
                return userKeys.size();
            } else {
                return 0;
            }

        }

        @Override
        public Object getItem(int arg0) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getItemId(int arg0) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public View getView(int position, View itemView, ViewGroup arg2) {
            ViewHolder holder;
            if (itemView == null) {
                holder = new ViewHolder();
                itemView = mInflater.inflate(R.layout.users_item, null);
                holder.icon = (ImageView) itemView.findViewById(R.id.headIcon);
                holder.name = (TextView) itemView.findViewById(R.id.name);
                holder.ip = (TextView) itemView.findViewById(R.id.ip);
                holder.add = (Button) itemView.findViewById(R.id.addButton);
                itemView.setTag(holder);
            } else {
                holder = (ViewHolder) itemView.getTag();
            }

            String key = userKeys.get(position);
            Person person = new Person();
            person = usersMap.get(key);
            holder.icon.setImageResource(person.getPersonHeadIconId());// 解决在线好友列表用户头像显示单一头像问题 add
                                                                       // by chelu
            holder.name.setText(person.getPersonNickeName());
            holder.ip.setText(person.getIpAddress());
            holder.add.setOnClickListener(new AddOnClickListener(person));
            return itemView;
        }

        class ViewHolder {
            ImageView icon;
            TextView name;
            TextView ip;
            Button add;

        }

        public class AddOnClickListener implements OnClickListener {
            private Person person;

            public AddOnClickListener(Person person) {
                this.person = person;
            }

            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(OnLineActivity.this, SendApplyActivity.class);
                intent.putExtra("PersonInfo", person);
                startActivity(intent);
                // finish();
            }

        }
    }

}
