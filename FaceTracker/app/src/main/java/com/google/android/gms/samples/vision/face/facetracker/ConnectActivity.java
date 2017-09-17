package com.google.android.gms.samples.vision.face.facetracker;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;

import org.json.JSONException;
import org.json.JSONObject;

public class ConnectActivity extends AppCompatActivity {

    TextView text;
    TextView aboutView;
    TextView emailView;
    private String about;
    private String birthday;
    private String email;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        Intent intent = getIntent();
        String userName = intent.getStringExtra(HackMitActivity.VIEW_USER_MESSAGE);
        text = (TextView) findViewById(R.id.user_name);
        aboutView = (TextView) findViewById(R.id.about_me);
        emailView = (TextView) findViewById(R.id.email);
        text.setText(userName);
       // getFriendInformation();
    }

    private void getFriendInformation(){
        new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "/{user-id}",
                null,
                HttpMethod.GET,
                new GraphRequest.Callback() {
                    public void onCompleted(GraphResponse response) {
            /* handle the result */
                        JSONObject object = response.getJSONObject();
                        try {
                            email = object.getString("email");
                            emailView.setText(email);
                            about = object.getString("about");
                            aboutView.setText(about);
                            birthday = object.getString("birthday");
                        } catch (JSONException e ){
                            e.printStackTrace();
                        }
                    }
                }
        ).executeAsync();
    }

}
