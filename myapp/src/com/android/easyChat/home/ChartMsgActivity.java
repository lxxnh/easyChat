
package com.android.easyChat.home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.android.easyChat.MyAppApplication;
import com.android.easyChat.R;
import com.android.easyChat.service.MainService;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.FileName;
import com.android.easyChat.util.FileState;
import com.android.easyChat.util.Message;
import com.android.easyChat.util.Person;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ChartMsgActivity extends Activity implements OnClickListener {
    private Person person = null;
    private Person me = null;
    private EditText chartMsg = null;
    private Button chartMsgSend = null;
    private Button chartMsgFile = null;
    private LinearLayout chartMsgPanel = null;
    private MainService mService = null;
    private Intent mMainServiceIntent = null;
    private MyBroadcastRecv broadcastRecv = null;
    private IntentFilter bFilter = null;
    private ScrollView chartMsgScroll = null;
    private boolean isPaused = false;// �жϱ����Ƿ�ɼ�
    private boolean isRemoteUserClosed = false; // �Ƿ�Զ���û��Ѿ��ر���ͨ����
    private ArrayList<FileState> receivedFileNames = null;// ���յ��ĶԷ����������ļ���
    private ArrayList<FileState> beSendFileNames = null;// ���͵��Է����ļ�����Ϣ
    private ReceiveSendFileListAdapter receiveFileListAdapter = new ReceiveSendFileListAdapter(this);
    private ReceiveSendFileListAdapter sendFileListAdapter = new ReceiveSendFileListAdapter(this);
    private MyAppApplication myAppApplication;
    private String downloadDirectory = "/sdcard/myappDownload";
    private AlertDialog sendDialog;
    private AlertDialog receive;
    private TextView back;
    SharedPreferences pre;
    SharedPreferences.Editor editor;
    SimpleDateFormat dateteformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    String msg_time_key;
    private AlertDialog receiveFileDialog;
    private AlertDialog sendFileDialog;

    // �����ͻ��յ���Ϣ�����Ļ�������һ��
    private final Handler mHandler = new Handler();
    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            int offset = chartMsgPanel.getMeasuredHeight() - chartMsgScroll.getHeight();// �жϸ߶�
            if (offset > 0) {
                chartMsgScroll.scrollBy(0, offset);// ÿ�ι�100����λ
            }
        }
    };

    // /**
    // * MainService�����뵱ǰActivity�İ�������
    // */
    // private ServiceConnection sConnection = new ServiceConnection(){
    // @Override
    // public void onServiceConnected(ComponentName name, IBinder service) {
    // mService = ((MainService.ServiceBinder)service).getService();
    // showMsg(person.macAddress);//����������ӳɹ����ȡ���û�����Ϣ����ʾ
    // System.out.println("Service connected to activity...");
    // }
    // @Override
    // public void onServiceDisconnected(ComponentName name) {
    // mService = null;
    // System.out.println("Service disconnected to activity...");
    // }
    // };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.person_chart_layout);
        pre = PreferenceManager
                .getDefaultSharedPreferences(this);
        editor = pre.edit();

        myAppApplication = (MyAppApplication) getApplication();
        mService = myAppApplication.getService();
        Intent intent = getIntent();
        person = (Person) intent.getExtras().getSerializable("person");
        // =============��ʾ��һ������ʱ�� add by chenlu=============
        msg_time_key = "msg_time" + person.getMacAddress();

        me = (Person) intent.getExtras().getSerializable("me");
        ((TextView) findViewById(R.id.friends_name)).setText(person.personNickeName);
        chartMsg = (EditText) findViewById(R.id.chart_msg);
        chartMsgSend = (Button) findViewById(R.id.chart_msg_send);
        chartMsgSend.setOnClickListener(this);
        chartMsgFile = (Button) findViewById(R.id.chart_msg_file);
        chartMsgFile.setOnClickListener(this);
        chartMsgPanel = (LinearLayout) findViewById(R.id.chart_msg_panel);
        chartMsgScroll = (ScrollView) findViewById(R.id.chart_msg_scroll);
        back = (TextView) findViewById(R.id.back);
        back.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                finish();
            }
        });

        // ��ǰActivity���̨MainService���а�
        // mMainServiceIntent = new Intent(this,MainService.class);
        // bindService(mMainServiceIntent, sConnection, BIND_AUTO_CREATE);
        showMsg(person.macAddress);
        regBroadcastRecv();

    }

    @Override
    public void onClick(View vi) {
        switch (vi.getId()) {
            case R.id.chart_msg_send:
                String msg = chartMsg.getText().toString();
                if (null == msg || msg.length() <= 0) {
                    Toast.makeText(this, getString(R.string.content_is_empty), Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                chartMsg.setText("");
                View view = getLayoutInflater().inflate(R.layout.send_msg_layout, null);
                ImageView iView = (ImageView) view.findViewById(R.id.send_head_icon);
                TextView smcView = (TextView) view.findViewById(R.id.send_msg_content);
                // TextView smtView = (TextView)view.findViewById(R.id.send_msg_time);
                // TextView nView = (TextView)view.findViewById(R.id.send_nickename);
                iView.setImageResource(me.personHeadIconId);
                smcView.setText(msg);
                // smtView.setText(new Date().toLocaleString());
                // nView.setText(me.personNickeName);
                editor.putString(msg_time_key, dateteformat.format(new Date()));
                editor.commit();

                chartMsgPanel.addView(view);
                mService.sendMsg(person.macAddress, msg);
                mHandler.post(scrollRunnable);
                break;
            case R.id.chart_msg_file:
                Intent intent = new Intent(this, MyFileManager.class);
                intent.putExtra("selectType", Constant.SELECT_FILES);
                startActivityForResult(intent, Constant.FILE_RESULT_CODE);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getOrder()) {
            case 1:
                Intent intent = new Intent(this, MyFileManager.class);
                intent.putExtra("selectType", Constant.SELECT_FILES);
                startActivityForResult(intent, Constant.FILE_RESULT_CODE);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // �����û�id��service���ø��û���Ϣ������ʾ����Ϣ
    private void showMsg(String mac) {
        Log.d("lxx", "ChartMsgActivity mac= " + mac);
        List<Message> msgs = mService.getMessagesByMac(mac);
        if (null != msgs) {
            while (msgs.size() > 0) {
                View view = getLayoutInflater().inflate(R.layout.received_msg_layout, null);
                ImageView iView = (ImageView) view.findViewById(R.id.received_head_icon);
                TextView smcView = (TextView) view.findViewById(R.id.received_msg_content);
                // TextView smtView = (TextView)view.findViewById(R.id.received_msg_time);
                // TextView nView = (TextView)view.findViewById(R.id.received_nickename);
                iView.setImageResource(person.personHeadIconId);
                View view1 = getLayoutInflater().inflate(R.layout.show_send_file_message, null);
                TextView sendFileMessage = (TextView) view1.findViewById(R.id.send_file_msg);
                TextView sendFileTime = (TextView) view1.findViewById(R.id.send_file_time);
                Message msg = msgs.remove(0);
                if (!msg.isSendFileMessage) {
                    smcView.setText(msg.msg);
                    // smtView.setText(msg.receivedTime);
                    // nView.setText(person.personNickeName);
                    chartMsgPanel.addView(view);
                } else {
                    sendFileMessage.setText(msg.msg);
                    sendFileTime.setText(msg.receivedTime);
                    chartMsgPanel.addView(view1);
                }
                editor.putString(msg_time_key, msg.receivedTime);
                editor.commit();
                mHandler.post(scrollRunnable);
            }
        }
    }

    boolean finishedSendFile = false;// ��¼��ǰ��Щ�ļ��ǲ��Ǳ����Ѿ����չ���

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (null != data) {
                int selectType = data.getExtras().getInt("selectType");
                if (selectType == Constant.SELECT_FILE_PATH) {// ����յ������ļ���ѡ��ģʽ��˵��������Ҫ����Է����������ļ�����ѵ�ǰѡ����ļ���·�����ط����
                    String fileSavePath = data.getExtras().getString("fileSavePath");
                    if (null != fileSavePath) {
                        mService.receiveFiles(fileSavePath);
                        finishedSendFile = true;// �ѱ��ν���״̬��Ϊtrue
                    } else {
                        Toast.makeText(this, getString(R.string.folder_can_not_write),
                                Toast.LENGTH_SHORT).show();
                    }
                } else if (selectType == Constant.SELECT_FILES) {// ����յ������ļ�ѡ��ģʽ��˵��������Ҫ�����ļ�����ѵ�ǰѡ��������ļ����ظ�����㡣
                    @SuppressWarnings("unchecked")
                    final ArrayList<FileName> files = (ArrayList<FileName>) data.getExtras().get(
                            "files");
                    mService.sendFiles(person.macAddress, files);// �ѵ�ǰѡ��������ļ����ظ������

                    // ��ʾ�ļ������б�
                    beSendFileNames = mService.getBeSendFileNames();// �ӷ������������Ҫ���յ��ļ����ļ���
                    if (beSendFileNames.size() <= 0)
                        return;
                    sendFileListAdapter.setResources(beSendFileNames);
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(me.personNickeName);
                    builder.setMessage(R.string.start_to_send_file);
                    builder.setIcon(me.personHeadIconId);
                    View vi = getLayoutInflater().inflate(R.layout.request_file_popupwindow_layout,
                            null);
                    builder.setView(vi);
                    final AlertDialog fileListDialog = builder.show();
                    sendFileDialog = fileListDialog;
                    fileListDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface arg0) {
                            beSendFileNames.clear();
                            files.clear();
                        }
                    });
                    ListView lv = (ListView) vi.findViewById(R.id.receive_file_list);// ��Ҫ���յ��ļ��嵥
                    lv.setAdapter(sendFileListAdapter);
                    Button btn_ok = (Button) vi.findViewById(R.id.receive_file_okbtn);
                    btn_ok.setVisibility(View.GONE);
                    Button btn_cancle = (Button) vi.findViewById(R.id.receive_file_cancel);
                    // ����ð�ť������������㷢���û��ܾ������ļ��Ĺ㲥
                    btn_cancle.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            fileListDialog.dismiss();
                        }
                    });
                }
            }
        }
    }

    // �㲥������
    private class MyBroadcastRecv extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constant.hasMsgUpdatedAction)) {
                showMsg(person.macAddress);
            } else if (intent.getAction().equals(Constant.dataReceiveErrorAction)
                    || intent.getAction().equals(Constant.dataSendErrorAction)) {
                Toast.makeText(ChartMsgActivity.this, intent.getExtras().getString("msg"),
                        Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(Constant.fileSendStateUpdateAction)) {// �յ����Է������ļ�����״̬֪ͨ
                beSendFileNames = mService.getBeSendFileNames();// ��õ�ǰ�����ļ�����״̬
                sendFileListAdapter.setResources(beSendFileNames);
                sendFileListAdapter.notifyDataSetChanged();// �����ļ������б�
            } else if (intent.getAction().equals(Constant.fileReceiveStateUpdateAction)) {// �յ����Է������ļ�����״̬֪ͨ
                receivedFileNames = mService.getReceivedFileNames();// ��õ�ǰ�����ļ�����״̬
                receiveFileListAdapter.setResources(receivedFileNames);
                receiveFileListAdapter.notifyDataSetChanged();// �����ļ������б�
            } else if (intent.getAction().equals(Constant.remoteUserRefuseReceiveFileAction)) {
                Toast.makeText(ChartMsgActivity.this, getString(R.string.refuse_receive_file),
                        Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(Constant.fileReceiveOrSendFinished)) {
                if (receiveFileDialog != null) {
                    receiveFileDialog.dismiss();
                }
                if (sendFileDialog != null) {
                    sendFileDialog.dismiss();
                }
            } else if (intent.getAction().equals(Constant.receivedSendFileRequestAction)) {
                if (!isPaused) {// ��������ڿɼ�״̬����Ӧ�㲥,����һ����ʾ���Ƿ�Ҫ���շ��������ļ�
                    receivedFileNames = mService.getReceivedFileNames();// �ӷ������������Ҫ���յ��ļ����ļ���
                    receiveFileListAdapter.setResources(receivedFileNames);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(person.personNickeName);
                    builder.setMessage(R.string.sending_file_to_you);
                    builder.setIcon(person.personHeadIconId);
                    View vi = getLayoutInflater().inflate(R.layout.request_file_popupwindow_layout,
                            null);
                    builder.setView(vi);
                    final AlertDialog revFileDialog = builder.show();
                    receiveFileDialog = revFileDialog;
                    revFileDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface arg0) {
                            receivedFileNames.clear();
                            Log.d("lxx", "finishedSendFile= " + finishedSendFile);
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
                            // Intent(ChartMsgActivity.this,MyFileManager.class);
                            // intent.putExtra("selectType", Constant.SELECT_FILE_PATH);
                            // startActivityForResult(intent, 0);
                                mService.receiveFiles("/sdcard/myappDownload");
                                finishedSendFile = true;// �ѱ��ν���״̬��Ϊtrue
                            }
                        }
                    });

                    // ����ð�ť������������㷢���û��ܾ������ļ��Ĺ㲥
                    btn_cancle.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            revFileDialog.dismiss();
                        }
                    });
                }
            }
        }
    }

    // �㲥������ע��
    private void regBroadcastRecv() {
        broadcastRecv = new MyBroadcastRecv();
        bFilter = new IntentFilter();
        bFilter.addAction(Constant.hasMsgUpdatedAction);
        bFilter.addAction(Constant.receivedSendFileRequestAction);
        bFilter.addAction(Constant.fileReceiveStateUpdateAction);
        bFilter.addAction(Constant.fileSendStateUpdateAction);
        bFilter.addAction(Constant.fileReceiveOrSendFinished);
        registerReceiver(broadcastRecv, bFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unbindService(sConnection);
        unregisterReceiver(broadcastRecv);
    }
}
