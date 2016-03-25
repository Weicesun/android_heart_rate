package edu.gatech.ece6258;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Time;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import edu.gatech.ece6258.util.FastIcaRgb;
import edu.gatech.ece6258.util.Fft;
import edu.gatech.ece6258.util.StorageUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

public class RearCamera extends AppCompatActivity {

    private FrameLayout rearCameraPreview;
    private Camera mCamera;
    private static int defaultCameraId = 0;
    private TextView showResult;
    private ImageView mImageViewRectangle0;
    private SharedPreferences settings;
    private static final String PREFS_NAME = "PrefsFile";
    private RelativeLayout mRelativeLayoutRoot;

    /* Heart Rate Related Variables */
    private int heartRateFrameLength = 100;
    private double[] arrayRed = new double[heartRateFrameLength];
    private double[] arrayGreen = new double[heartRateFrameLength];
    private double[] arrayBlue = new double[heartRateFrameLength];
    private double[] component1 = new double[heartRateFrameLength];
    private double[] component2 = new double[heartRateFrameLength];
    private double[] component3 = new double[heartRateFrameLength];
    private double heartRate = 0;
    private int frameNumber = 1;
    private int left = 0, top = 0, right = 0, bottom = 0, smallPreviewWidth = 0, smallPreviewHeight = 0, numberOfPixelsToAnalyze = 0;

    private int previewWidth = 0, previewHeight = 0; // Defined in surfaceChanged()
    /* Frame Frequency */
    private long samplingFrequency;

    /* Face Detection Variables */
    private int numberOfFacesCurrentlyDetected = 0;
    //used to use to loacte the position of the face
    private int faceLeft0 = 0;
    private int faceTop0 = 0;
    private int faceRight0 = 0;
    private int faceBottom0 = 0;
    private int previewLeft = 0;
    private int previewTop = 0;
    private int previewRight = 0;
    private int previewBottom = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rear_camera);
        settings = getSharedPreferences(PREFS_NAME, 0);
        rearCameraPreview = (FrameLayout) findViewById(R.id.rearcamerapreview);
        showResult = (TextView) findViewById(R.id.showresult2);
        mImageViewRectangle0   = (ImageView) findViewById(R.id.imageViewRectangle0);
        mRelativeLayoutRoot = (RelativeLayout) findViewById(R.id.relativelayout);
        mCamera = getCameraInstance();
        rearCameraPreview.addView(new CameraPreview(this,mCamera));//create and camera preview to screen
    }


    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        Camera.Size mPreviewSize; //set the size of the picture
        List<Camera.Size> mSupportedPreviewSizes;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            SurfaceHolder mHolder;
            mCamera = camera;

            mHolder = getHolder(); // inorder to handle the pixels in the surface
            mHolder.addCallback(this);
            //mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        }

        public void surfaceCreated(SurfaceHolder holder) { // The Surface has been created, now tell the camera where to draw the preview
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
            catch (Exception e) { } // Camera is not available (in use or does not exist)
        } // END surfaceCreated()
        public void surfaceDestroyed(SurfaceHolder holder) { // Called right before surface is destroyed
            // Because the CameraDevice object is not a shared resource, it's very important to release it when the activity is paused.
            if (mCamera != null) {
                mCamera.setPreviewCallback(null); // This is for manually added buffers/threads // Use setPreviewCallback() for automatic buffers
                mCamera.stopPreview();
                mCamera.release(); // release the camera for other applications
                mCamera = null;
            }

        }
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {//every time the image changed the surfacechanged call once
            if(holder.getSurface() == null) { return; } // preview surface does not exist // WAS mHolder
            mCamera.stopPreview(); // stop preview before making changes
            previewWidth  = w;
            previewHeight = h;

            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, w, h);
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            mCamera.setParameters(parameters);
            requestLayout();//initiate layout again

            previewWidth = mPreviewSize.width;
            previewHeight = mPreviewSize.height;
            previewLeft   = (int) (previewWidth  * .30);
            previewTop    = (int) (previewHeight * .30);
            previewRight  = (int) (previewWidth  * .70);
            previewBottom = (int) (previewHeight * .70);
            int rectangleWidth = previewRight - previewLeft;
            int rectangleHeight = previewBottom - previewTop;



            try {
                setCameraDisplayOrientation(RearCamera.this, defaultCameraId, mCamera);
                mCamera.setFaceDetectionListener(new MyFaceDetectionListener());
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
                startFaceDetection(); // start face detection feature
                ImageView mImageView = new ImageView(RearCamera.this);
                mRelativeLayoutRoot.addView(mImageView, new RelativeLayout.LayoutParams(rectangleWidth, rectangleHeight));



            } catch (Exception e) { } // Error starting camera preview




            mCamera.setPreviewCallback(new Camera.PreviewCallback() { // Gets called for every frame
                public void onPreviewFrame(byte[] data, Camera c) { // NOTE: not ran if buffer isn't big enough for data
                    if(numberOfFacesCurrentlyDetected == 0) {
                        frameNumber = 0;
                    } else {
                        // when the face showup need to get start
                        if (frameNumber == 0) {
                            left = previewLeft; top = previewTop; right = previewRight; bottom = previewBottom;
                            smallPreviewWidth = right - left+1; // because coordinate system is different and backwards
                            smallPreviewHeight = bottom - top+1;
                            numberOfPixelsToAnalyze = smallPreviewWidth * smallPreviewHeight;
                        }


                       //start to anaylese the content of the frame
                        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                        Rect rect = new Rect(left, top, right, bottom);
                        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21,previewWidth,previewHeight,null); // Create YUV image from byte[]
                        yuvimage.compressToJpeg(rect, 100, outstr);                                              // Convert YUV image to Jpeg // NOTE: changes Rect's size
                        Bitmap bmp = BitmapFactory.decodeByteArray(outstr.toByteArray(), 0, outstr.size());      // Convert Jpeg to Bitmap

                        smallPreviewWidth = bmp.getWidth();
                        smallPreviewHeight = bmp.getHeight();


                        int r = 0, g = 0, b = 0;
                        int[] pix = new int[numberOfPixelsToAnalyze];
                        bmp.getPixels(pix, 0, smallPreviewWidth, 0, 0, smallPreviewWidth, smallPreviewHeight);//Returns in pixels[] a copy of the data in the bitmap

                        for(int i = 0; i < smallPreviewHeight; i++) {
                            for(int j = 0; j < smallPreviewWidth; j++) {
                                int index = i * smallPreviewWidth + j;
                                r += (pix[index] >> 16) & 0xff; //get the red component truncate other part
                                g += (pix[index] >> 8) & 0xff;
                                b += pix[index] & 0xff;
                            }
                        }

                        r /= numberOfPixelsToAnalyze;
                        g /= numberOfPixelsToAnalyze;
                        b /= numberOfPixelsToAnalyze;
                        // in this case everyframe has its own mean rgb
                        if(frameNumber < heartRateFrameLength) {
                            if(frameNumber == 0) {
                                samplingFrequency = System.nanoTime(); // Start time set timestamp in nano second
                            }



                            arrayRed[frameNumber] = ((double) r);
                            arrayGreen[frameNumber] = ((double) g);
                            arrayBlue[frameNumber] = ((double) b);

                            showResult.setText("Heart Rate: in " + (frameNumber/100)+"%" + "..");
                            frameNumber++;
                        }
                        else if(frameNumber == heartRateFrameLength) {
                            samplingFrequency = System.nanoTime() - samplingFrequency; // length of heartRateFrameLength frames
                            double finalSamplingFrequency = samplingFrequency / (double)1000000000; // Length of time to get frames in seconds
                            finalSamplingFrequency = heartRateFrameLength / finalSamplingFrequency; // Frames per second in seconds


                            FastIcaRgb.preICA(arrayRed, arrayGreen, arrayBlue, heartRateFrameLength, component1, component2, component3); // heartRateFrameLength = 300 frames for now
                            double heartRateFrequency = Fft.FFT(component2, heartRateFrameLength, finalSamplingFrequency);//using array red or green?
                            if (heartRateFrequency == 0) {
                                showResult.setText("Heart Rate: Error, try again");
                            } else {
                                heartRate = Math.round(heartRateFrequency * 60);

                                showResult.setText("Heart Rate: " + heartRate);

                                saveSharedPreference("heartRate",(int)heartRate);
                                frameNumber++; // make sure that the camera make only one time sampling on face

                                promptUserToSaveData(); // Ask user if they would like to save this data
                            }
                        }
                        else {

                        }
                    }
                }
            });
        }


    }


    public static Camera getCameraInstance() {
        int numberOfCameras = Camera.getNumberOfCameras();

        // Find the ID of the default camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { // CameraInfo.CAMERA_FACING_FRONT to use front camera
                defaultCameraId = i;
            }
        }

        Camera c = null;
        try {
            c = Camera.open(defaultCameraId); // attempt to get a Camera instance
        }
        catch (Exception e) { } // Camera is not available (in use or does not exist)
        return c; // returns null if camera is unavailable
    }
    public static Camera getCameraInstance(int wantedCameraId) {

        Camera c = null;
        try {
            c = Camera.open(wantedCameraId); // attempt to get a Camera instance
        }
        catch (Exception e) { } // Camera is not available (in use or does not exist)
        return c; // returns null if camera is unavailable
    }

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    class MyFaceDetectionListener implements Camera.FaceDetectionListener {
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            numberOfFacesCurrentlyDetected = faces.length;
            if (numberOfFacesCurrentlyDetected > 0) {
                faceLeft0   = faces[0].rect.left;
                faceTop0    = faces[0].rect.top;
                faceRight0  = faces[0].rect.right;
                faceBottom0 = faces[0].rect.bottom;
              //  mTextViewFace0Coordinates.setText("Face Rectangle: (" + faceLeft0 + "," + faceTop0 + "), (" + faceRight0 + "," + faceBottom0 + ")");

                mImageViewRectangle0.bringToFront();
                mImageViewRectangle0.setPadding(previewLeft, previewTop, previewWidth-previewRight, previewHeight-previewBottom);
                mImageViewRectangle0.postInvalidate();

            }
        }
    }
    public void startFaceDetection() {
        // Try starting Face Detection
        Camera.Parameters params = mCamera.getParameters();

        // start face detection only *after* preview has started
        if (params.getMaxNumDetectedFaces() > 0) {
            // camera supports face detection, so can start it:
            mCamera.startFaceDetection();
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.2;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private void promptUserToSaveData() {
        new AlertDialog.Builder(this)
                .setTitle("Save Data")
                .setMessage("Would you like to save the health data?")
                        //.setView(input)
                .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //Editable value = input.getText();
                        if (StorageUtils.isExternalStorageAvailableAndWritable()) {
                            Time timeNow = new Time(Time.getCurrentTimezone());
                            timeNow.setToNow();
                            writeToTextFile("Heart Rate: " + heartRate + "," + timeNow.format2445() + "\n");
                        } else {
                            Toast.makeText(RearCamera.this, "SD card storage not available", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        }).show();
    }


    private void saveSharedPreference(String key, int value) {
        SharedPreferences.Editor editor = settings.edit(); // Needed to make changes
        editor.putInt(key, value);
        editor.commit(); // This line saves the edits
    }



    private void writeToTextFile(String data) {
        File sdCard = Environment.getExternalStorageDirectory();
        File directory = new File (sdCard.getAbsolutePath() + "/ece6258testresult");
        directory.mkdirs();
        File file = new File(directory, "data.csv");
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file, true); // NOTE: This (", true") is the key to not overwriting everything
            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            osw.write(data);
            osw.flush();
            osw.close();
            Toast.makeText(this, "Data successfully save in " + file.toString(), Toast.LENGTH_LONG).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

