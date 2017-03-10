
package com.android.easyChat.home;

import com.android.easyChat.R;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.Person;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SendApplyActivity extends Activity {
    private Person person = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_apply);
        TextView back = (TextView) findViewById(R.id.back);
        TextView send = (TextView) findViewById(R.id.send);
        // EditText validateMsg = (EditText)findViewById(R.id.validateMsg);
        // String message = validateMsg.getText().toString();
        Intent intent = getIntent();
        person = (Person) intent.getExtras().getSerializable("PersonInfo");
        // ==========°ó¶¨¼àÌýÆ÷==================
        send.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                EditText validateMsg = (EditText) findViewById(R.id.validateMsg);
                String message = validateMsg.getText().toString();
                person.setApplyMessage(message);
                Intent intent = new Intent();
                intent.setAction(Constant.addFriendRequestAction);
                intent.putExtra("PersonInfo", person);
                Intent intent1 = new Intent();
                intent1.setClass(SendApplyActivity.this, EasyChatMainActivity.class);
                Toast toast = Toast.makeText(SendApplyActivity.this, R.string.send_apply_success,
                        Toast.LENGTH_LONG);
                LinearLayout toastView = (LinearLayout) toast.getView();
                toast.setGravity(Gravity.CENTER, 0, 0);
                ImageView imageView = new ImageView(getApplicationContext());
                imageView.setImageResource(R.drawable.ic_send_btnstyle_holo_light);
                toastView.addView(imageView, 0);
                toast.show();
                sendBroadcast(intent);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                startActivity(intent1);
                // finish();
            }
        });
        back.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.send_apply, menu);
        return true;
    }

}
