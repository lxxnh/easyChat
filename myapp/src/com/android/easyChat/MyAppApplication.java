
package com.android.easyChat;

import com.android.easyChat.service.MainService;

import android.app.Application;

public class MyAppApplication extends Application {
    private MainService service;

    public MainService getService() {
        return service;
    }

    public void setService(MainService mService) {
        this.service = mService;
    }

}
