
package com.android.easyChat.home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.FileName;
import com.android.easyChat.util.FileState;
import com.android.easyChat.util.Person;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class EasyChatMainActivity extends Activity implements View.OnClickListener {
    private ExpandableListView ev = null;
    private String[] groupIndicatorLabeles = null;
    private MyBroadcastRecv broadcastRecv = null;
    private IntentFilter bFilter = null;
    private ArrayList<Map<String, Person>> children = null;
    private ArrayList<ArrayList<String>> onlineKeys = null;
    private MainService mService = null;
    private Intent mMainServiceIntent = null;
    private ExListAdapter adapter = null;
    private Person me = null;
    private Person person = null;
    private AlertDialog dialog = null;
    private boolean isPaused = false;// �жϱ����ǲ��ǿɼ�
    private ArrayList<FileState> receivedFileNames = null;// ���յ��ĶԷ����������ļ���
    private ArrayList<FileState> beSendFileNames = null;// ���͵��Է����ļ�����Ϣ
    private MyAppApplication myAppApplication;// add-by chenlu
    private static Map<String, Person> applyFriendsMap = null;
    private ArrayList<String> macs = null;
    private TextView applyMessageCount;
    private View applyPrompt;
    private static Map<String, String> nickNameMap;
    private Person person1;
    SharedPreferences pre;
    private AlertDialog receiveFileDialog;
    /**
     * MainService�����뵱ǰActivity�İ�������
     */
    private ServiceConnection sConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MainService.ServiceBinder) service).getService();
            myAppApplication.setService(mService);// add-by chenlu
            System.out.println("Service connected to activity...");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            System.out.println("Service disconnected to activity...");
        }
    };

    private ReceiveSendFileListAdapter receiveFileListAdapter = new ReceiveSendFileListAdapter(this);
    private ReceiveSendFileListAdapter sendFileListAdapter = new ReceiveSendFileListAdapter(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_feige);
        pre = PreferenceManager.getDefaultSharedPreferences(this);

        groupIndicatorLabeles = getResources().getStringArray(R.array.groupIndicatorLabeles);
        myAppApplication = (MyAppApplication) getApplication();// add-by chenlu
        // ��ǰActivity���̨MainService���а�
        mMainServiceIntent = new Intent(this, MainService.class);
        bindService(mMainServiceIntent, sConnection, BIND_AUTO_CREATE);
        startService(mMainServiceIntent);

        ev = (ExpandableListView) findViewById(R.id.main_list);
        applyPrompt = (View) findViewById(R.id.applyPrompt);
        applyMessageCount = (TextView) findViewById(R.id.apply_count);
        regBroadcastRecv();

        ImageView addFriend = (ImageView) findViewById(R.id.add_friends_btn);
        addFriend.setOnClickListener(new android.view.View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(EasyChatMainActivity.this, OnLineActivity.class);
                startActivity(intent);

            }
        });
        RelativeLayout applyMessage = (RelativeLayout) findViewById(R.id.apply_message);
        applyMessage.setOnClickListener(new android.view.View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(EasyChatMainActivity.this, ApplyActivity.class);
                startActivity(intent);

            }
        });
        ImageView headIcon = (ImageView) findViewById(R.id.my_head_icon);
        headIcon.setOnClickListener(new android.view.View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(EasyChatMainActivity.this, EditInfoActivity.class);
                startActivity(intent);
            }
        });

    }

    public void setApplyMessageCount() {
        Log.d("chenlu22", "=======setApplyMessageCount ====");
        // applyFriendsMap = mService.getUsersMap();
        // macs = mService.getApplyKeys();
        // for (int i = 0; i < macs.size(); i++) {
        // if(!applyFriendsMap.containsKey(macs.get(i))){
        // macs.remove(i);
        // }
        // }
        // if (macs != null) {
        // applyMessageCount.setText(macs.size()+"");
        // } else {
        // applyMessageCount.setText("0");
        // }
        Set<String> applyMessage = new HashSet<String>();
        applyMessage = pre.getStringSet("applyMessageKeys", applyMessage);
        Log.d("chenlu22", "applyMessage.size = " + applyMessage.size());
        if (applyMessage != null) {
            if (applyMessage.size() > 0) {
                applyPrompt.setVisibility(View.VISIBLE); // mod by chenlu.��֤��Ϣ����0ʱ����ɫԭ�����
                applyMessageCount.setText(applyMessage.size() + "");
            } else {
                applyPrompt.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        getMyInfomation();
        setApplyMessageCount();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastRecv);
        stopService(mMainServiceIntent);
        unbindService(sConnection);
    }

    // ==============================ExpandableListView����������===================================
    private class ExListAdapter extends BaseExpandableListAdapter {
        private Context context = null;

        public ExListAdapter(Context context) {
            this.context = context;
        }

        // ���ĳ���û�����
        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return children.get(groupPosition)
                    .get(onlineKeys.get(groupPosition).get(childPosition));
        }

        // ����û����û��б��е����
        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        // �����û�����View
        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parentView) {
            View view = getLayoutInflater().inflate(
                    R.layout.person_item_layout, null);// ����List�û���Ŀ���ֶ���;
            if (groupPosition < children.size()) {// ���groupPosition������ܴ�children�б��л��һ��children����
                if (children.get(groupPosition) != null) {
                    if (onlineKeys.get(groupPosition).size() != 0
                            && children.get(groupPosition).size() != 0) {
                        Log.d("lxx", childPosition + "+++++");
                        Person person = children.get(groupPosition).get(
                                onlineKeys.get(groupPosition)
                                        .get(childPosition));// ��õ�ǰ�û�ʵ��
                        view.setTag(person);// ���һ��tag����Ա��ڵ���¼��и��ݸñ�ǽ�����ش���
                        view.setOnClickListener(EasyChatMainActivity.this);
                        view.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

                            @Override
                            public void onCreateContextMenu(ContextMenu arg0, View arg1,
                                    ContextMenuInfo arg2) {
                                // TODO Auto-generated method stub
                                person1 = (Person) arg1.getTag();
                                arg0.add(0, 0, 0, R.string.edit_nickname);
                                arg0.add(0, 1, 0, R.string.delete_friend);
                            }
                        });
                        view.setPadding(30, 0, 0, 0);// ����������հ׾���
                        // holder = new ViewHolder();
                        ImageView headIconView = (ImageView) view
                                .findViewById(R.id.person_head_icon);// ͷ��
                        TextView nickeNameView = (TextView) view
                                .findViewById(R.id.person_nickename);// �ǳ�
                        TextView loginTimeView = (TextView) view
                                .findViewById(R.id.person_login_time);// ��¼ʱ��
                        TextView msgCountView = (TextView) view
                                .findViewById(R.id.person_msg_count);// δ����Ϣ����
                        // TextView ipaddressView =
                        // (TextView)view.findViewById(R.id.person_ipaddress);//IP��ַ
                        View prompt = (View) view.findViewById(R.id.message_prompt);
                        // =============��ʾ��һ������ʱ�� add by chenlu===============
                        TextView msgTime = (TextView) view.findViewById(R.id.msg_time);
                        String msg_time = "msg_time" + person.getMacAddress();
                        String timeContent = parseDate(pre.getString(msg_time, ""));
                        msgTime.setText(timeContent);

                        headIconView.setImageResource(person.personHeadIconId);
                        nickNameMap = mService.getNickNameMap();
                        if (!nickNameMap.containsKey(person.macAddress)) {
                            nickeNameView.setText(person.personNickeName);
                        } else {
                            nickeNameView.setText(nickNameMap
                                    .get(person.macAddress));
                        }
                        loginTimeView.setText(person.loginTime);
                        String msgCountStr = getString(R.string.init_msg_count);
                        // �����û�id��service���ø��û�����Ϣ����
                        // ===========δ����Ϣ������0ʱ��ʾ��ԲȦ mod by chenlu ================
                        if (mService.getMessagesCountByMac(person.macAddress) > 0) {
                            prompt.setVisibility(View.VISIBLE);
                            msgCountView.setText(String.format(msgCountStr, mService
                                    .getMessagesCountByMac(person.macAddress)));
                        }
                    }
                }
            }
            Log.d("chenlu20", "view = " + view);
            return view;
        }

        class ViewHolder {
            ImageView headIconView;
            TextView nickeNameView;
            TextView loginTimeView;
            TextView msgCountView;

        }

        // ���ĳ���û����е��û���
        @Override
        public int getChildrenCount(int groupPosition) {
            int childrenCount = 0;
            if (groupPosition < children.size())
                childrenCount = onlineKeys.get(groupPosition).size();
            return childrenCount;
        }

        // ���ý���û������
        @Override
        public Object getGroup(int groupPosition) {
            return children.get(groupPosition);
        }

        // ����û�������,�ô����û����������ص��������Ƶ�����
        @Override
        public int getGroupCount() {
            return groupIndicatorLabeles.length;
        }

        // ����û������
        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        // �����û��鲼��View
        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (isExpanded) {
                ev.setGroupIndicator(getResources().getDrawable(R.drawable.all_bird_open));
            } else {
                ev.setGroupIndicator(getResources().getDrawable(R.drawable.all_bird));
            }
            AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, 60);
            TextView textView = new TextView(context);
            textView.setLayoutParams(lp);
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            textView.setPadding(70, 0, 0, 0);
            int childrenCount = 0;
            if (groupPosition < children.size()) {// ���groupPosition����ܴ�children�б��л��children�������ø�children�����е��û�����
                childrenCount = onlineKeys.get(groupPosition).size();
            }
            textView.setText(groupIndicatorLabeles[groupPosition] + "(" + childrenCount + ")");
            return textView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }

    // =================================ExpandableListView��������������===================================================

    // ������ѵ������Ϣ
    private void getMyInfomation() {
        int iconId = pre.getInt("headIconId", R.drawable.white_bird);
        String nickeName = pre.getString("nickeName", android.os.Build.MODEL);
        ImageView myHeadIcon = (ImageView) findViewById(R.id.my_head_icon);
        myHeadIcon.setImageResource(iconId);
        TextView myNickeName = (TextView) findViewById(R.id.myName);
        myNickeName.setText(nickeName);
        me = new Person();
        me.personHeadIconId = iconId;
        me.personNickeName = nickeName;
    }

    // =============��ʾ��һ������ʱ��========================
    public String parseDate(String string) {
        if (string.equals("")) {
            return null;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            Date date = dateFormat.parse(string);
            SimpleDateFormat dateFormat2 = new SimpleDateFormat("HH:mm");// ֻ��ʾʱ��
            SimpleDateFormat dateFormat3 = new SimpleDateFormat("MM-dd");// ֻ��ʾ����
            SimpleDateFormat dateFormat4 = new SimpleDateFormat("yyyy-MM-dd");// ͬһ��
            String currentDate = dateFormat4.format(new Date());
            String msgTime = dateFormat4.format(date);
            if (currentDate.equals(msgTime)) {
                return dateFormat2.format(date);// ���ʱͬһ�����Ϣ����ֻ��ʾʱ��
            } else {
                return dateFormat3.format(date);
            }
        } catch (java.text.ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
        /*
         * case R.id.myinfo_panel://����ϵͳ���ô��� showSettingDialog(); break;
         */
            case R.id.person_item_layout:// ת������Ϣҳ��
                person = (Person) view.getTag();// �û��б��childView�����ʱ
                Log.d("lxx", "onlineKeys.get(0).contains(person.personId)---"
                        + onlineKeys.get(0).contains(person.personId));
                // if(onlineKeys.get(0).contains(person.personId)){
                // final Builder builder = new AlertDialog.Builder(this);
                // builder.setTitle("tianjiahaoyou");
                // builder.setMessage("querentianjia"+person.personNickeName+"weihaoyou?");
                // builder.setPositiveButton("queren",new OnClickListener() {
                //
                // @Override
                // public void onClick(DialogInterface arg0, int arg1) {
                // // TODO Auto-generated method stub
                // Intent intent = new Intent();
                // intent.setAction(Constant.addFriendRequestAction);
                // intent.putExtra("person", person);
                // sendBroadcast(intent);
                // }
                // });
                // builder.setNegativeButton("quxiao", new OnClickListener() {
                //
                // @Override
                // public void onClick(DialogInterface arg0, int arg1) {
                // // TODO Auto-generated method stub
                // arg0.dismiss();
                // }
                // });
                // builder.create().show();
                // }else
                openChartPage(person);
                break;
        }
    }

    boolean finishedSendFile = false;// ��¼��ǰ��Щ�ļ��ǲ��Ǳ����Ѿ����չ���

    // =========================�㲥������==========================================================
    private class MyBroadcastRecv extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constant.updateMyInformationAction)) {
                getMyInfomation();
            } else if (intent.getAction().equals(Constant.dataReceiveErrorAction)
                    || intent.getAction().equals(Constant.dataSendErrorAction)) {
                Toast.makeText(EasyChatMainActivity.this, intent.getExtras().getString("msg"),
                        Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(Constant.receiveAddFriendAction)) {
                setApplyMessageCount();
            } else if (intent.getAction().equals(Constant.fileReceiveStateUpdateAction)) {// �յ����Է������ļ�����״̬֪ͨ
                if (!isPaused) {
                    receivedFileNames = mService.getReceivedFileNames();// ��õ�ǰ�����ļ�����״̬
                    receiveFileListAdapter.setResources(receivedFileNames);
                    receiveFileListAdapter.notifyDataSetChanged();// �����ļ������б�
                }
            } else if (intent.getAction().equals(Constant.fileSendStateUpdateAction)) {// �յ����Է������ļ�����״̬֪ͨ
                if (!isPaused) {
                    beSendFileNames = mService.getBeSendFileNames();// ��õ�ǰ�����ļ�����״̬
                    sendFileListAdapter.setResources(beSendFileNames);
                    sendFileListAdapter.notifyDataSetChanged();// �����ļ������б�
                }
            } else if (intent.getAction().equals(Constant.remoteUserRefuseReceiveFileAction)) {
                Toast.makeText(EasyChatMainActivity.this, getString(R.string.refuse_receive_file),
                        Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(Constant.personHasChangedAction)) {
                children = mService.getChildren();
                onlineKeys = mService.getOnlineKeys();
                Log.d("lxx1", "children.size()=" + children.size() + "--onlineKeys.size()="
                        + onlineKeys.size() + "");
                if (null == adapter) {
                    adapter = new ExListAdapter(EasyChatMainActivity.this);
                    ev.setAdapter(adapter);
                    ev.expandGroup(0);
                    ev.setGroupIndicator(getResources().getDrawable(R.drawable.all_bird));
                }
                adapter.notifyDataSetChanged();
            } else if (intent.getAction().equals(Constant.hasMsgUpdatedAction)) {
                adapter.notifyDataSetChanged();
            } else if (intent.getAction().equals(Constant.fileReceiveOrSendFinished)) {
                if (receiveFileDialog != null) {
                    receiveFileDialog.dismiss();
                }
            } else if (intent.getAction().equals(Constant.addFriendRequestAction)) {
                Log.d("lxx", "------------receive");
                // Toast.makeText(FlyPigeonMainActivity.this, "�ɹ�������Ӻ�������", Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(Constant.receivedSendFileRequestAction)) {// ���յ��ļ�����������������ļ�
                if (!isPaused) {// ��������ڿɼ�״̬����Ӧ�㲥,����һ����ʾ���Ƿ�Ҫ���շ��������ļ�
                    receivedFileNames = mService.getReceivedFileNames();// �ӷ������������Ҫ���յ��ļ����ļ���
                    if (receivedFileNames.size() <= 0)
                        return;
                    receiveFileListAdapter.setResources(receivedFileNames);
                    Person psn = (Person) intent.getExtras().get("person");
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(psn.personNickeName);
                    builder.setMessage(R.string.sending_file_to_you);
                    builder.setIcon(psn.personHeadIconId);
                    View vi = getLayoutInflater().inflate(R.layout.request_file_popupwindow_layout,
                            null);
                    builder.setView(vi);
                    final AlertDialog recDialog = builder.show();
                    receiveFileDialog = recDialog;
                    recDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface arg0) {
                            receivedFileNames.clear();
                            if (!finishedSendFile) {// ��������ļ���δ���վ͹رս��մ��ڣ�˵���������ν��գ�ͬʱ��Զ�̷���һ���ܾ����յ�ָ�
                                Intent intent = new Intent();
                                intent.setAction(Constant.refuseReceiveFileAction);
                                sendBroadcast(intent);
                            }
                            finishedSendFile = false;// �ر��ļ����նԻ��򣬱���ʾ�����ļ�������ɣ��ѱ����ļ�����״̬��Ϊfalse
                        }
                    });
                    ListView lv = (ListView) vi.findViewById(R.id.receive_file_list);// ��Ҫ���յ��ļ��嵥
                    lv.setAdapter(receiveFileListAdapter);
                    Button btn_ok = (Button) vi.findViewById(R.id.receive_file_okbtn);
                    Button btn_cancle = (Button) vi.findViewById(R.id.receive_file_cancel);
                    // ����ð�ť���������ļ�ѡ�����������ó��ļ���ѡ��ģʽ��ѡ��һ���������նԷ��ļ����ļ���
                    btn_ok.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!finishedSendFile) {// ��������ļ��Ѿ����չ������ٴ��ļ���ѡ����
                                // Intent intent = new
                                // Intent(FlyPigeonMainActivity.this,MyFileManager.class);
                                // intent.putExtra("selectType", Constant.SELECT_FILE_PATH);
                                // startActivityForResult(intent, 0);
                                mService.receiveFiles("/sdcard/myappDownload");
                                finishedSendFile = true;// �ѱ��ν���״̬��Ϊtrue
                            }
                            // dialog.dismiss();
                        }
                    });
                    // ����ð�ť������������㷢���û��ܾ������ļ��Ĺ㲥
                    btn_cancle.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            recDialog.dismiss();
                            if (!finishedSendFile) {
                                Intent intent = new Intent();
                                intent.setAction(Constant.refuseReceiveFileAction);
                                sendBroadcast(intent);
                            }
                        }
                    });
                }
            }
        }
    }

    // =========================�㲥����������==========================================================

    // �㲥������ע��
    private void regBroadcastRecv() {
        broadcastRecv = new MyBroadcastRecv();
        bFilter = new IntentFilter();
        bFilter.addAction(Constant.updateMyInformationAction);
        bFilter.addAction(Constant.personHasChangedAction);
        bFilter.addAction(Constant.hasMsgUpdatedAction);
        bFilter.addAction(Constant.receivedSendFileRequestAction);
        bFilter.addAction(Constant.remoteUserRefuseReceiveFileAction);
        bFilter.addAction(Constant.dataReceiveErrorAction);
        bFilter.addAction(Constant.dataSendErrorAction);
        bFilter.addAction(Constant.fileReceiveStateUpdateAction);
        bFilter.addAction(Constant.fileSendStateUpdateAction);
        bFilter.addAction(Constant.receiveAddFriendAction);
        bFilter.addAction(Constant.addFriendRequestAction);
        bFilter.addAction(Constant.fileReceiveOrSendFinished);
        registerReceiver(broadcastRecv, bFilter);
    }

    // �򿪷�����ҳ��
    private void openChartPage(Person person) {
        Intent intent = new Intent(this, ChartMsgActivity.class);
        if (nickNameMap.containsKey(person.macAddress)) {
            person.personNickeName = nickNameMap.get(person.macAddress);
        }
        intent.putExtra("person", person);
        Log.d("lxx", person.personHeadIconId + "====");
        intent.putExtra("me", me);
        startActivity(intent);
    }

    public void showDeleteFriendDialog() {
        final Builder builder = new AlertDialog.Builder(EasyChatMainActivity.this);
        builder.setTitle(R.string.delete_friend);
        builder.setMessage(R.string.confirm_delete_friend);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface arg0) {
                // TODO Auto-generated method stub

            }
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                // TODO Auto-generated method stub
                mService.deleteFriend(person1);
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

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case 0:
                Intent intent = new Intent(EasyChatMainActivity.this, SetNickNameActivity.class);
                if (nickNameMap.containsKey(person1.macAddress)) {
                    person1.personNickeName = nickNameMap.get(person1.macAddress);
                }
                intent.putExtra("person", person1);
                startActivity(intent);
                break;

            case 1:
                showDeleteFriendDialog();
                break;

            default:
                break;
        }
        return super.onContextItemSelected(item);
    }
}
