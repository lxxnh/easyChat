
package com.android.easyChat.home;


import com.android.easyChat.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class MainActivity extends Activity {

    /**
     * MainService服务与当前Activity的绑定连接器
     */


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Handler handler = new Handler();
        handler.postDelayed(new Loading(), 3000);
    }

    class Loading implements Runnable {

        @Override
        public void run() {

            Intent intent = new Intent(MainActivity.this, EasyChatMainActivity.class);
            startActivity(intent);
            MainActivity.this.finish();
        }

    }

}
