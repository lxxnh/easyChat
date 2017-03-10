
package com.android.easyChat.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.android.easyChat.R;
import com.android.easyChat.util.ByteAndInt;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.FileName;
import com.android.easyChat.util.FileState;
import com.android.easyChat.util.Message;
import com.android.easyChat.util.Person;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class MainService extends Service {
    private ServiceBinder sBinder = new ServiceBinder();// �������
    private static ArrayList<Map<String, Person>> children = new ArrayList<Map<String, Person>>();// �����������е��û���ÿ��map���󱣴�һ�����ȫ���û�

    private static Map<String, Person> childrenMap = new HashMap<String, Person>();// ��ǰ���߷Ǻ����û�
    private static Map<String, Person> fridendMap = new HashMap<String, Person>();// ��ǰ���ߺ���
    private static Map<String, Person> applyFriendsMap = new HashMap<String, Person>();// ������֤

    private static ArrayList<ArrayList<String>> onlinekeys = new ArrayList<ArrayList<String>>();// ��ǰ�����û�mac

    private static ArrayList<String> personKeys = new ArrayList<String>();// ��ǰ���߷Ǻ���mac
    private static ArrayList<String> friendKeys = new ArrayList<String>();// ��ǰ���ߺ����û�mac
    private static ArrayList<String> applyKeys = new ArrayList<String>();// ��������mac
    private static Map<String, String> applyMessageMap = new HashMap<String, String>();// ������֤��Ϣ
    private static Map<String, String> nickNameMap = new HashMap<String, String>();
    private static Set<String> allFriendKeys;// ����
    private static Map<String, List<Message>> msgContainer = new HashMap<String, List<Message>>();// �����û���Ϣ����
    private SharedPreferences pre = null;
    private SharedPreferences.Editor editor = null;
    private WifiManager wifiManager = null;
    private ServiceBroadcastReceiver receiver = null;
    public InetAddress localInetAddress = null;
    private String localMacAddress = null;// ����Mac��ַ
    private String localIp = null;
    private byte[] localIpBytes = null;
    private byte[] regBuffer = new byte[Constant.bufferSize];// ��������ע�ύ��ָ��
    /*
     * regBuffer: 0-2:pkgHead,��Ӧ�÷��������ݱ� 3�������� 4���������� 5���������� 6-9���û�ID���˴��������⺬�� 10-13��iconID 14-43���ǳ�
     * 44-47��ip 48-64��mac��ַ
     */
    private byte[] addFriendBuffer = new byte[Constant.bufferSize];// ��Ӻ���
    private byte[] msgSendBuffer = new byte[Constant.bufferSize];// ��Ϣ���ͽ���
    private byte[] fileSendBuffer = new byte[Constant.bufferSize];// �ļ����ͽ���ָ��
    private static Person me = null;// ������������������Ϣ
    private CommunicationBridge comBridge = null;// ͨѶ��Э�����ģ��

    @Override
    public IBinder onBind(Intent arg0) {
        return sBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onRebind(Intent intent) {

    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onStart(Intent intent, int startId) {
        initCmdBuffer();// ��ʼ��ָ���
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        localMacAddress = wifiInfo.getMacAddress();// ��ȡ����Mac��ַ
        new CheckNetConnectivity().start();// �������״̬����ȡIP��ַ

        comBridge = new CommunicationBridge();// ����socket����
        comBridge.start();

        pre = PreferenceManager.getDefaultSharedPreferences(this);
        editor = pre.edit();
        createDownloadDirectory();

        regBroadcastReceiver();// ע��㲥������
        getMyInfomation();// ���������Ϣ
        new UpdateMe().start();// �����緢������������ע��
        new CheckUserOnline().start();// ����û��б��Ƿ��г�ʱ�û�
        sendPersonHasChangedBroadcast();// ֪ͨ�����û�������˳�
    }

    // �����
    public class ServiceBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }

    public void createDownloadDirectory() {
        File dirFile = new File("/sdcard/myappDownload");
        if (!dirFile.exists()) {
            dirFile.mkdir();
        }
    }

    // ������ѵ������Ϣ
    private void getMyInfomation() {
        SharedPreferences pre = PreferenceManager
                .getDefaultSharedPreferences(this);
        allFriendKeys = new HashSet<String>();
        allFriendKeys = pre.getStringSet("allFriendKeys", allFriendKeys);
        Set<String> apply = new HashSet<String>();
        apply = pre.getStringSet("applyKeys", apply);
        Iterator<String> it = apply.iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (!applyKeys.contains(key))
                applyKeys.add(key);
        }
        applyMessageMap.clear();
        Set<String> applyMessage = new HashSet<String>();
        applyMessage = pre.getStringSet("applyMessageKeys", applyMessage);
        Iterator<String> it1 = applyMessage.iterator();
        while (it1.hasNext()) {
            String key = it1.next();
            String[] applyMsg = key.split(",");
            if (applyMsg.length > 1) {
                applyMessageMap.put(applyMsg[0], applyMsg[1]);
            } else {
                applyMessageMap.put(applyMsg[0], "");
            }

        }
        int iconId = pre.getInt("headIconId", R.drawable.white_bird);
        // String nickeName = pre.getString("nickeName", "Zhang San");
        String nickeName = pre.getString("nickeName", android.os.Build.MODEL);// Ĭ���ǳ�Ϊ�ֻ��ͺ�
        int myId = pre.getInt("myId", Constant.getMyId());
        editor.putInt("myId", myId);
        editor.commit();

        if (null == me)
            me = new Person();
        me.personHeadIconId = iconId;
        me.personNickeName = nickeName;
        me.personId = myId;
        me.ipAddress = localIp;
        me.setMacAddress(localMacAddress);// add-by chenlu
        // ����ע�������û�����
        byte[] localMacAddressBytes = localMacAddress.getBytes();
        System.arraycopy(localMacAddressBytes, 0, regBuffer, 48, localMacAddressBytes.length);// ����Mac��ַ
        System.arraycopy(ByteAndInt.int2ByteArray(myId), 0, regBuffer, 6, 4);
        System.arraycopy(ByteAndInt.int2ByteArray(iconId), 0, regBuffer, 10, 4);
        for (int i = 14; i < 44; i++)
            regBuffer[i] = 0;// ��ԭ�����ǳ��������
        byte[] nickeNameBytes = nickeName.getBytes();
        System.arraycopy(nickeNameBytes, 0, regBuffer, 14,
                nickeNameBytes.length);

    }

    private String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = new Date();
        return dateFormat.format(date);
    }

    // �����������״̬,��ñ���IP��ַ
    private class CheckNetConnectivity extends Thread {
        public void run() {
            try {
                if (!wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(true);
                }

                for (Enumeration<NetworkInterface> en = NetworkInterface
                        .getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf
                            .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            if (inetAddress.isReachable(1000)) {
                                localInetAddress = inetAddress;
                                localIp = inetAddress.getHostAddress()
                                        .toString();
                                localIpBytes = inetAddress.getAddress();
                                System.arraycopy(localIpBytes, 0, regBuffer,
                                        44, 4);
                            }
                        }
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };
    };

    // ��ʼ��ָ���
    private void initCmdBuffer() {
        // ��ʼ���û�ע��ָ���
        for (int i = 0; i < Constant.bufferSize; i++)
            regBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, regBuffer, 0, 3);
        regBuffer[3] = Constant.CMD80;
        regBuffer[4] = Constant.CMD_TYPE1;
        regBuffer[5] = Constant.OPR_CMD1;

        // chushihuaaddfriend
        for (int i = 0; i < Constant.bufferSize; i++)
            addFriendBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, regBuffer, 0, 3);
        addFriendBuffer[3] = Constant.CMD83;
        addFriendBuffer[4] = Constant.CMD_TYPE1;
        addFriendBuffer[5] = Constant.OPR_CMD1;

        // ��ʼ����Ϣ����ָ���
        for (int i = 0; i < Constant.bufferSize; i++)
            msgSendBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, msgSendBuffer, 0, 3);
        msgSendBuffer[3] = Constant.CMD81;
        msgSendBuffer[4] = Constant.CMD_TYPE1;
        msgSendBuffer[5] = Constant.OPR_CMD1;

        // ��ʼ�������ļ�ָ���
        for (int i = 0; i < Constant.bufferSize; i++)
            fileSendBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, fileSendBuffer, 0, 3);
        fileSendBuffer[3] = Constant.CMD82;
        fileSendBuffer[4] = Constant.CMD_TYPE1;
        fileSendBuffer[5] = Constant.OPR_CMD1;

    }

    // ��������û�����
    public ArrayList<Map<String, Person>> getChildren() {
        return children;
    }

    // ��������û�id
    public ArrayList<ArrayList<String>> getOnlineKeys() {
        return onlinekeys;
    }

    // �����û�id��ø��û�����Ϣ
    public List<Message> getMessagesByMac(String mac) {
        Log.d("lxx", "service mac= " + mac);
        return msgContainer.get(mac);
    }

    // �����û�id��ø��û�����Ϣ����
    public int getMessagesCountByMac(String mac) {
        List<Message> msgs = msgContainer.get(mac);
        if (null != msgs) {
            return msgs.size();
        } else {
            return 0;
        }
    }

    // ÿ��3�뷢��һ��������
    boolean isStopUpdateMe = false;

    private class UpdateMe extends Thread {
        @Override
        public void run() {
            while (!isStopUpdateMe) {
                try {
                    comBridge.joinOrganization();
                    sleep(3000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ����û��Ƿ����ߣ��������20��˵���û������ߣ�����б���������û�
    ArrayList<String> myList = new ArrayList<String>();
    ArrayList<String> myList2 = new ArrayList<String>();

    private class CheckUserOnline extends Thread {
        @Override
        public void run() {
            super.run();
            boolean hasChanged = false;
            while (!isStopUpdateMe) {
                if (childrenMap.size() > 0) {
                    Set<String> keys = childrenMap.keySet();
                    for (String key : keys) {
                        if (System.currentTimeMillis()
                                - childrenMap.get(key).timeStamp > 20000) {
                            myList2.add(key);
                            // childrenMap.remove(key);
                            // personKeys.remove(key);
                            // hasChanged = true;
                        }
                    }
                    for (int i = 0; i < myList.size(); i++) {
                        childrenMap.remove(myList.get(i));
                        personKeys.remove(myList.get(i));
                        hasChanged = true;
                    }
                }
                if (fridendMap.size() > 0) {
                    Set<String> keys = fridendMap.keySet();
                    for (String key : keys) {
                        if (System.currentTimeMillis()
                                - fridendMap.get(key).timeStamp > 20000) {
                            myList.add(key);
                            // fridendMap.remove(key);
                            // friendKeys.remove(key);
                            // hasChanged = true;
                        }
                    }
                    for (int i = 0; i < myList.size(); i++) {
                        Log.d("lxx", "fridendMap.remove");
                        fridendMap.remove(myList.get(i));
                        friendKeys.remove(myList.get(i));
                        hasChanged = true;
                    }
                }
                if (hasChanged)
                    sendPersonHasChangedBroadcast();
                myList.clear();
                myList2.clear();
                hasChanged = false;
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // �����û����¹㲥
    private void sendPersonHasChangedBroadcast() {
        Intent intent = new Intent();
        intent.setAction(Constant.personHasChangedAction);
        sendBroadcast(intent);
    }

    // ע��㲥������
    private void regBroadcastReceiver() {
        receiver = new ServiceBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constant.WIFIACTION);
        filter.addAction(Constant.ETHACTION);
        filter.addAction(Constant.updateMyInformationAction);
        filter.addAction(Constant.refuseReceiveFileAction);
        filter.addAction(Constant.imAliveNow);
        filter.addAction(Constant.addFriendRequestAction);
        filter.addAction(Constant.receiveAddFriendAction);
        registerReceiver(receiver, filter);
    }

    // �㲥������������
    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constant.WIFIACTION)
                    || intent.getAction().equals(Constant.ETHACTION)) {
                new CheckNetConnectivity().start();
            } else if (intent.getAction().equals(
                    Constant.updateMyInformationAction)) {
                getMyInfomation();
                comBridge.joinOrganization();
            } else if (intent.getAction().equals(
                    Constant.refuseReceiveFileAction)) {
                comBridge.refuseReceiveFile();
            } else if (intent.getAction().equals(Constant.imAliveNow)) {

            } else if (intent.getAction().equals(Constant.addFriendRequestAction)) {
                Log.d("lxx", "Constant.addFriendRequestAction" + Constant.addFriendRequestAction);
                Person person = (Person) intent.getExtras().getSerializable("PersonInfo");
                comBridge.addFriendRequset(person);
            } else if (intent.getAction().equals(Constant.receiveAddFriendAction)) {
                Person person = (Person) intent.getExtras().getSerializable("person");
                if (!applyKeys.contains(person.macAddress)) {
                    applyKeys.add(person.macAddress);
                }
                updateApplyKeys(applyKeys);
            }
        }
    }

    public void updateApplyKeys(ArrayList<String> applyKeys) {
        Set<String> apply = new HashSet<String>();
        for (int i = 0; i < applyKeys.size(); i++) {
            apply.add(applyKeys.get(i));
        }
        editor.putStringSet("applyKeys", apply);
        editor.commit();
    }

    public void updateApplyMessage(Map<String, String> applyMsg) {
        Set<String> applySet = new HashSet<String>();
        Set<String> applySet1 = new HashSet<String>();
        applySet = applyMsg.keySet();
        Iterator<String> it = applySet.iterator();
        while (it.hasNext()) {
            String apply = it.next();
            apply = apply + "," + applyMsg.get(apply);
            applySet1.add(apply);
        }
        editor.putStringSet("applyMessageKeys", applySet1);
        editor.commit();
    }

    public void agreeAddFriend(Person person) {
        comBridge.agreeAddFriend(person);
    }

    public void refuseAddFriend(Person person) {
        comBridge.refuseAddFriend(person);
    }

    // ��պ�����֤�б�
    public void clearApplyList() {
        comBridge.clearApplyList();
    }

    public void deleteFriend(Person person) {
        comBridge.deleteFriend(person);
    }

    // ������Ϣ
    public void sendMsg(String mac, String msg) {
        comBridge.sendMsg(mac, msg);
    }

    // �����ļ�
    public void sendFiles(String mac, ArrayList<FileName> files) {
        comBridge.sendFiles(mac, files);
    }

    // �����ļ�
    public void receiveFiles(String fileSavePath) {
        comBridge.receiveFiles(fileSavePath);
    }

    // ��������յ��ļ���
    public ArrayList<FileState> getReceivedFileNames() {
        return comBridge.getReceivedFileNames();
    }

    // ��������͵��ļ���
    public ArrayList<FileState> getBeSendFileNames() {
        return comBridge.getBeSendFileNames();
    }

    @Override
    public void onDestroy() {
        comBridge.release();
        unregisterReceiver(receiver);
        isStopUpdateMe = true;
        System.out.println("Service on destory...");
    }

    // ========================Э�������ͨѶģ��=======================================================
    private class CommunicationBridge extends Thread {
        private MulticastSocket multicastSocket = null;
        private byte[] recvBuffer = new byte[Constant.bufferSize];
        private String fileSenderMac;// ���������ļ������ߵ�mac��ַ
        private boolean isBusyNow = false;// �����Ƿ������շ��ļ��������״̬Ϊtrue���ʾ�������ڽ����շ��ļ���������ʱ��Ҫ�����������ļ����û�����æָ��
        private String fileSavePath = null;// ����������յ����ļ�
        private ArrayList<FileName> tempFiles = null;// ������ʱ������Ҫ���͵��ļ���
        private String tempMac;// ������ʱ������Ҫ�����ļ����û�mac(�����ļ������û�mac)
        private ArrayList<FileState> receivedFileNames = new ArrayList<FileState>();
        private ArrayList<FileState> beSendFileNames = new ArrayList<FileState>();

        private FileHandler fileHandler = null;// �ļ������̣߳������շ��ļ�
        private int count = 0;

        public CommunicationBridge() {
            fileHandler = new FileHandler();
            fileHandler.start();
            Log.d("lxx", "CommunicationBridge");
        }

        // ���鲥�˿ڣ�׼���鲥ͨѶ
        @Override
        public void run() {
            super.run();
            try {
                Log.d("lxx", "Socket started...");
                multicastSocket = new MulticastSocket(Constant.PORT);
                multicastSocket.setLoopbackMode(true);
                multicastSocket.joinGroup(InetAddress
                        .getByName(Constant.MULTICAST_IP));
                System.out.println("Socket started...");
                while (!multicastSocket.isClosed() && null != multicastSocket) {
                    for (int i = 0; i < Constant.bufferSize; i++) {
                        recvBuffer[i] = 0;
                    }
                    DatagramPacket rdp = new DatagramPacket(recvBuffer,
                            recvBuffer.length);
                    Log.d("lxx", "begin receive...");
                    multicastSocket.receive(rdp);
                    Log.d("lxx", "end receive...");
                    parsePackage(recvBuffer);
                    Log.d("lxx", "after parse");
                }
            } catch (Exception e) {
                try {
                    if (null != multicastSocket && !multicastSocket.isClosed()) {
                        multicastSocket.leaveGroup(InetAddress
                                .getByName(Constant.MULTICAST_IP));
                        multicastSocket.close();
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                e.printStackTrace();
            }
        }

        // �������յ������ݰ�
        private void parsePackage(byte[] pkg) {
            Log.d("lxx", "parsePackage enter");
            int CMD = pkg[3];// ������
            int cmdType = pkg[4];// ��������
            int oprCmd = pkg[5];// ��������


            // ����û�MAC
            byte[] macAddressBytes = new byte[17];
            System.arraycopy(pkg, 48, macAddressBytes, 0, 17);
            String macAddressString = new String(macAddressBytes);// �õ��ַ�����ʽ��mac��ַ

            switch (CMD) {
                case Constant.CMD83:
                    switch (cmdType) {
                        case Constant.CMD_TYPE1:
                            switch (oprCmd) {
                                case Constant.OPR_CMD1:
                                    Log.d("lxx", "receiveAddFriendAction");
                                    byte[] applyMessageBytes = new byte[100];
                                    System.arraycopy(pkg, 65, applyMessageBytes, 0, 100);
                                    String applyMessage = new String(applyMessageBytes).trim();
                                    Intent intent = new Intent();
                                    intent.setAction(Constant.receiveAddFriendAction);
                                    Person person = childrenMap.get(macAddressString);
                                    person.setApplyMessage(applyMessage);
                                    if (applyMessageMap.containsKey(macAddressString)) {
                                        applyMessageMap.remove(macAddressString);
                                    }
                                    applyMessageMap.put(macAddressString, applyMessage);
                                    updateApplyMessage(applyMessageMap);
                                    Log.d("lxx", childrenMap.get(macAddressString)
                                            .getApplyMessage());
                                    intent.putExtra("person", person);
                                    sendBroadcast(intent);
                                    break;
                            }
                            break;
                        case Constant.CMD_TYPE2:
                            switch (oprCmd) {
                                case Constant.OPR_CMD2:// �յ�ͬ����Ӻ���
                                    Log.d("chenlu22", "agreeAddFriendAction");
                                    if (applyKeys.contains(macAddressString)) {
                                        applyKeys.remove(macAddressString);
                                        updateApplyKeys(applyKeys);
                                        applyMessageMap.remove(macAddressString);
                                        updateApplyMessage(applyMessageMap);
                                    }
                                    addFriendKeys(macAddressString);
                                    // friendKeys.add(macAddressString);
                                    break;
                                case Constant.OPR_CMD3:// �յ��ܽ^��Ӻ���
                                    Log.d("lxx", "refuseAddFriendAction");
                                    Intent intent = new Intent();
                                    intent.setAction(Constant.refuseAddFriendAction);
                                    Person person = childrenMap.get(macAddressString);
                                    intent.putExtra("person", person);
                                    sendBroadcast(intent);
                                    break;
                                case Constant.OPR_CMD4:
                                    removeFriendKeys(macAddressString);
                                    break;
                            }
                            break;
                    }
                    break;
                case Constant.CMD80:
                    switch (cmdType) {
                        case Constant.CMD_TYPE1:
                            // �������Ϣ�����Լ���������Է����ͻ�Ӧ��,���ѶԷ������û��б�
                            Log.d("lxx", "macAddressString=" + macAddressString + "me.macAddress"
                                    + me.macAddress);
                            if (!macAddressString.equals(me.macAddress)) {
                                updatePerson(macAddressString, pkg);
                                // ����Ӧ���
                                byte[] ipBytes = new byte[4];// ������󷽵�ip��ַ
                                System.arraycopy(pkg, 44, ipBytes, 0, 4);
                                try {
                                    InetAddress targetIp = InetAddress
                                            .getByAddress(ipBytes);
                                    regBuffer[4] = Constant.CMD_TYPE2;// ���Լ���ע����Ϣ�޸ĳ�Ӧ����Ϣ��־�����Լ�����Ϣ���͸�����
                                    DatagramPacket dp = new DatagramPacket(regBuffer,
                                            Constant.bufferSize, targetIp,
                                            Constant.PORT);
                                    multicastSocket.send(dp);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case Constant.CMD_TYPE2:
                            // updatePerson(userId, pkg);
                            break;
                        case Constant.CMD_TYPE3:
                            childrenMap.remove(macAddressString);
                            personKeys.remove(macAddressString);
                            fridendMap.remove(macAddressString);
                            friendKeys.remove(macAddressString);
                            sendPersonHasChangedBroadcast();
                            break;
                    }
                    break;
                case Constant.CMD81:// �յ���Ϣ
                    switch (cmdType) {
                        case Constant.CMD_TYPE1:
                            List<Message> messages = null;
                            if (msgContainer.containsKey(macAddressString)) {
                                messages = msgContainer.get(macAddressString);
                                Log.d("lxx", "msgContainer.containsKey(macAddressString)=="
                                        + macAddressString);
                            } else {
                                messages = new ArrayList<Message>();
                            }
                            byte[] msgBytes = new byte[Constant.msgLength];
                            System.arraycopy(pkg, 65, msgBytes, 0, Constant.msgLength);
                            String msgStr = new String(msgBytes).trim();
                            Message msg = new Message();
                            msg.msg = msgStr;
                            msg.receivedTime = getCurrentTime();
                            msg.isSendFileMessage = false;
                            messages.add(msg);
                            msgContainer.put(macAddressString, messages);
                            Log.d("lxx", msg.msg);
                            if (msgContainer != null) {
                                Log.d("lxx", "msgContainer=" + msgContainer.size());
                            }

                            Intent intent = new Intent();
                            intent.setAction(Constant.hasMsgUpdatedAction);
                            intent.putExtra("macAddressString", macAddressString);
                            intent.putExtra("msgCount", messages.size());
                            sendBroadcast(intent);
                            break;
                        case Constant.CMD_TYPE2:
                            break;
                    }
                    break;
                case Constant.CMD82:
                    switch (cmdType) {
                        case Constant.CMD_TYPE1:// �յ��ļ���������
                            switch (oprCmd) {
                                case Constant.OPR_CMD1:
                                    // ���͹㲥��֪ͨ�������ļ���Ҫ����
                                    if (!isBusyNow) {
                                        // isBusyNow = true;
                                        fileSenderMac = macAddressString;// �����ļ������ߵ�mac���Ա�����������߾ܾ������ļ�ʱ����ͨ����mac�ҵ������ߣ����������߷��;ܾ�����ָ��
                                        Person person = fridendMap.get(macAddressString);
                                        Intent intent = new Intent();
                                        intent.putExtra("person", person);
                                        intent.setAction(Constant.receivedSendFileRequestAction);
                                        sendBroadcast(intent);
                                    } else {// �����ǰ�����շ��ļ�����Է�����æָ��
                                        Person person = fridendMap.get(macAddressString);
                                        fileSendBuffer[4] = Constant.CMD_TYPE2;
                                        fileSendBuffer[5] = Constant.OPR_CMD4;
                                        byte[] meIdBytes = ByteAndInt
                                                .int2ByteArray(me.personId);
                                        System.arraycopy(meIdBytes, 0, fileSendBuffer, 6, 4);
                                        try {
                                            DatagramPacket dp = new DatagramPacket(
                                                    fileSendBuffer,
                                                    Constant.bufferSize,
                                                    InetAddress.getByName(person.ipAddress),
                                                    Constant.PORT);
                                            multicastSocket.send(dp);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    break;
                                case Constant.OPR_CMD5:// ���նԷ����������ļ�����Ϣ
                                    byte[] fileNameBytes = new byte[Constant.fileNameLength];
                                    byte[] fileSizeByte = new byte[8];
                                    System.arraycopy(pkg, 65, fileNameBytes, 0,
                                            Constant.fileNameLength);
                                    System.arraycopy(pkg, 164, fileSizeByte, 0, 8);
                                    FileState fs = new FileState();
                                    fs.fileName = new String(fileNameBytes).trim();
                                    fs.fileSize = Long.valueOf(ByteAndInt
                                            .byteArrayToLong(fileSizeByte));
                                    receivedFileNames.add(fs);
                                    break;
                            }
                            break;
                        case Constant.CMD_TYPE2:
                            switch (oprCmd) {
                                case Constant.OPR_CMD2:// �Է�ͬ������ļ�
                                    count = 0;
                                    fileHandler.startSendFile();
                                    System.out
                                            .println("Start send file to remote user ...");
                                    break;
                                case Constant.OPR_CMD3:// �Է��ܾ������ļ�
                                    Intent intent = new Intent();
                                    intent.setAction(Constant.remoteUserRefuseReceiveFileAction);
                                    sendBroadcast(intent);
                                    System.out
                                            .println("Remote user refuse to receive file ...");
                                    break;
                                case Constant.OPR_CMD4:// �Է�����æ
                                    System.out.println("Remote user is busy now ...");
                                    break;
                            }
                            break;
                    /*
                     * case Constant.CMD_TYPE3: switch (oprCmd) { case Constant.OPR_CMD2:
                     * List<Message> messages = null; if
                     * (msgContainer.containsKey(macAddressString)) { messages =
                     * msgContainer.get(macAddressString); Log.d("lxx",
                     * "msgContainer.containsKey(macAddressString)=="+macAddressString); } else {
                     * messages = new ArrayList<Message>(); } Message msg = new Message(); msg.msg =
                     * "�Է��ɹ��Ӆ��������ļ�"; msg.receivedTime = getCurrentTime(); msg.isSendFileMessage =
                     * true; messages.add(msg); msgContainer.put(macAddressString, messages); Intent
                     * intent = new Intent(); intent.setAction(Constant.hasMsgUpdatedAction);
                     * sendBroadcast(intent); }
                     */
                    }
                    break;
            }
        }

        // ���»���û���Ϣ���û��б���
        private void updatePerson(String mac, byte[] pkg) {
            Person person = new Person();
            getPerson(pkg, person);
            Log.d("lxx", "allFriendKeys : " + allFriendKeys.toString() + " --person.timeStamp="
                    + person.timeStamp);
            if (!allFriendKeys.contains(mac)) {
                // if (childrenMap.containsKey(mac)) {
                // childrenMap.remove(mac);
                // }
                childrenMap.put(mac, person);// ���������û����������Ǻ����б�
                if (!personKeys.contains(mac))
                    personKeys.add(mac);
                // if (!children.contains(childrenMap))
                // children.add(childrenMap);
                // if (!onlinekeys.contains(personKeys))
                // onlinekeys.add(personKeys);
                friendKeys.remove(mac);
                fridendMap.remove(mac);
            } else {
                // if (fridendMap.containsKey(mac)) {
                // fridendMap.remove(mac);
                // }
                fridendMap.put(mac, person);
                Log.d("lxx1", "fridendMap.put(userId, person)" + person.toString());
                if (!friendKeys.contains(mac))
                    friendKeys.add(mac);
                // if (!children.contains(fridendMap))
                // children.add(fridendMap);
                // if (!onlinekeys.contains(friendKeys))
                // onlinekeys.add(friendKeys);
                personKeys.remove(mac);
                childrenMap.remove(mac);
            }
            /*
             * if (!children.contains(childrenMap)) children.add(childrenMap); if
             * (!onlinekeys.contains(personKeys)) onlinekeys.add(personKeys);
             */
            if (!children.contains(fridendMap))
                children.add(fridendMap);
            if (!onlinekeys.contains(friendKeys))
                onlinekeys.add(friendKeys);
            Log.d("lxx1", "fridendMap" + children.get(0).toString());
            Log.d("lxx1", "onlinekeys.size()=" + onlinekeys.size() + "---" + onlinekeys.toString());
            Log.d("lxx1", children.toString());
            Log.d("lxx1", "in service --" + childrenMap.toString());
            Log.d("lxx1", "children.size()=" + children.size() + "--childrenMap.size()="
                    + childrenMap.size() + "--fridendMap.size()" + fridendMap.size());
            sendPersonHasChangedBroadcast();
            Log.d("lxx1", "fasongwangenxinyonghuliebiaoxinxi");
        }

        private void addFriendKeys(String mac) {
            Log.d("lxx11", "in addFriendKeys" + mac);
            allFriendKeys.add(mac);
            Log.d("lxx11", allFriendKeys.toString());
            editor.putStringSet("allFriendKeys", allFriendKeys);
            editor.commit();
            Person p = childrenMap.get(mac);
            childrenMap.remove(mac);
            personKeys.remove(mac);
            fridendMap.put(mac, p);
            if (!friendKeys.contains(mac))
                friendKeys.add(mac);
            if (!children.contains(fridendMap))
                children.add(fridendMap);
            if (!onlinekeys.contains(friendKeys))
                onlinekeys.add(friendKeys);
            sendPersonHasChangedBroadcast();
        }

        private void removeFriendKeys(String mac) {
            allFriendKeys.remove(mac);
            Log.d("lxx11", "in removeFriendKeys");
            editor.putStringSet("allFriendKeys", allFriendKeys);
            editor.commit();
            Person p = fridendMap.get(mac);
            fridendMap.remove(mac);
            friendKeys.remove(mac);
            childrenMap.put(mac, p);
            if (!personKeys.contains(mac))
                personKeys.add(mac);
            if (!children.contains(fridendMap))
                children.add(fridendMap);
            if (!onlinekeys.contains(friendKeys))
                onlinekeys.add(friendKeys);
            sendPersonHasChangedBroadcast();
        }

        // �ر�Socket����
        private void release() {
            try {
                regBuffer[4] = Constant.CMD_TYPE3;// �����������޸ĳ�ע����־�����㲥���ͣ��������û����˳�
                DatagramPacket dp = new DatagramPacket(regBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(Constant.MULTICAST_IP),
                        Constant.PORT);
                multicastSocket.send(dp);
                System.out.println("Send logout cmd ...");

                multicastSocket.leaveGroup(InetAddress
                        .getByName(Constant.MULTICAST_IP));
                multicastSocket.close();

                System.out.println("Socket has closed ...");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                fileHandler.release();
            }
        }

        // �������ݰ�����ȡһ���û���Ϣ
        private void getPerson(byte[] pkg, Person person) {

            byte[] personIdBytes = new byte[4];
            byte[] iconIdBytes = new byte[4];
            byte[] nickeNameBytes = new byte[30];
            byte[] personIpBytes = new byte[4];
            byte[] personMacBytes = new byte[17];// add-by chenlu ���MAC

            System.arraycopy(pkg, 6, personIdBytes, 0, 4);
            System.arraycopy(pkg, 10, iconIdBytes, 0, 4);
            System.arraycopy(pkg, 14, nickeNameBytes, 0, 30);
            System.arraycopy(pkg, 44, personIpBytes, 0, 4);
            System.arraycopy(pkg, 48, personMacBytes, 0, 17);// add-by chenlu

            person.personId = ByteAndInt.byteArray2Int(personIdBytes);
            person.personHeadIconId = ByteAndInt.byteArray2Int(iconIdBytes);
            person.personNickeName = (new String(nickeNameBytes)).trim();
            person.ipAddress = Constant.intToIp(ByteAndInt
                    .byteArray2Int(personIpBytes));
            person.timeStamp = System.currentTimeMillis();
            person.setMacAddress((new String(personMacBytes)).trim());// add-by chenlu
        }

        // ע���Լ���������
        public void joinOrganization() {
            try {
                if (null != multicastSocket && !multicastSocket.isClosed()) {
                    regBuffer[4] = Constant.CMD_TYPE1;// �ָ���ע�������־����������ע���Լ�
                    DatagramPacket dp = new DatagramPacket(regBuffer,
                            Constant.bufferSize,
                            InetAddress.getByName(Constant.MULTICAST_IP),
                            Constant.PORT);
                    multicastSocket.send(dp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // ������Ϣ
        public void sendMsg(String mac, String msg) {
            try {
                Person psn = fridendMap.get(mac);
                if (null != psn) {
                    System.arraycopy(ByteAndInt.int2ByteArray(me.personId), 0,
                            msgSendBuffer, 6, 4);
                    byte[] macBytes = me.macAddress.getBytes();
                    System.arraycopy(macBytes, 0, msgSendBuffer, 48, 17);
                    int msgLength = Constant.msgLength + 65;
                    for (int i = 65; i < msgLength; i++) {
                        msgSendBuffer[i] = 0;
                    }
                    byte[] msgBytes = msg.getBytes();
                    System.arraycopy(msgBytes, 0, msgSendBuffer, 65,
                            msgBytes.length);
                    DatagramPacket dp = new DatagramPacket(msgSendBuffer,
                            Constant.bufferSize,
                            InetAddress.getByName(psn.ipAddress), Constant.PORT);
                    multicastSocket.send(dp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ��Է�������������ļ�ָ��
        public void sendFiles(String mac, ArrayList<FileName> files) {
            if (mac != null && null != files && files.size() > 0) {
                try {
                    tempMac = mac;
                    tempFiles = files;
                    Person person = fridendMap.get(mac);
                    fileSendBuffer[4] = Constant.CMD_TYPE1;
                    fileSendBuffer[5] = Constant.OPR_CMD5;
                    byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
                    System.arraycopy(meIdBytes, 0, fileSendBuffer, 6, 4);
                    byte[] macBytes = me.macAddress.getBytes();
                    System.arraycopy(macBytes, 0, fileSendBuffer, 48, 17);
                    int fileNameLength = Constant.fileNameLength + 65;// ���ͷ�ļ������ļ����洢�����Ա�д�µ��ļ���
                    // ��Ҫ���͵������ļ������͸��Է�
                    for (final FileName file : tempFiles) {
                        // �ռ�����Ҫ�����ļ������������
                        FileState fs = new FileState(file.fileSize, 0,
                                file.getFileName());
                        beSendFileNames.add(fs);

                        byte[] fileNameBytes = file.getFileName().getBytes();
                        for (int i = 65; i < fileNameLength; i++)
                            fileSendBuffer[i] = 0;
                        System.arraycopy(fileNameBytes, 0, fileSendBuffer, 65,
                                fileNameBytes.length);// ���ļ�������ͷ���ݰ�
                        System.arraycopy(
                                ByteAndInt.longToByteArray(file.fileSize), 0,
                                fileSendBuffer, 164, 8);
                        DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                                Constant.bufferSize,
                                InetAddress.getByName(person.ipAddress),
                                Constant.PORT);
                        multicastSocket.send(dp);
                    }
                    // �Է�������������ļ�ָ��
                    fileSendBuffer[5] = Constant.OPR_CMD1;
                    DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                            Constant.bufferSize,
                            InetAddress.getByName(person.ipAddress),
                            Constant.PORT);
                    multicastSocket.send(dp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void addFriendRequset(Person person) {
            try {
                Log.d("lxx", "go in addFriendRequset");
                addFriendBuffer[4] = Constant.CMD_TYPE1;
                addFriendBuffer[5] = Constant.OPR_CMD1;
                // childrenMap.remove(person.personId);
                // fridendMap.put(person.macAddress, person);
                // if(!children.contains(fridendMap)){
                // children.add(fridendMap);
                // }
                // sendPersonHasChangedBroadcast();
                // ==========chenlu delete ============
                byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
                byte[] macBytes = me.macAddress.getBytes();
                // byte[] meIconIdBytes = ByteAndInt.int2ByteArray(me.personHeadIconId);
                // byte[] nickeNameBytes = me.personNickeName.getBytes();
                System.arraycopy(meIdBytes, 0, addFriendBuffer, 6, 4);
                System.arraycopy(macBytes, 0, addFriendBuffer, 48, 17);
                for (int i = 65; i < 165; i++) {
                    addFriendBuffer[i] = 0;
                }
                Log.d("lxx", person.applyMessage);
                byte[] applyMessageBytes = person.applyMessage.getBytes();
                System.arraycopy(applyMessageBytes, 0, addFriendBuffer, 65,
                        applyMessageBytes.length);
                Log.d("lxx", person.applyMessage);
                // �Է���������TIANJIAHAOYOUָ��
                DatagramPacket dp = new DatagramPacket(addFriendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress),
                        Constant.PORT);
                multicastSocket.send(dp);
                Log.d("lxx",
                        "InetAddress.getByName(person.ipAddress)"
                                + InetAddress.getByName(person.ipAddress));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void refuseAddFriend(Person person) {
            try {
                Log.d("lxx", "go in refuseAddFriend");
                addFriendBuffer[4] = Constant.CMD_TYPE2;
                addFriendBuffer[5] = Constant.OPR_CMD3;
                // byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
                applyKeys.remove(person.macAddress);
                updateApplyKeys(applyKeys);
                applyMessageMap.remove(person.macAddress);
                updateApplyMessage(applyMessageMap);
                // System.arraycopy(meIdBytes, 0, addFriendBuffer, 6, 4);
                byte[] macBytes = me.macAddress.getBytes();
                System.arraycopy(macBytes, 0, addFriendBuffer, 48, 17);
                // �Է����;ܽ^��Ӻ���ָ��
                DatagramPacket dp = new DatagramPacket(addFriendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress),
                        Constant.PORT);
                multicastSocket.send(dp);
                Log.d("lxx",
                        "InetAddress.getByName(person.ipAddress)"
                                + InetAddress.getByName(person.ipAddress));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void agreeAddFriend(Person person) {
            try {
                Log.d("lxx", "go in agreeAddFriend");
                addFriendBuffer[4] = Constant.CMD_TYPE2;
                addFriendBuffer[5] = Constant.OPR_CMD2;
                // byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
                // childrenMap.remove(person.macAddress);
                // fridendMap.put(person.macAddress, person);
                applyKeys.remove(person.macAddress);
                // friendKeys.add(person.macAddress);
                updateApplyKeys(applyKeys);
                applyMessageMap.remove(person.macAddress);
                updateApplyMessage(applyMessageMap);
                // if(!children.contains(fridendMap)){
                // children.add(fridendMap);
                // }
                // if(!onlinekeys.contains(friendKeys)){
                // onlinekeys.add(friendKeys);
                // }
                // sendPersonHasChangedBroadcast();
                // byte[] meIconIdBytes = ByteAndInt.int2ByteArray(me.personHeadIconId);
                // byte[] nickeNameBytes = me.personNickeName.getBytes();
                // System.arraycopy(meIdBytes, 0, addFriendBuffer, 6, 4);
                addFriendKeys(person.macAddress);
                byte[] macBytes = me.macAddress.getBytes();
                System.arraycopy(macBytes, 0, addFriendBuffer, 48, 17);
                // �Է�����ͬ����Ӻ���ָ��
                DatagramPacket dp = new DatagramPacket(addFriendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress),
                        Constant.PORT);
                multicastSocket.send(dp);
                Log.d("lxx",
                        "InetAddress.getByName(person.ipAddress)"
                                + InetAddress.getByName(person.ipAddress));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void deleteFriend(Person person) {
            removeFriendKeys(person.macAddress);
            addFriendBuffer[4] = Constant.CMD_TYPE2;
            addFriendBuffer[5] = Constant.OPR_CMD4;
            // byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
            // System.arraycopy(meIdBytes, 0, addFriendBuffer, 6, 4);
            byte[] macBytes = me.macAddress.getBytes();
            System.arraycopy(macBytes, 0, addFriendBuffer, 48, 17);
            DatagramPacket dp;
            try {
                dp = new DatagramPacket(addFriendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress),
                        Constant.PORT);
                multicastSocket.send(dp);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void clearApplyList() {
            applyKeys.clear();
            Set<String> apply = new HashSet<String>();
            editor.putStringSet("applyKeys", apply);
            editor.commit();
        }

        // ��Է���Ӧͬ������ļ�ָ��
        public void receiveFiles(String fileSavePath) {
            count = 0;
            this.fileSavePath = fileSavePath;
            Person person = fridendMap.get(fileSenderMac);
            fileSendBuffer[4] = Constant.CMD_TYPE2;
            fileSendBuffer[5] = Constant.OPR_CMD2;
            byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
            System.arraycopy(meIdBytes, 0, fileSendBuffer, 6, 4);
            try {
                DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress), Constant.PORT);
                multicastSocket.send(dp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ���ļ������߷��;ܾ������ļ�ָ��
        public void refuseReceiveFile() {
            // isBusyNow = false;
            Person person = fridendMap.get(fileSenderMac);
            fileSendBuffer[4] = Constant.CMD_TYPE2;
            fileSendBuffer[5] = Constant.OPR_CMD3;
            byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
            System.arraycopy(meIdBytes, 0, fileSendBuffer, 6, 4);
            try {
                DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                        Constant.bufferSize,
                        InetAddress.getByName(person.ipAddress), Constant.PORT);
                multicastSocket.send(dp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ����������ļ����ļ���
        public ArrayList<FileState> getReceivedFileNames() {
            return receivedFileNames;
        }

        // ����������ļ����ļ���
        public ArrayList<FileState> getBeSendFileNames() {
            return beSendFileNames;
        }

        // =========================TCP�ļ�����ģ��==================================================================
        // ����Tcp������ļ��շ�ģ��
        private class FileHandler extends Thread {
            private ServerSocket sSocket = null;

            public FileHandler() {
            }

            @Override
            public void run() {
                super.run();
                try {
                    sSocket = new ServerSocket(Constant.PORT);
                    System.out.println("File Handler socket started ...");
                    while (!sSocket.isClosed() && null != sSocket) {
                        Socket socket = sSocket.accept();
                        socket.setSoTimeout(5000);
                        new SaveFileToDisk(socket).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // ������յ�������
            private class SaveFileToDisk extends Thread {
                private Socket socket = null;

                public SaveFileToDisk(Socket socket) {
                    this.socket = socket;
                }

                @Override
                public void run() {
                    super.run();
                    OutputStream output = null;
                    InputStream input = null;
                    try {
                        byte[] recvFileCmd = new byte[Constant.bufferSize];// ���նԷ���һ�η����������ݣ������ݰ��а�����Ҫ���͵��ļ���
                        input = socket.getInputStream();
                        input.read(recvFileCmd);// ��ȡ�Է�������������
                        int cmdType = recvFileCmd[4];// ��Э����λΪ��������
                        int oprCmd = recvFileCmd[5];// ��������
                        if (cmdType == Constant.CMD_TYPE1
                                && oprCmd == Constant.OPR_CMD6) {
                            byte[] fileNameBytes = new byte[Constant.fileNameLength];// ���յ������ݰ�����ȡ�ļ���
                            System.arraycopy(recvFileCmd, 10, fileNameBytes, 0,
                                    Constant.fileNameLength);
                            StringBuffer sb = new StringBuffer();
                            String fName = new String(fileNameBytes).trim();
                            sb.append(fileSavePath).append(File.separator)
                                    .append(fName);// ��ϳ��������ļ���
                            String fileName = sb.toString();
                            File file = new File(fileName);// ���ݻ�õ��ļ��������ļ�
                            // �������ݽ��ջ�������׼�����նԷ����������ļ�����
                            byte[] readBuffer = new byte[Constant.readBufferSize];
                            output = new FileOutputStream(file);// ���ļ������׼���ѽ��յ�������д���ļ���
                            int readSize = 0;
                            int length = 0;
                            long count = 0;
                            FileState fs = getFileStateByName(fName,
                                    receivedFileNames);

                            while (-1 != (readSize = input.read(readBuffer))) {// ѭ����ȡ����
                                output.write(readBuffer, 0, readSize);// �ѽ��յ�������д���ļ���
                                output.flush();
                                length += readSize;
                                count++;
                                if (count % 10 == 0) {
                                    fs.currentSize = length;
                                    fs.percent = ((int) ((Float.valueOf(length) / Float
                                            .valueOf(fs.fileSize)) * 100));
                                    Intent intent = new Intent();
                                    intent.setAction(Constant.fileReceiveStateUpdateAction);
                                    sendBroadcast(intent);
                                }
                            }
                            fs.currentSize = length;
                            fs.percent = ((int) ((Float.valueOf(length) / Float
                                    .valueOf(fs.fileSize)) * 100));
                            Intent intent = new Intent();
                            intent.setAction(Constant.fileReceiveStateUpdateAction);
                            sendBroadcast(intent);
                            receiveFileFinished(fName);
                        } else {
                            Intent intent = new Intent();
                            intent.putExtra("msg",
                                    getString(R.string.data_receive_error));
                            intent.setAction(Constant.dataReceiveErrorAction);
                            sendBroadcast(intent);
                        }
                    } catch (Exception e) {
                        Intent intent = new Intent();
                        intent.putExtra("msg", e.getMessage());
                        intent.setAction(Constant.dataReceiveErrorAction);
                        sendBroadcast(intent);
                        e.printStackTrace();
                    } finally {
                        try {
                            if (null != input)
                                input.close();
                            if (null != output)
                                output.close();
                            if (!socket.isClosed())
                                socket.close();
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }

            public void receiveFileFinished(String fileName) {
                count++;
                if (count == receivedFileNames.size()) {
                    Intent i = new Intent();
                    i.setAction(Constant.fileReceiveOrSendFinished);
                    sendBroadcast(i);
                }
                List<Message> messages = null;
                if (msgContainer.containsKey(fileSenderMac)) {
                    messages = msgContainer.get(fileSenderMac);
                    Log.d("lxx11", "msgContainer.containsKey(macAddressString)==" + fileSenderMac);
                } else {
                    messages = new ArrayList<Message>();
                }
                Message msg = new Message();
                msg.msg = fileName + getResources().getString(R.string.file_receive_success);
                msg.receivedTime = getCurrentTime();
                msg.isSendFileMessage = true;
                messages.add(msg);
                msgContainer.put(fileSenderMac, messages);
                Intent intent = new Intent();
                intent.setAction(Constant.hasMsgUpdatedAction);
                sendBroadcast(intent);
                Person person = fridendMap.get(fileSenderMac);
                fileSendBuffer[4] = Constant.CMD_TYPE3;
                fileSendBuffer[5] = Constant.OPR_CMD2;
                byte[] meIdBytes = ByteAndInt.int2ByteArray(me.personId);
                System.arraycopy(meIdBytes, 0, fileSendBuffer, 6, 4);
                try {
                    DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                            Constant.bufferSize,
                            InetAddress.getByName(person.ipAddress), Constant.PORT);
                    multicastSocket.send(dp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // ��ʼ���Է������ļ�
            public void startSendFile() {
                // ��ý��շ���Ϣ
                Person person = fridendMap.get(tempMac);
                final String userIp = person.ipAddress;
                // ���ͷ���ݰ��������ݰ��а���Ҫ���͵��ļ���
                final byte[] sendFileCmd = new byte[Constant.bufferSize];
                for (int i = 0; i < Constant.bufferSize; i++)
                    sendFileCmd[i] = 0;
                System.arraycopy(Constant.pkgHead, 0, sendFileCmd, 0, 3);
                sendFileCmd[3] = Constant.CMD82;
                sendFileCmd[4] = Constant.CMD_TYPE1;
                sendFileCmd[5] = Constant.OPR_CMD6;
                System.arraycopy(ByteAndInt.int2ByteArray(me.personId), 0,
                        sendFileCmd, 6, 4);
                for (final FileName file : tempFiles) {// ���ö��̷߳����ļ�
                    new Thread() {
                        @Override
                        public void run() {
                            Socket socket = null;
                            OutputStream output = null;
                            InputStream input = null;
                            try {
                                socket = new Socket(userIp, Constant.PORT);
                                byte[] fileNameBytes = file.getFileName()
                                        .getBytes();
                                int fileNameLength = Constant.fileNameLength + 10;// ���ͷ�ļ������ļ����洢�����Ա�д�µ��ļ���
                                for (int i = 10; i < fileNameLength; i++)
                                    sendFileCmd[i] = 0;
                                System.arraycopy(fileNameBytes, 0, sendFileCmd,
                                        10, fileNameBytes.length);// ���ļ�������ͷ���ݰ�
                                System.arraycopy(ByteAndInt
                                        .longToByteArray(file.fileSize), 0,
                                        sendFileCmd, 100, 8);
                                output = socket.getOutputStream();// ����һ�������
                                output.write(sendFileCmd);// ��ͷ���ݰ������Է�
                                output.flush();
                                sleep(1000);// sleep 1���ӣ��ȴ��Է�������
                                // �������ݷ��ͻ�����
                                byte[] readBuffer = new byte[Constant.readBufferSize];// �ļ���д����
                                input = new FileInputStream(new File(
                                        file.fileName));// ��һ���ļ�������
                                int readSize = 0;
                                int length = 0;
                                long count = 0;
                                FileState fs = getFileStateByName(
                                        file.getFileName(), beSendFileNames);
                                while (-1 != (readSize = input.read(readBuffer))) {// ѭ�����ļ����ݷ��͸��Է�
                                    output.write(readBuffer, 0, readSize);// ������д��������з��͸��Է�
                                    output.flush();
                                    length += readSize;

                                    count++;
                                    if (count % 10 == 0) {
                                        fs.currentSize = length;
                                        fs.percent = ((int) ((Float
                                                .valueOf(length) / Float
                                                .valueOf(fs.fileSize)) * 100));
                                        Intent intent = new Intent();
                                        intent.setAction(Constant.fileSendStateUpdateAction);
                                        sendBroadcast(intent);
                                    }
                                }
                                fs.currentSize = length;
                                fs.percent = ((int) ((Float.valueOf(length) / Float
                                        .valueOf(fs.fileSize)) * 100));
                                Intent intent = new Intent();
                                intent.setAction(Constant.fileSendStateUpdateAction);
                                sendBroadcast(intent);
                                sendFileFished(file.getFileName());
                            } catch (Exception e) {
                                // ������㷢���ļ����������Ϣ
                                Intent intent = new Intent();
                                intent.putExtra("msg", e.getMessage());
                                intent.setAction(Constant.dataSendErrorAction);
                                sendBroadcast(intent);
                                e.printStackTrace();
                            } finally {
                                try {
                                    if (null != output)
                                        output.close();
                                    if (null != input)
                                        input.close();
                                    if (!socket.isClosed())
                                        socket.close();
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }.start();
                }
            }

            public void sendFileFished(String fileName) {
                count++;
                if (count == beSendFileNames.size()) {
                    Intent i = new Intent();
                    i.setAction(Constant.fileReceiveOrSendFinished);
                    sendBroadcast(i);
                }
                List<Message> messages = null;
                if (msgContainer.containsKey(tempMac)) {
                    messages = msgContainer.get(tempMac);
                    Log.d("lxx", "msgContainer.containsKey(macAddressString)==" + tempMac);
                } else {
                    messages = new ArrayList<Message>();
                }
                Message msg = new Message();
                msg.msg = fileName + getResources().getString(R.string.file_send_success);
                msg.receivedTime = getCurrentTime();
                msg.isSendFileMessage = true;
                messages.add(msg);
                msgContainer.put(tempMac, messages);
                Intent intent = new Intent();
                intent.setAction(Constant.hasMsgUpdatedAction);
                sendBroadcast(intent);
            }

            // �����ļ������ļ�״̬�б��л�ø��ļ�״̬
            private FileState getFileStateByName(String fileName,
                    ArrayList<FileState> fileStates) {
                for (FileState fileState : fileStates) {
                    if (fileState.fileName.equals(fileName)) {
                        return fileState;
                    }
                }
                return null;
            }

            public void release() {
                try {
                    Log.d("lxx11", "sSocket release");
                    if (null != sSocket)
                        sSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // =========================TCP�ļ�����ģ�����==============================================================

    }

    // ========================Э�������ͨѶģ�����=======================================================

    // add-by chenlu

    public ArrayList<String> getApplyKeys() {
        // TODO Auto-generated method stub
        return applyKeys;
    }

    public String getMyMac() {
        // TODO Auto-generated method stub
        return localMacAddress;
    }

    public String getMyIp() {
        // TODO Auto-generated method stub
        return localIp;
    }

    // ������߷Ǻ���
    public Map<String, Person> getUsersMap() {
        // TODO Auto-generated method stub
        return childrenMap;
    }

    public Map<String, String> getApplyMessageMap() {
        return applyMessageMap;
    }

    public Map<String, String> getNickNameMap() {
        nickNameMap.clear();
        Set<String> nickNameSet = new HashSet<String>();
        nickNameSet = pre.getStringSet("nickNameMap", nickNameSet);
        Iterator<String> it = nickNameSet.iterator();
        while (it.hasNext()) {
            String key = it.next();
            String[] nickName = key.split(",");
            nickNameMap.put(nickName[0], nickName[1]);
        }
        return nickNameMap;
    }

    public ArrayList<String> getUserKeys() {
        // TODO Auto-generated method stub
        return personKeys;
    }
}
