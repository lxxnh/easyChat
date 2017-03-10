
package com.android.easyChat.util;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    public String receivedTime = null;
    public String msg = null;
    public boolean isSendFileMessage;

    public Message() {
    };

    public Message(String receivedTime, String msg) {
        this.receivedTime = receivedTime;
        this.msg = msg;
    }
}