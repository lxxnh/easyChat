
package com.android.easyChat.util;

import java.io.Serializable;

public class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    public int personId = 0;
    public int personHeadIconId = 0;
    public String personNickeName = null;
    public String ipAddress = null;
    public String macAddress = null;// add-by chenlu
    public String loginTime = null;
    public long timeStamp = 0;
    public int groupId = 0;
    public String applyMessage;

    public Person(int personId, int personHeadIconId, String personNickeName, String ipAddress,
            String loginTime) {
        this.personId = personId;
        this.personHeadIconId = personHeadIconId;
        this.personNickeName = personNickeName;
        this.ipAddress = ipAddress;
        this.loginTime = loginTime;
    }

    public Person() {
    }

    // add -by chenlu
    public int getPersonHeadIconId() {
        return personHeadIconId;
    }

    public void setPersonHeadIconId(int personHeadIconId) {
        this.personHeadIconId = personHeadIconId;
    }

    public String getPersonNickeName() {
        return personNickeName;
    }

    public String getApplyMessage() {
        return applyMessage;
    }

    public void setApplyMessage(String applyMessage) {
        this.applyMessage = applyMessage;
    }

    public void setPersonNickeName(String personNickeName) {
        this.personNickeName = personNickeName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

}
