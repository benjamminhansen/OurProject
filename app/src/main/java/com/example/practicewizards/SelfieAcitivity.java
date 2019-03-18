package com.example.practicewizards;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class SelfieAcitivity extends AppCompatActivity {
    private static final String TAG = "SelfieAcitivty.java";
    private static int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private TextureView selfieView;
    private TextureView.SurfaceTextureListener selfieTextListener = new TextureView.SurfaceTextureListener() {
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

    //CAMERA DEVICE FOR SELFIE VIEW
    private CameraDevice selfieCameraDevice;
    private CameraDevice.StateCallback selfieCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            selfieCameraDevice = camera;
            try {
                startPic();
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error: unable to access camera");
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            selfieCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            selfieCameraDevice = null;
        }
    };

    //FUNCTIONS MEMBER VARIABLES
    // State members
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    // Hold the state
    private int captureState = STATE_PREVIEW;

    // Boolean representing whether picture has been taken or not
    boolean picTaken = false;
    // Bitmap of image
    private Bitmap bitmap;
    // Button to take selfie photo
    private Button selfieTakeImageButton;
    // Folder for Selfies
    private File selfiePhotoFolder;
    // File name for selfie picture
    private String selfiePhotoFileName;

    private HandlerThread selfieBackgroundHandlerThread;
    private Handler selfieBackgroundHandler;
    private String selfieCameraDeviceId; // for setup of the camera
    private CaptureRequest.Builder selfieCaptureRequestBuilder;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private final ImageReader.OnImageAvailableListener selfieOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Call our runnable to save photo to storage
                    // Post to the handler the latest image reader
                    selfieBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };

    // Nested runnable class
    private class ImageSaver implements Runnable {
        private final Image image;

        public ImageSaver(Image image) {
            this.image = image;
        }
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
                if (!selfiePhotoFolder.exists())
                    Log.e(TAG, "Called create photo folder, it still doesn't exist" +
                            selfiePhotoFolder.mkdirs());
                fileOutputStream = new FileOutputStream(createPhotoFileName()); // open file
                Toast.makeText(getApplicationContext(), "File Output Stream Created",
                        Toast.LENGTH_SHORT).show();
                fileOutputStream.write(bytes); // Write the bytes to the file
                Log.d(TAG, "File Name: " + selfiePhotoFileName);

                // Set picTaken to true, picture and file saving have been successful
                picTaken = true;
                // Save the image to outer class
                bitmap = BitmapFactory.decodeFile(selfiePhotoFileName);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException nullPtr) {
                Log.e(TAG, "Null something");
                nullPtr.printStackTrace();
            }
            finally {
                // Close image
                Log.i(TAG, "Close the output stream");
                image.close();
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
                                Toast.makeText(getApplicationContext(), "AF LOCKED!",
                                        Toast.LENGTH_SHORT).show();
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

                    // Stop streaming the camera. Hold the state
                    try {
                        session.stopRepeating(); // Stop repeating requests
                        closeCamera(); // Close camera
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error in closing camera");
                        e.printStackTrace();
                    }
                }
            };

    // Image size
    private Size imageSize;
    // Image reader
    private ImageReader imageReader;

    /**
     * Creates views, also Logs the file location from public path.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie);
        getWindow().getDecorView().setBackgroundColor(Color.argb(255, 0, 100, 100));

        Intent selfieIntent = getIntent();
        // Set view
        selfieView = (TextureView) findViewById(R.id.selfieView);

        // Get our photo folder ready
        createPhotoFolder();

        // Log
        Log.i(TAG, "Files Location" + selfiePhotoFolder.getAbsolutePath());


        selfieTakeImageButton = findViewById(R.id.btn_takeSelfie);
        selfieTakeImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If the picture has not been taken, take it!
                if (!picTaken) {
                    // Call lock focus to begin taking our picture!
                    lockFocus();
                    selfieTakeImageButton.setText(R.string.retake);
                }
                // else when clicked after picture has been taken
                // Reset the view and set up cameras again and change the text
                // Set pic taken back to false
                else {
                    // Delete last saved image
                    if (picTaken && selfiePhotoFileName != null) {
                        Log.i(TAG, "Delete old photo");
                        File fDelete = new File(selfiePhotoFileName);
                        // Make sure it exists
                        if (fDelete.exists()) {
                            // Delete the file and set the string filename to null
                            // No more pic taken
                            fDelete.delete();
                            selfiePhotoFileName = null;
                            picTaken = false;
                        }
                    }
                    // Pause momentarily and then resume again.
                    onPause();
                    onResume();
                    // Reset text
                    selfieTakeImageButton.setText(R.string.take_selfie);
                    // Reset bitmap, help Garbage Collector free up the buffer faster
                    bitmap = null;
                }
            }
        }); // End of onClickListener initialization
    }

    /**
     * Starts next activity to merge the two pictures
     * @param view reference to views state
     */
    public void startMergeActivity(View view) {
        // Don't start next activity if the user hasn't taken a picture
        // and saved the image
        // make sure we have a saved image. Double check also the bitmap
        if (picTaken && bitmap != null) {
            Log.i(TAG, "Selfie intent starting");
            Intent selfieIntent = new Intent(this, SelfieAcitivity.class);
            startActivity(selfieIntent);
        }
        // Else image is null, make toast
        else {
            Toast.makeText(getApplicationContext(), R.string.error_pic_not_taken,
                    Toast.LENGTH_SHORT).show();
        }
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
        if(selfieView.isAvailable()) {
            // Set up and connect
            setUpCamera(selfieView.getWidth(), selfieView.getHeight());
            connectCamera();
        }
        // Else view not available, set it
        else {
            // Call set on groupView
            selfieView.setSurfaceTextureListener(selfieTextListener);
        }
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // IF permission code is the camera permission code
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT){
            // If the first result given which will be the camera permission has not been granted
            // Make a toast notifying user
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Application won't run without camera services", Toast.LENGTH_LONG).show();
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
     * Creates a Camera Manager Object and iterates through its CameraIdList to determine which camera ID is needed to connect the camera
     * @param width
     * Uses the the width of the texture view to determine image size width
     * @param height
     * Uses the the height of the texture view to determine image size height
     */
    private void setUpCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for(String cameraId : cameraManager.getCameraIdList()) {
                //create an object to store each camera ID's camera characteristics
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                // See if the Lens facing of found camera is front facing. If it is, we want it!
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    //Set the image size to be the width and height of the texture view
                    imageSize = new Size(selfieView.getWidth(), selfieView.getHeight());
                    // image reader with selfie view's width, height, and maxImages is just 1
                    imageReader = ImageReader.newInstance(selfieView.getWidth(), selfieView.getHeight(),
                            ImageFormat.JPEG, 1);
                    //Set image reader's available listener
                    imageReader.setOnImageAvailableListener(selfieOnImageAvailableListener,
                            selfieBackgroundHandler);
                    //set the Camera Device ID to the selected camera
                    selfieCameraDeviceId = cameraId; //create a parameter for this
                    return;
                }
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
                    cameraManager.openCamera(selfieCameraDeviceId, selfieCameraDeviceStateCallback, selfieBackgroundHandler);
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
                cameraManager.openCamera(selfieCameraDeviceId, selfieCameraDeviceStateCallback, selfieBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Camera failed to open");
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets up the surfaceTexture, sets its buffer size, creates a previewSurface
     * to add as target for our selfieCaptureRequestBuilder. The builder comes from
     * a createCaptureRequest() on the selfieCameraDevice using the TEMPLATE_STILL_CAPTURE
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
        SurfaceTexture surfaceTexture = selfieView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(selfieView.getWidth(), selfieView.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        // Set CaptureRequestBuilder from camera createCaptureRequest() method and
        // add the builder to target the preview surface
        // Create the capture session
        try {
            selfieCaptureRequestBuilder = selfieCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            selfieCaptureRequestBuilder.addTarget(previewSurface);
            selfieCameraDevice.createCaptureSession(Arrays.asList(previewSurface,
                    imageReader.getSurface()), new CameraCaptureSession.StateCallback() {

                /**
                 * Create a capture session live streaming the CaptureRequestBuilder
                 * @param session
                 */
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    previewCaptureSession = session; // Set member
                    // Try to set repeating requests
                    try {
                        session.setRepeatingRequest(selfieCaptureRequestBuilder.build(),
                                null, selfieBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error in accessing cameras");
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(), "Unable to setup camera preview", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                    Log.i(TAG, "Close Capture Session and Image Reader");
                    if (imageReader != null) {
                        imageReader.close();
                    }
                }

            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Locks the focus on the live streaming camera device for setup to take the actual picture.
     * Picture will be taken immediately after.
     */
    private void lockFocus() {
        // Set our CaptureRequestBuilder to lock the focus
        selfieCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        // Lock the capture state
        // STATE_WAIT_LOCK is final int = 1
        // Capture State holds the current state of the camera preview (whether its locked or not)
        captureState = STATE_WAIT_LOCK;
        // Try to capture the image
        try {
            // Put it on the background thread
            previewCaptureSession.capture(selfieCaptureRequestBuilder.build(), previewCaptureCallback,
                    selfieBackgroundHandler);
        }
        // Catch any accessing exceptions
        catch (CameraAccessException camAccessExcept) {
            Log.e(TAG, "Error Accessing Camera for capture()");
            camAccessExcept.printStackTrace(); // Print stack
        }
    }

    /**
     * Closes the selfieCameraDevice
     */
    private void closeCamera() {
        if(selfieCameraDevice != null) {
            selfieCameraDevice.close();
            selfieCameraDevice = null;
        }
    }

    /**
     * initializes declared background handler thread and sets a name for it
     * starts the thread and initializes the handler using the same thread
     */
    private void startBackgroundThread() {
        selfieBackgroundHandlerThread = new HandlerThread("GroupCameraThread");
        selfieBackgroundHandlerThread.start();
        selfieBackgroundHandler = new Handler(selfieBackgroundHandlerThread.getLooper());
    }

    /**
     * Safely quits and joins any started threads and sets variables back to null
     */
    private void stopBackgroundThread() {
        //Avoid errors on stopping thread by quitting safely
        selfieBackgroundHandlerThread.quitSafely();
        try {
            //Join threads
            selfieBackgroundHandlerThread.join();
            //Set Background handler and Handler thread to null
            selfieBackgroundHandlerThread = null;
            selfieBackgroundHandler = null;
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
            selfieCaptureRequestBuilder =
                    selfieCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            selfieCaptureRequestBuilder.addTarget(imageReader.getSurface());
            selfieCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            // Create stillCaptureCallback
            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        // When capture has started, call createPhotoFileName()
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                     @NonNull CaptureRequest request,
                                                     long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            // Try to create a unique photo file
                            try {
                                createPhotoFileName();
                            } catch (IOException e) {
                                Log.e(TAG, "Error in calling createPhotoFileName()");
                                e.printStackTrace();
                            }
                        }
                    };
            // Call capture! Give it the builder and the stillCaptureCallback
            previewCaptureSession.capture(selfieCaptureRequestBuilder.build(), stillCaptureCallback,
                    null); // Already on the background thread, give thread null
        }
        catch (CameraAccessException camAccessExcept) {
            Log.e(TAG, "Error accessing camera");
            camAccessExcept.printStackTrace();
        }
    }

    /**
     * Contains photoFolder creation method and file name of the photo taken
     * @return selfiePhotoFileName will be returned
     */
    private void createPhotoFolder() {
        //Creates toast notifying photo folder creation
        Toast.makeText(getApplicationContext(), "Create Photo Folder called", Toast.LENGTH_SHORT)
                .show();
        //gets external storage from public directory path (DIRECTORY_PICTURES)
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        //if imageFile directory doesn't exist
        if (!imageFile.mkdirs())
            Log.e(TAG, "Directory not created");

        Toast.makeText(getApplicationContext(), "External file storage: " +
                imageFile.getName(), Toast.LENGTH_SHORT)
                .show();

        // Create folder from the abstract pathname created above (imageFile)
        selfiePhotoFolder = new File(imageFile, "CameraImages");
        Toast.makeText(getApplicationContext(), "Photo folder created: " +
                selfiePhotoFolder.getName(), Toast.LENGTH_SHORT)
                .show();

        //if photo folder doesn't exist
        if(!selfiePhotoFolder.exists()) {

            //toast notifying of directory creation
            Toast.makeText(getApplicationContext(), "Mkdir" + selfiePhotoFolder.mkdirs(),
                    Toast.LENGTH_SHORT).show();
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
            if (!selfiePhotoFolder.exists()) {
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
        // Don't create to temporary files if selfiePhotoFileName already exists
        if (selfiePhotoFileName == null) {
            Log.i(TAG, "File Name doesn't exist. Create it.");
            File photoFile = File.createTempFile(prepend, ".jpg", selfiePhotoFolder);
            selfiePhotoFileName = photoFile.getAbsolutePath();
            Log.i(TAG, selfiePhotoFileName);
        }
        //return photo filename
        return selfiePhotoFileName;
    }
}