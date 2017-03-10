
package com.android.easyChat.home;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.Person;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;

public class ApplyActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ApplyActivity";

    private ListView applyList;
    private MainService mService = null;
    private static Map<String, Person> applyFriendsMap = null;
    private ArrayList<String> macs = null;
    private MyAppApplication myAppApplication;
    private TextView backTextView;
    private ImageView delete;
    private Map<String, String> applyMessageMap;
    ApplyBroadcastReceiver broadcastRecv = null;
    private ArrayList<Person> agreeApplyList = new ArrayList<Person>();
    private ArrayList<Person> refuseApplyList = new ArrayList<Person>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apply);
        myAppApplication = (MyAppApplication) getApplication();
        mService = myAppApplication.getService();
        regBroadcastRecv();
        applyList = (ListView) (findViewById(R.id.applyList));
        updateAdapter();// 绘制好友验证列表
        backTextView = (TextView) findViewById(R.id.back);
        backTextView.setOnClickListener(this);
        delete = (ImageView) findViewById(R.id.delete);
        delete.setOnClickListener(this);
    }

    // =========注册广播接收器=================
    private void regBroadcastRecv() {
        broadcastRecv = new ApplyBroadcastReceiver();
        IntentFilter bFilter = new IntentFilter();
        bFilter.addAction(Constant.personApplyAction);
        bFilter.addAction(Constant.agreeApplyAction);
        bFilter.addAction(Constant.refuseApplyAction);
        registerReceiver(broadcastRecv, bFilter);
    }

    private class ApplyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            if (intent.getAction().equals(Constant.personApplyAction)) {
                // 收到广播后，更新好友验证列表
                updateAdapter();
            }
            if (intent.getAction().equals(Constant.agreeApplyAction)) {
                Log.d("chenlu22", "=======接收到广播==========");
                Person p = (Person) intent.getExtras().getSerializable("applyPerson");
                agreeApplyList.add(p);
                // mService.agreeAddFriend(p);
            }
            if (intent.getAction().equals(Constant.refuseApplyAction)) {
                Log.d("chenlu22", "=======接收到广播==========");
                Person p = (Person) intent.getExtras().getSerializable("applyPerson");
                refuseApplyList.add(p);
                // mService.refuseAddFriend(p);
            }
        }

    }

    // 绘制好友验证列表
    private void updateAdapter() {
        applyFriendsMap = mService.getUsersMap();
        macs = mService.getApplyKeys();
        applyMessageMap = mService.getApplyMessageMap();
        Log.d("lxx", "ApplyActivity updateAdapter macs.size=" + macs.size());
        for (int i = 0; i < macs.size(); i++) {
            if (!applyFriendsMap.containsKey(macs.get(i))) {
                macs.remove(i);
            }
        }
        Log.d("lxx", "ApplyActivity updateAdapter macs.size=" + macs.size());
        ApplyAdapter adapter = new ApplyAdapter(ApplyActivity.this, macs, applyFriendsMap);
        applyList.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (agreeApplyList.size() > 0) {
            for (int i = 0; i < agreeApplyList.size(); i++) {
                Person p = agreeApplyList.get(i);
                mService.agreeAddFriend(p);
            }
        }
        if (refuseApplyList.size() > 0) {
            for (int i = 0; i < refuseApplyList.size(); i++) {
                Person p = refuseApplyList.get(i);
                mService.refuseAddFriend(p);
            }
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(broadcastRecv);
    }

    // ===================好友验证适配器==================================
    private class ApplyAdapter extends BaseAdapter {
        private Map<String, Person> applyFriendsMap = null;
        private ArrayList<String> macs = null;
        private LayoutInflater mInflater;

        public ApplyAdapter(Context context, ArrayList<String> macs,
                Map<String, Person> applyFriendsMap) {
            this.mInflater = LayoutInflater.from(context);
            this.applyFriendsMap = applyFriendsMap;
            this.macs = macs;
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return macs.size();
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
            final ViewHolder holder;

            if (itemView == null) {
                holder = new ViewHolder();
                itemView = mInflater.inflate(R.layout.apply_item, null);
                holder.headIcon = (ImageView) itemView.findViewById(R.id.headIcon);
                holder.name = (TextView) itemView.findViewById(R.id.name);
                holder.message = (TextView) itemView.findViewById(R.id.message);
                holder.agree = (Button) itemView.findViewById(R.id.agree);
                holder.disagree = (Button) itemView.findViewById(R.id.disagree);
                holder.applyStatus = (TextView) itemView.findViewById(R.id.apply_status);
                itemView.setTag(holder);
            } else {
                holder = (ViewHolder) itemView.getTag();
            }
            if (macs.size() > 0) {
                String key = macs.get(position);
                Person person = applyFriendsMap.get(key);
                holder.headIcon.setImageResource(person.personHeadIconId);
                holder.name.setText(person.getPersonNickeName());
                if (applyMessageMap != null && applyMessageMap.containsKey(key)) {
                    holder.message.setText(getResources().getString(R.string.applyMsg)
                            + applyMessageMap.get(key));
                }
                // ====================拒绝或同意时按钮消失，显示已拒绝或已同意 mod by chenlu====================

                holder.disagree.setOnClickListener(new DisAgreeClickLinster(person, holder.agree,
                        holder.disagree, holder.applyStatus));
                holder.agree.setOnClickListener(new AgreeClickLinster(person, holder.agree,
                        holder.disagree, holder.applyStatus));
            }

            return itemView;
        }

        // ====================拒绝或同意时按钮消失，显示已拒绝或已同意 mod by chenlu====================
        class ViewHolder {
            ImageView headIcon;
            TextView name;
            TextView message;
            Button agree;
            Button disagree;
            TextView applyStatus;

        }

        // ====================拒绝或同意时按钮消失，显示已拒绝或已同意 mod by chenlu====================
        public class AgreeClickLinster implements OnClickListener {
            private Person person;
            private Button button1;
            private Button button2;
            private TextView text;

            public AgreeClickLinster(Person person, Button button1, Button button2, TextView text) {
                this.person = person;
                this.button1 = button1;
                this.button2 = button2;
                this.text = text;

            }

            @Override
            public void onClick(View arg0) {
                button1.setVisibility(View.GONE);
                button2.setVisibility(View.GONE);
                text.setVisibility(View.VISIBLE);
                text.setText(R.string.has_agreed);
                Intent intent = new Intent();
                intent.setAction(Constant.agreeApplyAction);
                intent.putExtra("applyPerson", person);
                sendBroadcast(intent);
            }

        }
    }

    // ====================拒绝或同意时按钮消失，显示已拒绝或已同意 mod by chenlu====================
    class DisAgreeClickLinster implements OnClickListener {
        private Person person;
        private Button button1;
        private Button button2;
        private TextView text;

        public DisAgreeClickLinster(Person person, Button button1, Button button2, TextView text) {
            this.person = person;
            this.button1 = button1;
            this.button2 = button2;
            this.text = text;

        }

        @Override
        public void onClick(View arg0) {
            // mService.refuseAddFriend(person);
            button1.setVisibility(View.GONE);
            button2.setVisibility(View.GONE);
            text.setVisibility(View.VISIBLE);
            text.setText(R.string.has_disagreed);
            Intent intent = new Intent();
            intent.setAction(Constant.refuseApplyAction);
            intent.putExtra("applyPerson", person);
            sendBroadcast(intent);
        }

    }

    @Override
    public void onClick(View arg0) {
        // TODO Auto-generated method stub
        switch (arg0.getId()) {
            case R.id.back:
                finish();
                break;
            case R.id.delete:
                Log.d("lxx", "clearApplyList");
                clearApplyList();
                break;
        }
    }

    public void clearApplyList() {
        final Builder builder = new AlertDialog.Builder(ApplyActivity.this);
        builder.setTitle(R.string.delete_apply);
        builder.setMessage(R.string.confirm_delete_apply);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                // TODO Auto-generated method stub
                mService.clearApplyList();
                updateAdapter();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                // TODO Auto-generated method stub
                arg0.dismiss();
            }
        });
        builder.create().show();
    }

}
