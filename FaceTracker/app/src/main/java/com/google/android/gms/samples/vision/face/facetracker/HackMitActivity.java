/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.ClarifaiResponse;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Prediction;

import android.widget.Button;

import com.facebook.login.widget.LoginButton;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.AddPersistedFaceResult;
import com.microsoft.projectoxford.face.contract.Candidate;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.Person;
import com.microsoft.projectoxford.face.rest.ClientException;


/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class HackMitActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";
    private static final String NON_NAME = "";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private Button mViewUserButton;

   //private ClarifaiClient clarifaiClient;

   // private ClarifaiAddInputThread clarifaiAddInputThread;
    public String predictedName = NON_NAME;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    public static final String VIEW_USER_MESSAGE = "com.lowjiansheng.MESSAGE";

    private Intent connectToViewUserIntent;

    private LoginButton loginButton;
    private CallbackManager callbackManager;

    private String MICROSOFT_FACE_API_KEY = "d08b80746a1d4d97b46e552c994c4f35";
    public static final String uriBase = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0";

    private FaceServiceClient faceServiceClient;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        FacebookSdk.sdkInitialize(getApplicationContext());
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mViewUserButton = (Button) findViewById(R.id.view_user);
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);


        loginButton = (LoginButton) findViewById(R.id.login_button);
        loginButton.setReadPermissions("email");

        callbackManager = CallbackManager.Factory.create();

        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                System.out.println("Got here");
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onError(FacebookException error) {
                System.out.println(error);
            }
        });

        faceServiceClient =
                new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0",
                        MICROSOFT_FACE_API_KEY);

        mViewUserButton.setVisibility(View.INVISIBLE);

       // clarifaiClient = new ClarifaiBuilder(API_KEY).buildSync();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
        connectToViewUserIntent = new Intent(this, ConnectActivity.class);

        mViewUserButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start new intent and bring user to the next activity.
                startActivity(connectToViewUserIntent);
            }
        });
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.NO_LANDMARKS)
                .build();

        ModifiedFaceDetector modifiedFaceDetector = new ModifiedFaceDetector(detector);
        modifiedFaceDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!modifiedFaceDetector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, modifiedFaceDetector)
                .setRequestedPreviewSize(800  , 600)
                .setAutoFocusEnabled(true)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(30.0f)
                .build();
    }

    private ClarifaiPredictThread clarifaiPredictThread;

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        clarifaiPredictThread = new ClarifaiPredictThread();
        clarifaiPredictThread.start();
       // clarifaiAddInputThread = new ClarifaiAddInputThread();
       // clarifaiAddInputThread.start();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void setButtonVisible(final boolean buttonVisible, final String text){

        Handler mainHandler = new Handler(getApplicationContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (buttonVisible){
                    mViewUserButton.setVisibility(View.VISIBLE);
                    mViewUserButton.setText("Click to view " + text);
                }
                else {
                    mViewUserButton.setVisibility(View.INVISIBLE);
                    mViewUserButton.setText(text);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
            mFaceGraphic.updateName(predictedName);
            setButtonVisible(true, predictedName);
            final Face dupItem = item;
            mCameraSource.takePicture(null, new CameraSource.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] bytes) {
                    clarifaiPredictThread.addImage(bytes);
                }
            });
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            mFaceGraphic.updateName(predictedName);
            setButtonVisible(true, predictedName);
            connectToViewUserIntent.putExtra(VIEW_USER_MESSAGE, predictedName);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
            setButtonVisible(false, NON_NAME);
            connectToViewUserIntent.removeExtra(VIEW_USER_MESSAGE);

        }
    }
    class ModifiedFaceDetector extends Detector<Face> {

        private Detector mDelegate;

        public ModifiedFaceDetector(Detector delegate){
            mDelegate = delegate;
        }

        @Override
        public SparseArray<Face> detect(Frame frame) {
            return mDelegate.detect(frame);
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }

    public class ClarifaiPredictThread extends Thread{

        ConcurrentLinkedQueue<byte[]> frames;

        public ClarifaiPredictThread() {
            frames = new ConcurrentLinkedQueue();
        }

        @Override
        public void run() {
            while (true) {
                if (!frames.isEmpty()) {

                    byte[] frame = frames.poll();
                    ByteArrayInputStream bis = new ByteArrayInputStream(frame);
                    DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
                    Date dateobj = new Date();
                    com.microsoft.projectoxford.face.contract.Face[] faceId;
                    UUID faceIdUUID;
                    try{
                        faceId = faceServiceClient.detect(bis, true, false, null);
                     //   AddPersistedFaceResult result  = faceServiceClient.AddFaceToFaceList("hackmit_fd", bis, null, null);
                      //  faceId = result.persistedFaceId;
                        System.out.println(faceId);
                        for (int j = 0 ; j < faceId.length; j++ ) {
                            faceIdUUID = faceId[j].faceId;
                            UUID[] faceIds = {faceIdUUID};
                            IdentifyResult[] identifyResults = faceServiceClient.identity("friends", faceIds, 1);
                            for (int i = 0 ; i < identifyResults.length; i++) {
                                List<Candidate> candidates = identifyResults[i].candidates;
                                for (int k = 0 ; k < candidates.size(); k++ ){
                                    Person person = faceServiceClient.getPerson("friends", candidates.get(k).personId);
                                    predictedName = person.name;
                                }
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClientException e) {
                        e.printStackTrace();
                    }

                    /*
                    ClarifaiResponse<List<ClarifaiOutput<Prediction>>> res = clarifaiClient
                            .predict("new friends").withInputs(ClarifaiInput.forImage(frame)).executeSync();

                    if (res.isSuccessful()){
                        List<ClarifaiOutput<Prediction>> results = res.get();
                        for (ClarifaiOutput<Prediction> result : results) {
                            List<Prediction> predictons = result.data();
                            float max = 0;
                            predictedName = NON_NAME;
                            for (Prediction prediction : predictons) {
                                if (prediction.asConcept().value() > max) {
                                    max = prediction.asConcept().value();
                                    predictedName = prediction.asConcept().name();
                                }
                            }
                            if (max < 0.5) {
                                predictedName = NON_NAME;
                            }
                            Input input = new Input(frame, predictedName);
                            //clarifaiAddInputThread.addInput(input);
                        }
                    }
                    else {
                        System.out.println("Failed");
                    }
                    */

                }
            }
        }

        public void addImage(byte[] frame) {
            frames.add(frame);
        }
    }

    public class ClarifaiAddInputThread extends Thread{

        ConcurrentLinkedQueue<Input> inputs;

        public ClarifaiAddInputThread(){
            inputs = new ConcurrentLinkedQueue<>();
        }

        public void addInput(Input input) {
            inputs.add(input);
        }

        /*
        public void run(){
            while (true) {
                if (!inputs.isEmpty()) {
                    Input input = inputs.poll();
                    System.out.println("Adding inputs!");
                    clarifaiClient.addInputs()
                            .plus(ClarifaiInput.forImage(input.picture)
                            .withConcepts(
                                    Concept.forName(input.concept)
                            ))
                            .executeSync();
                }
            }
        }*/
    }


    public class Input{
        String concept;
        byte[] picture;

        public Input(byte[] picture, String concept){
            this.picture = picture;
            this.concept = concept;
        }
    }
}
