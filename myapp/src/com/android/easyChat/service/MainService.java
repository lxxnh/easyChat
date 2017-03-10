
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
    private ServiceBinder sBinder = new ServiceBinder();// 服务绑定器
    private static ArrayList<Map<String, Person>> children = new ArrayList<Map<String, Person>>();// 保存所有组中的用户，每个map对象保存一个组的全部用户

    private static Map<String, Person> childrenMap = new HashMap<String, Person>();// 当前在线非好友用户
    private static Map<String, Person> fridendMap = new HashMap<String, Person>();// 当前在线好友
    private static Map<String, Person> applyFriendsMap = new HashMap<String, Person>();// 好友验证

    private static ArrayList<ArrayList<String>> onlinekeys = new ArrayList<ArrayList<String>>();// 当前在线用户mac

    private static ArrayList<String> personKeys = new ArrayList<String>();// 当前在线非好友mac
    private static ArrayList<String> friendKeys = new ArrayList<String>();// 当前在线好友用户mac
    private static ArrayList<String> applyKeys = new ArrayList<String>();// 好友申请mac
    private static Map<String, String> applyMessageMap = new HashMap<String, String>();// 好友验证消息
    private static Map<String, String> nickNameMap = new HashMap<String, String>();
    private static Set<String> allFriendKeys;// 好友
    private static Map<String, List<Message>> msgContainer = new HashMap<String, List<Message>>();// 所有用户信息容器
    private SharedPreferences pre = null;
    private SharedPreferences.Editor editor = null;
    private WifiManager wifiManager = null;
    private ServiceBroadcastReceiver receiver = null;
    public InetAddress localInetAddress = null;
    private String localMacAddress = null;// 本机Mac地址
    private String localIp = null;
    private byte[] localIpBytes = null;
    private byte[] regBuffer = new byte[Constant.bufferSize];// 本机网络注册交互指令
    /*
     * regBuffer: 0-2:pkgHead,该应用发出的数据报 3：命令字 4：命令类型 5：操作命令 6-9：用户ID。此处已无特殊含义 10-13：iconID 14-43：昵称
     * 44-47：ip 48-64：mac地址
     */
    private byte[] addFriendBuffer = new byte[Constant.bufferSize];// 添加好友
    private byte[] msgSendBuffer = new byte[Constant.bufferSize];// 信息发送交互
    private byte[] fileSendBuffer = new byte[Constant.bufferSize];// 文件发送交互指令
    private static Person me = null;// 用来保存自身的相关信息
    private CommunicationBridge comBridge = null;// 通讯与协议解析模块

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
        initCmdBuffer();// 初始化指令缓存
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        localMacAddress = wifiInfo.getMacAddress();// 获取本机Mac地址
        new CheckNetConnectivity().start();// 侦测网络状态，获取IP地址

        comBridge = new CommunicationBridge();// 启动socket连接
        comBridge.start();

        pre = PreferenceManager.getDefaultSharedPreferences(this);
        editor = pre.edit();
        createDownloadDirectory();

        regBroadcastReceiver();// 注册广播接收器
        getMyInfomation();// 获得自身信息
        new UpdateMe().start();// 向网络发送心跳包，并注册
        new CheckUserOnline().start();// 检查用户列表是否有超时用户
        sendPersonHasChangedBroadcast();// 通知有新用户加入或退出
    }

    // 服务绑定
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

    // 获得自已的相关信息
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
        String nickeName = pre.getString("nickeName", android.os.Build.MODEL);// 默认昵称为手机型号
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
        // 更新注册命令用户数据
        byte[] localMacAddressBytes = localMacAddress.getBytes();
        System.arraycopy(localMacAddressBytes, 0, regBuffer, 48, localMacAddressBytes.length);// 存入Mac地址
        System.arraycopy(ByteAndInt.int2ByteArray(myId), 0, regBuffer, 6, 4);
        System.arraycopy(ByteAndInt.int2ByteArray(iconId), 0, regBuffer, 10, 4);
        for (int i = 14; i < 44; i++)
            regBuffer[i] = 0;// 把原来的昵称内容清空
        byte[] nickeNameBytes = nickeName.getBytes();
        System.arraycopy(nickeNameBytes, 0, regBuffer, 14,
                nickeNameBytes.length);

    }

    private String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = new Date();
        return dateFormat.format(date);
    }

    // 检测网络连接状态,获得本机IP地址
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

    // 初始化指令缓存
    private void initCmdBuffer() {
        // 初始化用户注册指令缓存
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

        // 初始化信息发送指令缓存
        for (int i = 0; i < Constant.bufferSize; i++)
            msgSendBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, msgSendBuffer, 0, 3);
        msgSendBuffer[3] = Constant.CMD81;
        msgSendBuffer[4] = Constant.CMD_TYPE1;
        msgSendBuffer[5] = Constant.OPR_CMD1;

        // 初始化发送文件指令缓存
        for (int i = 0; i < Constant.bufferSize; i++)
            fileSendBuffer[i] = 0;
        System.arraycopy(Constant.pkgHead, 0, fileSendBuffer, 0, 3);
        fileSendBuffer[3] = Constant.CMD82;
        fileSendBuffer[4] = Constant.CMD_TYPE1;
        fileSendBuffer[5] = Constant.OPR_CMD1;

    }

    // 获得所有用户对象
    public ArrayList<Map<String, Person>> getChildren() {
        return children;
    }

    // 获得所有用户id
    public ArrayList<ArrayList<String>> getOnlineKeys() {
        return onlinekeys;
    }

    // 根据用户id获得该用户的消息
    public List<Message> getMessagesByMac(String mac) {
        Log.d("lxx", "service mac= " + mac);
        return msgContainer.get(mac);
    }

    // 根据用户id获得该用户的消息数量
    public int getMessagesCountByMac(String mac) {
        List<Message> msgs = msgContainer.get(mac);
        if (null != msgs) {
            return msgs.size();
        } else {
            return 0;
        }
    }

    // 每隔3秒发送一个心跳包
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

    // 检测用户是否在线，如果超过20秒说明用户已离线，则从列表中清除该用户
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

    // 发送用户更新广播
    private void sendPersonHasChangedBroadcast() {
        Intent intent = new Intent();
        intent.setAction(Constant.personHasChangedAction);
        sendBroadcast(intent);
    }

    // 注册广播接收器
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

    // 广播接收器处理类
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

    // 清空好友验证列表
    public void clearApplyList() {
        comBridge.clearApplyList();
    }

    public void deleteFriend(Person person) {
        comBridge.deleteFriend(person);
    }

    // 发送信息
    public void sendMsg(String mac, String msg) {
        comBridge.sendMsg(mac, msg);
    }

    // 发送文件
    public void sendFiles(String mac, ArrayList<FileName> files) {
        comBridge.sendFiles(mac, files);
    }

    // 接收文件
    public void receiveFiles(String fileSavePath) {
        comBridge.receiveFiles(fileSavePath);
    }

    // 获得欲接收的文件名
    public ArrayList<FileState> getReceivedFileNames() {
        return comBridge.getReceivedFileNames();
    }

    // 获得欲发送的文件名
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

    // ========================协议分析与通讯模块=======================================================
    private class CommunicationBridge extends Thread {
        private MulticastSocket multicastSocket = null;
        private byte[] recvBuffer = new byte[Constant.bufferSize];
        private String fileSenderMac;// 用来保存文件发送者的mac地址
        private boolean isBusyNow = false;// 现在是否正在收发文件，如果该状态为true则表示现在正在进行收发文件操作，这时需要向其它发送文件的用户发送忙指令
        private String fileSavePath = null;// 用来保存接收到的文件
        private ArrayList<FileName> tempFiles = null;// 用来临时保存需要发送的文件名
        private String tempMac;// 用来临时保存需要发送文件的用户mac(接受文件方的用户mac)
        private ArrayList<FileState> receivedFileNames = new ArrayList<FileState>();
        private ArrayList<FileState> beSendFileNames = new ArrayList<FileState>();

        private FileHandler fileHandler = null;// 文件处理线程，用来收发文件
        private int count = 0;

        public CommunicationBridge() {
            fileHandler = new FileHandler();
            fileHandler.start();
            Log.d("lxx", "CommunicationBridge");
        }

        // 打开组播端口，准备组播通讯
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

        // 解析接收到的数据包
        private void parsePackage(byte[] pkg) {
            Log.d("lxx", "parsePackage enter");
            int CMD = pkg[3];// 命令字
            int cmdType = pkg[4];// 命令类型
            int oprCmd = pkg[5];// 操作命令


            // 获得用户MAC
            byte[] macAddressBytes = new byte[17];
            System.arraycopy(pkg, 48, macAddressBytes, 0, 17);
            String macAddressString = new String(macAddressBytes);// 得到字符串形式的mac地址

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
                                case Constant.OPR_CMD2:// 收到同意添加好友
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
                                case Constant.OPR_CMD3:// 收到拒絕添加好友
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
                            // 如果该信息不是自己发出则给对方发送回应包,并把对方加入用户列表
                            Log.d("lxx", "macAddressString=" + macAddressString + "me.macAddress"
                                    + me.macAddress);
                            if (!macAddressString.equals(me.macAddress)) {
                                updatePerson(macAddressString, pkg);
                                // 发送应答包
                                byte[] ipBytes = new byte[4];// 获得请求方的ip地址
                                System.arraycopy(pkg, 44, ipBytes, 0, 4);
                                try {
                                    InetAddress targetIp = InetAddress
                                            .getByAddress(ipBytes);
                                    regBuffer[4] = Constant.CMD_TYPE2;// 把自己的注册信息修改成应答信息标志，把自己的信息发送给请求方
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
                case Constant.CMD81:// 收到信息
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
                        case Constant.CMD_TYPE1:// 收到文件传输请求
                            switch (oprCmd) {
                                case Constant.OPR_CMD1:
                                    // 发送广播，通知界面有文件需要传输
                                    if (!isBusyNow) {
                                        // isBusyNow = true;
                                        fileSenderMac = macAddressString;// 保存文件发送者的mac，以便后面若接收者拒绝接收文件时可以通过该mac找到发送者，并给发送者发送拒绝接收指令
                                        Person person = fridendMap.get(macAddressString);
                                        Intent intent = new Intent();
                                        intent.putExtra("person", person);
                                        intent.setAction(Constant.receivedSendFileRequestAction);
                                        sendBroadcast(intent);
                                    } else {// 如果当前正在收发文件则向对方发送忙指令
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
                                case Constant.OPR_CMD5:// 接收对方传过来的文件名信息
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
                                case Constant.OPR_CMD2:// 对方同意接收文件
                                    count = 0;
                                    fileHandler.startSendFile();
                                    System.out
                                            .println("Start send file to remote user ...");
                                    break;
                                case Constant.OPR_CMD3:// 对方拒绝接收文件
                                    Intent intent = new Intent();
                                    intent.setAction(Constant.remoteUserRefuseReceiveFileAction);
                                    sendBroadcast(intent);
                                    System.out
                                            .println("Remote user refuse to receive file ...");
                                    break;
                                case Constant.OPR_CMD4:// 对方现在忙
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
                     * "对方成功接収您发的文件"; msg.receivedTime = getCurrentTime(); msg.isSendFileMessage =
                     * true; messages.add(msg); msgContainer.put(macAddressString, messages); Intent
                     * intent = new Intent(); intent.setAction(Constant.hasMsgUpdatedAction);
                     * sendBroadcast(intent); }
                     */
                    }
                    break;
            }
        }

        // 更新或加用户信息到用户列表中
        private void updatePerson(String mac, byte[] pkg) {
            Person person = new Person();
            getPerson(pkg, person);
            Log.d("lxx", "allFriendKeys : " + allFriendKeys.toString() + " --person.timeStamp="
                    + person.timeStamp);
            if (!allFriendKeys.contains(mac)) {
                // if (childrenMap.containsKey(mac)) {
                // childrenMap.remove(mac);
                // }
                childrenMap.put(mac, person);// 如果好友里没有它，加入非好友列表
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

        // 关闭Socket连接
        private void release() {
            try {
                regBuffer[4] = Constant.CMD_TYPE3;// 把命令类型修改成注消标志，并广播发送，从所有用户中退出
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

        // 分析数据包并获取一个用户信息
        private void getPerson(byte[] pkg, Person person) {

            byte[] personIdBytes = new byte[4];
            byte[] iconIdBytes = new byte[4];
            byte[] nickeNameBytes = new byte[30];
            byte[] personIpBytes = new byte[4];
            byte[] personMacBytes = new byte[17];// add-by chenlu 获得MAC

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

        // 注册自己到网络中
        public void joinOrganization() {
            try {
                if (null != multicastSocket && !multicastSocket.isClosed()) {
                    regBuffer[4] = Constant.CMD_TYPE1;// 恢复成注册请求标志，向网络中注册自己
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

        // 发送信息
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

        // 向对方发送请求接收文件指令
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
                    int fileNameLength = Constant.fileNameLength + 65;// 清除头文件包的文件名存储区域，以便写新的文件名
                    // 把要传送的所有文件名传送给对方
                    for (final FileName file : tempFiles) {
                        // 收集生成要发送文件的文相关资料
                        FileState fs = new FileState(file.fileSize, 0,
                                file.getFileName());
                        beSendFileNames.add(fs);

                        byte[] fileNameBytes = file.getFileName().getBytes();
                        for (int i = 65; i < fileNameLength; i++)
                            fileSendBuffer[i] = 0;
                        System.arraycopy(fileNameBytes, 0, fileSendBuffer, 65,
                                fileNameBytes.length);// 把文件名放入头数据包
                        System.arraycopy(
                                ByteAndInt.longToByteArray(file.fileSize), 0,
                                fileSendBuffer, 164, 8);
                        DatagramPacket dp = new DatagramPacket(fileSendBuffer,
                                Constant.bufferSize,
                                InetAddress.getByName(person.ipAddress),
                                Constant.PORT);
                        multicastSocket.send(dp);
                    }
                    // 对方发送请求接收文件指令
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
                // 对方发送请求TIANJIAHAOYOU指令
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
                // 对方发送拒絕添加好友指令
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
                // 对方发送同意添加好友指令
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

        // 向对方响应同意接收文件指令
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

        // 向文件发送者发送拒绝接收文件指令
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

        // 获得欲接收文件的文件名
        public ArrayList<FileState> getReceivedFileNames() {
            return receivedFileNames;
        }

        // 获得欲发送文件的文件名
        public ArrayList<FileState> getBeSendFileNames() {
            return beSendFileNames;
        }

        // =========================TCP文件传输模块==================================================================
        // 基于Tcp传输的文件收发模块
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

            // 保存接收到的数据
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
                        byte[] recvFileCmd = new byte[Constant.bufferSize];// 接收对方第一次发过来的数据，该数据包中包含了要发送的文件名
                        input = socket.getInputStream();
                        input.read(recvFileCmd);// 读取对方发过来的数据
                        int cmdType = recvFileCmd[4];// 按协议这位为命令类型
                        int oprCmd = recvFileCmd[5];// 操作命令
                        if (cmdType == Constant.CMD_TYPE1
                                && oprCmd == Constant.OPR_CMD6) {
                            byte[] fileNameBytes = new byte[Constant.fileNameLength];// 从收到的数据包中提取文件名
                            System.arraycopy(recvFileCmd, 10, fileNameBytes, 0,
                                    Constant.fileNameLength);
                            StringBuffer sb = new StringBuffer();
                            String fName = new String(fileNameBytes).trim();
                            sb.append(fileSavePath).append(File.separator)
                                    .append(fName);// 组合成完整的文件名
                            String fileName = sb.toString();
                            File file = new File(fileName);// 根据获得的文件名创建文件
                            // 定义数据接收缓冲区，准备接收对方传过来的文件内容
                            byte[] readBuffer = new byte[Constant.readBufferSize];
                            output = new FileOutputStream(file);// 打开文件输出流准备把接收到的内容写到文件中
                            int readSize = 0;
                            int length = 0;
                            long count = 0;
                            FileState fs = getFileStateByName(fName,
                                    receivedFileNames);

                            while (-1 != (readSize = input.read(readBuffer))) {// 循环读取内容
                                output.write(readBuffer, 0, readSize);// 把接收到的内容写到文件中
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

            // 开始给对方发送文件
            public void startSendFile() {
                // 获得接收方信息
                Person person = fridendMap.get(tempMac);
                final String userIp = person.ipAddress;
                // 组合头数据包，该数据包中包括要发送的文件名
                final byte[] sendFileCmd = new byte[Constant.bufferSize];
                for (int i = 0; i < Constant.bufferSize; i++)
                    sendFileCmd[i] = 0;
                System.arraycopy(Constant.pkgHead, 0, sendFileCmd, 0, 3);
                sendFileCmd[3] = Constant.CMD82;
                sendFileCmd[4] = Constant.CMD_TYPE1;
                sendFileCmd[5] = Constant.OPR_CMD6;
                System.arraycopy(ByteAndInt.int2ByteArray(me.personId), 0,
                        sendFileCmd, 6, 4);
                for (final FileName file : tempFiles) {// 采用多线程发送文件
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
                                int fileNameLength = Constant.fileNameLength + 10;// 清除头文件包的文件名存储区域，以便写新的文件名
                                for (int i = 10; i < fileNameLength; i++)
                                    sendFileCmd[i] = 0;
                                System.arraycopy(fileNameBytes, 0, sendFileCmd,
                                        10, fileNameBytes.length);// 把文件名放入头数据包
                                System.arraycopy(ByteAndInt
                                        .longToByteArray(file.fileSize), 0,
                                        sendFileCmd, 100, 8);
                                output = socket.getOutputStream();// 构造一个输出流
                                output.write(sendFileCmd);// 把头数据包发给对方
                                output.flush();
                                sleep(1000);// sleep 1秒钟，等待对方处理完
                                // 定义数据发送缓冲区
                                byte[] readBuffer = new byte[Constant.readBufferSize];// 文件读写缓存
                                input = new FileInputStream(new File(
                                        file.fileName));// 打开一个文件输入流
                                int readSize = 0;
                                int length = 0;
                                long count = 0;
                                FileState fs = getFileStateByName(
                                        file.getFileName(), beSendFileNames);
                                while (-1 != (readSize = input.read(readBuffer))) {// 循环把文件内容发送给对方
                                    output.write(readBuffer, 0, readSize);// 把内容写到输出流中发送给对方
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
                                // 往界面层发送文件传输出错信息
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

            // 根据文件名从文件状态列表中获得该文件状态
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
        // =========================TCP文件传输模块结束==============================================================

    }

    // ========================协议分析与通讯模块结束=======================================================

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

    // 获得在线非好友
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
