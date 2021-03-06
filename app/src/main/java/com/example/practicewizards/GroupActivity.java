package com.example.practicewizards;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Group Activity class
 * initializes variables for use in various methods throughout this activity.
 * Uses TextureView to create a new SurfaceTextureListener.
 * Methods include actions such as setting up the camera, calls connectCamera()
 * Methods also handled are those such as the size changing, if it is destroyed,
 * or if it has updated.
 */
public class GroupActivity extends AppCompatActivity {
    private static final String TAG = "GroupActivity.java";
    private File groupPhotoFolder;
    private String groupPhotoFileName;

    private static int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    // State members
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    // Hold the state
    private int captureState = STATE_PREVIEW;


    private TextureView groupView;
    private TextureView.SurfaceTextureListener groupTextListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setUpCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    //CAMERA DEVICE FOR GROUP VIEW
    private CameraDevice groupCameraDevice;
    private CameraDevice.StateCallback groupCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            groupCameraDevice = camera;
            try {
                startPic();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            groupCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            groupCameraDevice = null;
        }
    };

    //FUNCTIONS MEMBER VARIABLES
    private HandlerThread groupBackgroundHandlerThread;
    private Handler groupBackgroundHandler;
    private String groupCameraDeviceId; // for setup of the camera

    // Bitmap of image
    private Bitmap bitmap;

    private final ImageReader.OnImageAvailableListener groupOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Call our runnable to save photo to storage
                    // Post to the handler the latest image reader
                    groupBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };

    // Nested runnable class
    private class ImageSaver implements Runnable {
        private final Image image;

        public ImageSaver(Image image) {
            this.image = image;
        }

        /**
         * A runnable method to be ran by handler when image is available
         */
        @Override
        public void run() {
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            // Remaining bytes
            byte[] bytes = new byte[byteBuffer.remaining()];
            // Call get to retrieve all the bytes representing the image data
            byteBuffer.get(bytes);
            // Now put bytes into file
            FileOutputStream fileOutputStream = null;
            try {
                // createPhotoFolder() should have already been called
                Log.i(TAG, "Write the photo to the photo filename");
                fileOutputStream = new FileOutputStream(groupPhotoFileName); // open file
                fileOutputStream.write(bytes); // Write the bytes to the file
                Log.d(TAG, "File Name: " + groupPhotoFileName);

                Log.i(TAG, "File saved");

                // Update the UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // We're done saving, set next button visible
                        findViewById(R.id.to_selfie).setVisibility(View.VISIBLE);
                        // Allow user to click next
                        // Find the view and set its visibility on
                        final ImageView imageView = findViewById(R.id.groupImageDisplayView);
                        // Use picasso to pull the file image and center it inside of the image view
                        // Use .memoryPolicy() with NO_CACHE to help Picasso skip looking for
                        // image in cache and NO_STORE to not store final result of picasso load
                        // file in cache at all!
                        Picasso.with(getApplicationContext())
                                .load(new File(groupPhotoFileName))
                                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                                .into(imageView);
                        // Hide texture view but keep space dimensions
                        findViewById(R.id.groupView).setVisibility(View.INVISIBLE);
                        // Show user image
                        imageView.setVisibility(View.VISIBLE);

                        Log.d(TAG, "Set Display View to visible");

                        imageView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Now hide from user
                                imageView.setVisibility(View.INVISIBLE);
                                // And reopen live stream
                                findViewById(R.id.groupView).setVisibility(View.VISIBLE);
                            }
                        }, 3000);

                        //groupTakeImageButton.setText(R.string.retake);
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException nullPtr) {
                Log.e(TAG, "Null something");
                nullPtr.printStackTrace();
            } finally {
                // Close image
                Log.i(TAG, "Close the output stream");
                image.close(); // Close image
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private CameraCaptureSession previewCaptureSession;
    private CameraCaptureSession.CaptureCallback previewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult captureResult) {
                    switch (captureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            // Set state back to preview to avoid taking tons of pics
                            captureState = STATE_PREVIEW;

                            // Integer for Auto Focus State
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            // SUPPORT new and old devices
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                startStillCapture();
                                Log.i(TAG, "AF Locked");
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result); // Start Still Capture
                }
            };
    private CaptureRequest.Builder groupCaptureRequestBuilder;
    // Mapping integers to integers
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    // Image size
    private Size imageSize;
    // Image reader
    private ImageReader imageReader;

    // Clockwise rotation starting at zero (landscape turned left mode)
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);     // Left
        ORIENTATIONS.append(Surface.ROTATION_90, 90);   // Upright
        ORIENTATIONS.append(Surface.ROTATION_180, 180); // Right
        ORIENTATIONS.append(Surface.ROTATION_270, 270); // Upside Down
    }


    // Button to take group photo
    private Button groupTakeImageButton;

    /**
     * Creates views, also Logs the file location from public path.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set views
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);
        getWindow().getDecorView().setBackgroundColor(Color.argb(255, 0, 100, 100));

        // Array of Needed Permission Strings
        // Camera permission is handled in a callback
        String[] permissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE, // INDEX 0
                Manifest.permission.READ_EXTERNAL_STORAGE   // INDEX 1
        };

        // Now check to make sure all permissions are granted
        if (!checkPermissions(permissions)) {
            // If not granted, show error
            Toast.makeText(this, "Must allow file saving permissions",
                    Toast.LENGTH_SHORT).show();
        } else {
            // Get our photo folder ready
            createPhotoFolder();

            // Log
            Log.i(TAG, "Files Location" + groupPhotoFolder.getAbsolutePath());
        }

        // Try to create a unique photo file
        try {
            createPhotoFileName();
        } catch (IOException e) {
            Log.e(TAG, "Error in calling createPhotoFileName()");
            e.printStackTrace();
        }

        // Set the groupView
        groupView = findViewById(R.id.groupView);


        // Find the take pic button and set its onClickListener() to temporarily set
        // Next button to INVISIBLE so user can't go next until file is saved. Then locks the
        // focus of camera device for processing the capture request
        groupTakeImageButton = findViewById(R.id.btn_takeGroup);
        groupTakeImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Post onto the queue
                groupBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // First set next button to invisible, not ready yet
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.to_selfie).setVisibility(View.INVISIBLE);
                            }
                        });

                        // Then Call lock focus to begin taking our picture!
                        lockFocus();
                    }
                });
            }
        }); // End of onClickListener initialization


    }

    /**
     * Starts next activity to take selfie pic.
     * Take Pic button is made invisible to prevent further capture requests.
     * Decodes image from file as a bitmap on background thread.
     * Rotates the image for correct orientation for bitmap of group pic.
     * Uses GSON to create a JSON of the bitmap to be passed to SelfieActivity
     * @param view reference to views state
     */
    public void startSelfieActivity(View view) {
        // First set take pic button to invisible
        findViewById(R.id.btn_takeGroup).setVisibility(View.INVISIBLE);
        // Post to decode the file
        groupBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                // Decode the file and then try to open its exifinterface data
                bitmap = BitmapFactory.decodeFile(groupPhotoFileName);
            }
        });
        // Then post to rotate the image as necessary and start next activity
        groupBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ExifInterface exifInterface = new ExifInterface(groupPhotoFileName);
                    // See if the returned getAttribute string tag equals "6"
                    // Left Landscape
                    if (exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION).equals("6")) {
                        Matrix matrix = new Matrix();
                        // Add 90 to create portrait bitmap
                        // plan to rotate bitmap 90 degrees to portrait mode
                        matrix.postRotate(90);
                        // Create a new bitmap from the desired bitmap (member variable)
                        // With no offset in x and y and its original width/height
                        // But with a different matrix rotation
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                bitmap.getHeight(), matrix, true);
                    }
                    // Right Landscape
                    else if (exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION).equals("8")) {
                        Matrix matrix = new Matrix();
                        // Subtract 90 to create portrait bitmap
                        // plan to rotate bitmap 90 degrees to portrait mode
                        matrix.postRotate(-90);
                        // Create a new bitmap from the desired bitmap (member variable)
                        // With no offset in x and y and its original width/height
                        // But with a different matrix rotation
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    }
                    Log.d(TAG, "ExifInterface " + exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Run on UI, go to next activity
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Selfie intent starting");
                        //Declare new GSON object for serialization/deserialization
                        Gson gson = new Gson();
                        //Convert bitmap object to JSON string using GSON
                        String bitmapJson = gson.toJson(bitmap);
                        //Declare new intent to next activity
                        Intent selfieIntent = new Intent(getApplicationContext(), SelfieAcitivity.class);
                        //Add JSON string to intent
                        selfieIntent.putExtra("Bitmap", bitmapJson);
                        //Add filename for group photo
                        selfieIntent.putExtra("GroupFileName", groupPhotoFileName);
                        //Start next activity
                        startActivity(selfieIntent);
                    }
                });
            }
        });
    }

    /**
     * When app is resumed, start background thread again, setup cameras again, connect to the
     * camera. If the view is not available, set the view
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Start thread
        startBackgroundThread();

        // See if view is available
        if(groupView.isAvailable()) {
            // Set up and connect
            setUpCamera(groupView.getWidth(), groupView.getHeight());
            connectCamera();
        }
        // Else view not available, set it
        else {
            // Call set on groupView
            groupView.setSurfaceTextureListener(groupTextListener);
        }
    }

    /**
     * Checks to see if read and write file saving permissions have been granted,
     * if not, prompts user to grant it
     */
    private boolean checkPermissions(String[] permissions) {
        int result = 0; // To be used in IF statement
        // List for our permission strings
        List<String> listPermissionsNeeded = new ArrayList<>();

        // Loop through permissions array
        for (String p : permissions) {
            // Set result code to the self perimissions check on the applications context and the
            // certain p permission
            result = ContextCompat.checkSelfPermission(this, p);
            // Check if granted
            if (result != PackageManager.PERMISSION_GRANTED) {
                // Add to permissions needed list
                listPermissionsNeeded.add(p);
            }
        }
        // If the list is empty, we got at least one! Call permission request function
        if (!listPermissionsNeeded.isEmpty()) {
            // Request permissions, which will call overridden onRequestPermissionsResult function
            // Give it the key int: 999
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                    999);
            return false; // Had to check
        }
        return true; // All done
    }

    /**
     * Prompt user to allow camera permissions if he/she has previously declined to do so
     * @param requestCode What request are we using
     * @param permissions Have permissions been granted
     * @param grantResults What permissions have been granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Call parent
        // IF permission code is the camera permission code
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            // If the first result given which will be the camera permission has not been granted
            // Make a toast notifying user
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Application won't run without camera services",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        // Else see if the request code is 999 for file saving permissions
        if (requestCode == 999) {
            // check to see if we have more than zero
            // If permissions were not granted
            // Make sad toast notifying them of sad disapproval
            if (grantResults.length > 0 &&
                    (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Sorry, we need to save files",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * When user navigates away, close the camera and stop the background thread
     */
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * When activity is safely killed
     */
    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * When activity is killed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Creates a Camera Manager Object and iterates through its CameraIdList to determine which
     * camera ID is needed to connect the camera
     * @param width Uses the the width of the texture view to determine image size width
     * @param height Uses the the height of the texture view to determine image size height
     */
    private void setUpCamera(int width, int height) {
        //create a camera manager object and retrieve its service context
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //iterate through the camera manager object's camera ID list
            for(String cameraId : cameraManager.getCameraIdList()){
                //create an object to store each camera ID's camera characteristics
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                //Skip the first (front facing) camera
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                //Set the image size to be the width and height of the texture view
                imageSize = new Size(groupView.getWidth(), groupView.getHeight());
                // image reader with group view's width, height, and maxImages is 2 because
                // "discarding all-but-the-newest Image requires temporarily acquiring two
                // Image at once." (https://developer.android.com/reference/android/
                //                              media/ImageReader#acquireLatestImage())
                imageReader = ImageReader.newInstance(groupView.getWidth(), groupView.getHeight(),
                        ImageFormat.JPEG, 2);
                //Set image reader's available listener
                imageReader.setOnImageAvailableListener(groupOnImageAvailableListener,
                        groupBackgroundHandler);
                //set the Camera Device ID to the selected camera
                groupCameraDeviceId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to iterate through camera ID list from cameraManager");
            e.printStackTrace();
        }
    }

    /**
     * Uses a camera ID, a camera device, and a background handler to connect and open the camera
     */
    private void connectCamera() {
        //create a camera manager object and retrieve its service context
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //check if android sdk version supports Camera 2 API
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            //check if user granted permission to access camera
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                try {
                    //open the camera
                    cameraManager.openCamera(groupCameraDeviceId, groupCameraDeviceStateCallback, groupBackgroundHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Camera failed to open");
                    e.printStackTrace();
                }
            } else {
                //if permission not yet granted, ask user for permission
                if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                    Toast.makeText(this, "App requires access to camera", Toast.LENGTH_LONG).show();
                }
                requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
            }
        } else {
            try {
                //if not within SDK range, try opening anyway
                cameraManager.openCamera(groupCameraDeviceId, groupCameraDeviceStateCallback, groupBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Camera failed to open");
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * Sets up the surfaceTexture, sets its buffer size, creates a previewSurface
     * to add as target for our groupCaptureRequestBuilder. The builder comes from
     * a createCaptureRequest() on the groupCameraDevice using the TEMPLATE_STILL_CAPTURE
     * for a single picture.
     * Then, we createCaptureSession() from the previewSurface, imageReader surface, and
     * create a new CameraCaptureSession.StateCallBack() anonymous object.
     * When onConfigured() is called on the CaptureSession, the previewCaptureSession member
     * variable will be set. Then we will continue to setRepeatingRequests for image capturing on
     * builder and background handler.
     * @throws CameraAccessException
     */
    private void startPic() throws CameraAccessException {
        // Set textures
        SurfaceTexture surfaceTexture = groupView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(groupView.getWidth(), groupView.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        // Set CaptureRequestBuilder from camera createCaptureRequest() method and
        // add the builder to target the preview surface
        // Create the capture session
        groupCaptureRequestBuilder = groupCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        groupCaptureRequestBuilder.addTarget(previewSurface);
        groupCameraDevice.createCaptureSession(Arrays.asList(previewSurface,
                imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {


                    /**
                     * Create a capture session live streaming the CaptureRequestBuilder
                     * @param session
                     */
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        // Set up member
                        previewCaptureSession = session;
                        try {
                            previewCaptureSession.setRepeatingRequest(
                                    groupCaptureRequestBuilder.build(), null,
                                    groupBackgroundHandler); // Used to be null!, 3rd parameter
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error in accessing cameras");
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {

                    }

                    @Override
                    public void onClosed(CameraCaptureSession session) {
                        Log.i(TAG, "Close Capture Session and Image Reader");
                        if (imageReader != null) {
                            imageReader.close();
                        }
                    }
                }, null);
    }

    /**
     * Locks the focus on the live streaming camera device for setup to take the actual picture.
     * Picture will be taken immediately after.
     */
    private void lockFocus() {
        // Set our CaptureRequestBuilder to lock the focus
        groupCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        // Lock the capture state
        // STATE_WAIT_LOCK is final int = 1
        // Capture State holds the current state of the camera preview (whether its locked or not)
        captureState = STATE_WAIT_LOCK;
        // Try to capture the image
        try {
            // Put it on the background thread
            previewCaptureSession.capture(groupCaptureRequestBuilder.build(), previewCaptureCallback,
                    groupBackgroundHandler);
        }
        // Catch any accessing exceptions
        catch (CameraAccessException camAccessExcept) {
            Log.e(TAG, "Error Accessing Camera for capture()");
            camAccessExcept.printStackTrace(); // Print stack
        }
    }

    /**
     * Closes the groupCameraDevice
     */
    private void closeCamera() {
        // Check if not null pointer
        if(groupCameraDevice != null) {
            // Close camera
            groupCameraDevice.close(); // end camera process
            groupCameraDevice = null; // set it to null
        }
    }

    /**
     * initializes declared background handler thread and sets a name for it
     * starts the thread and initializes the handler using the same thread
     */
    private void startBackgroundThread() {
        //Initialize Background Handler Thread
        groupBackgroundHandlerThread = new HandlerThread("GroupCameraThread");
        //Start Thread
        groupBackgroundHandlerThread.start();
        //Initialize Background Handler using the Background Handler Thread in its Constructor
        groupBackgroundHandler = new Handler(groupBackgroundHandlerThread.getLooper());
    }

    /**
     * Safely quits and joins any started threads and sets variables back to null
     */
    private void stopBackgroundThread() {
        //Avoid errors on stopping thread by quitting safely
        groupBackgroundHandlerThread.quitSafely();
        try {
            //Join threads
            groupBackgroundHandlerThread.join();

            //Set Background handler and Handler thread to null
            groupBackgroundHandlerThread = null;
            groupBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Group Background Handler Thread failed to join after quitting safely");
            e.printStackTrace();
        }
    }

    /**
     * Creates a still capture session for a single picture to be taken in portrait mode (90֯ ).
     * Create a stillCaptureCallback with the needed call to createPhotoFileName() when capture
     * has started.
     */
    private void startStillCapture() {
        // Try to create a CaptureCall back
        try {
            // Use the still capture template for our capture request builder
            // Add target to be the imageReader's surface
            // Set the orientation to be portrait
            groupCaptureRequestBuilder =
                    groupCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            groupCaptureRequestBuilder.addTarget(imageReader.getSurface());
            Log.v(TAG, "Still capture at orientation: " + ORIENTATIONS.valueAt(1));
            groupCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    ORIENTATIONS.valueAt(1));

            // Create stillCaptureCallback
            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        // When capture has started, call createPhotoFileName()
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                     @NonNull CaptureRequest request,
                                                     long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                        }
                    };
            // Call capture! Give it the builder and the stillCaptureCallback
            previewCaptureSession.capture(groupCaptureRequestBuilder.build(), stillCaptureCallback,
                    null); // Already on the background thread, give thread null
        }
        catch (CameraAccessException camAccessExcept) {
            Log.e(TAG, "Error accessing camera");
            camAccessExcept.printStackTrace();
        }
    }

    /**
     * Contains photoFolder creation method and file name of the photo taken
     * @return groupPhotoFileName will be returned
     */
    private void createPhotoFolder() {
        //Creates toast notifying photo folder creation
        //gets external storage from public directory path (DIRECTORY_PICTURES)
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Create folder from the abstract pathname created above (imageFile)
        groupPhotoFolder = new File(imageFile, "CameraImages");

        //if photo folder doesn't exist
        if(!groupPhotoFolder.exists()) {
            // Create sub-directory
            groupPhotoFolder.mkdirs();
        }
    }

    /**
     * Method creates name of photo file, adds additional date format and timestamp.
     * if photo folder does not exist, notifies of its non existence, also creates a temp
     * file that is prepended with ".jpg" and gets the path from the photo file.
     * @throws IOException if working with file fails
     */
    private String createPhotoFileName()throws IOException {
        //adds a date format for the timestamp of the photo taken
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "PHOTO_" + timeStamp + "_";
        try {
            //if photo folder does not exist...
            if (!groupPhotoFolder.exists()) {
                throw new NullPointerException("Photo Folder does not exist");
            }
        }
        catch (NullPointerException folderError) {
            //will notify of folders non-existence
            Log.e(TAG, "Folder non-existent");
            folderError.printStackTrace();
        }

        //creates temporary photo file with ".jpg" suffix which is then prepended with
        //existing photo folder.
        // Don't create to temporary files if groupPhotoFileName already exists
        if (groupPhotoFileName == null) {
            Log.i(TAG, "File Name doesn't exist. Create it.");
            File photoFile = File.createTempFile(prepend, ".jpg", groupPhotoFolder);
            groupPhotoFileName = photoFile.getAbsolutePath();
            Log.i(TAG, groupPhotoFileName);
        }
        //return photo filename
        return groupPhotoFileName;
    }
}
