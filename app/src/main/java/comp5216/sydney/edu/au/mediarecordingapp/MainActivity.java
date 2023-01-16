package comp5216.sydney.edu.au.mediarecordingapp;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


// Acknowledgement: The following code was heavily inspired by week 6 Android lab tutorial material
public class MainActivity extends AppCompatActivity {

    public static final String URL = "URL";
    public static final String CITY = "city";
    public static final String LOCATION_OBJECT = "locationObject";
    MarshmallowPermission marshmallowPermission = new MarshmallowPermission(this);

    private Uri photoUri;
    private Boolean photoMode; // True is photo, false is video
    private FusedLocationProviderClient fusedLocationClient;
    private Boolean hasNotSignedIn = true;
    private Boolean isWifiConnected = false;
    private Boolean isMobileNetworkConnected = false;
    private ArrayList<Item> queue = new ArrayList<>();
    private NetworkReceiver receiver;

    private TextView backupText;
    private TextView wifiHintText;
    private Button backupButton;

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    FirebaseStorage storage = FirebaseStorage.getInstance();

    ActivityResultLauncher<Intent> mLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (photoMode) {
                        checkLocationAndUpload(photoUri, photoMode);
                    } else {
                        checkLocationAndUpload(result.getData().getData(), photoMode);
                    }
                }
            });


    public void checkLocationAndUpload(Uri uploadItemUri, Boolean isPhoto) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            if (isMobileNetworkConnected || isWifiConnected) {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location loc) {
                                // NEED to set NEXUS emulator to set location, then go google maps and click on location on map. Then it won't be null.
                                if (loc != null) {
                                    // Logic to handle location object
                                    Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                                    try {
                                        List<Address> a = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                                        String cityName;
                                        if(a.get(0).getLocality() != null){
                                            cityName = a.get(0).getLocality().toLowerCase();
                                        }
                                        else{
                                            cityName = a.get(0).getSubAdminArea();
                                        }

                                        StorageReference ref;

                                        if (isPhoto) {
                                            ref = storage.getReference().child("images/" + uploadItemUri.getLastPathSegment());
                                        } else {
                                            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                                            ref = storage.getReference().child("videos/" + "VIDEO_" + timeStamp + ".mp4");
                                        }

                                        if (isWifiConnected) {
                                            uploadToServer(cityName, uploadItemUri, ref, loc);
                                        } else {
                                            wifiHintText.setVisibility(View.VISIBLE);
                                            queue.add(new Item(cityName, loc, uploadItemUri, ref));
                                        }


                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "Go to google maps, locate yourself", Toast.LENGTH_LONG).show();
                                }
                            }
                        });

            } else {
                Toast.makeText(MainActivity.this, "Turn on network(mobile/wifi) & retry!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "Location permission needed.\n Go to App settings", Toast.LENGTH_LONG).show();
        }
    }


    // Uploads to cloud storage first, then firestore
    public void uploadToServer(String cityName, Uri uploadItemUri, StorageReference ref, Location loc) {

        Map<String, Object> fireStoreData = new HashMap<>();
        UploadTask uploadTask = ref.putFile(uploadItemUri);

        // Upload to the server!
        uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                // Continue with the task to get the download URL
                return ref.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();

                    fireStoreData.put(CITY, cityName);
                    fireStoreData.put(LOCATION_OBJECT, loc);
                    fireStoreData.put(URL, downloadUri.toString());


                    db.collection(cityName).get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot qS) {
                            if (qS.isEmpty()) {
                                db.collection(cityName).document().set(fireStoreData).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        Toast.makeText(MainActivity.this, "Uploaded to firestore and storage!", Toast.LENGTH_LONG).show();
                                    }
                                });
                            } else {

                                db.collection(cityName).add(fireStoreData).addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentReference> task) {
                                        Toast.makeText(MainActivity.this, "Uploaded to firestore and storage!", Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                        }
                    });

                }
            }
        });
    }


    // See: https://developer.android.com/training/basics/intents/result
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            new ActivityResultCallback<FirebaseAuthUIAuthenticationResult>() {
                @Override
                public void onActivityResult(FirebaseAuthUIAuthenticationResult result) {
                }
            }
    );


    private void startSignIn() {
        // Sign in with FirebaseUI
        Intent intent = AuthUI.getInstance().createSignInIntentBuilder()
                .setAvailableProviders(Collections.singletonList(
                        new AuthUI.IdpConfig.EmailBuilder().build()))
                .setIsSmartLockEnabled(false)
                .build();

        signInLauncher.launch(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        backupText = (TextView) findViewById(R.id.backupTextView);
        backupButton = (Button) findViewById(R.id.backupButton);
        wifiHintText = (TextView) findViewById(R.id.askForWifiTextView);

        backupText.setVisibility(View.INVISIBLE);
        backupButton.setVisibility(View.INVISIBLE);
        wifiHintText.setVisibility(View.INVISIBLE);

        // Ask for permission for Camera and Location
        if (!marshmallowPermission.checkPermissionForCamera() || !marshmallowPermission.checkPermissionForExternalStorage()
                || !marshmallowPermission.checkPermissionForRecord() || !marshmallowPermission.checkPermissionForLocation()) {
            marshmallowPermission.requestPermissionForCamera();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver(MainActivity.this);
        this.registerReceiver(receiver, filter);

    }

    @Override
    public void onStart() {
        super.onStart();
        if (hasNotSignedIn) {
            startSignIn();
            hasNotSignedIn = false;
        }

    }

    public void askForBackup() {
        if (isWifiConnected && queue.size() > 0) {
            // Make the text view visible and button
            backupText.setText(getString(R.string.items_to_backup) + "   " + Integer.toString(queue.size()));
            wifiHintText.setVisibility(View.INVISIBLE);
            backupText.setVisibility(View.VISIBLE);
            backupButton.setVisibility(View.VISIBLE);
        }
    }


    public void onClickTakePhoto(View view) {
        if (marshmallowPermission.checkPermissionForCamera() && marshmallowPermission.checkPermissionForExternalStorage()) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // Create file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String photoFileName = "IMG_" + timeStamp + ".jpg";

            photoUri = getFileUri(photoFileName);
            // Tell the camera that when it takes the photo, store it at this URI (already contains file provider)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            photoMode = true;

            if (intent.resolveActivity(getPackageManager()) != null) {
                // Start the image capture intent to take photo
                mLauncher.launch(intent);
            }
        }
    }


    public void onClickTakeVideo(View view) {
        if (marshmallowPermission.checkPermissionForCamera() && marshmallowPermission.checkPermissionForExternalStorage()) {
            // create Intent to capture a video and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            photoMode = false;
            // Start the video record intent to capture video
            mLauncher.launch(intent);
        }
    }


    public void setWifiConnected(Boolean state) {
        isWifiConnected = state;
    }


    public void setMobileNetworkConnected(Boolean state) {
        isMobileNetworkConnected = state;
    }

    private Uri getFileUri(String fileName) {
        Uri fileUri = null;
        try {
            File mediaStorageDir = new File(getExternalFilesDir(Environment.getExternalStorageDirectory().toString()), "Pictures/");

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                Log.d("APP_TAG", "failed to create directory");
            }

            // Create the file target for the media based on filename
            File file = new File(mediaStorageDir, fileName);

            // Wrap File object into a content provider, required for API >= 24
            // See https://guides.codepath.com/android/Sharing-Content-with-Intents#sharing-files-with-api-24-or-higher
            if (Build.VERSION.SDK_INT >= 24) {
                // Camera needs to put it into a file provider (shared bucket)
                fileUri = FileProvider.getUriForFile(
                        this.getApplicationContext(),
                        "comp5216.sydney.edu.au.mediarecordingapp.fileProvider", file);
            } else {
                fileUri = Uri.fromFile(mediaStorageDir);
            }
        } catch (Exception ex) {
            Log.e("getFileUri", ex.getStackTrace().toString());
        }
        return fileUri;
    }


    public void uploadQueue(View view) {
        while (!queue.isEmpty()) {
            Item i = queue.get(0);
            uploadToServer(i.getCity(), i.getUploadItemUri(), i.getRef(), i.getLocation());
            queue.remove(0);
        }
        backupText.setText(getString(R.string.items_to_backup) + "   " + Integer.toString(queue.size()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            this.unregisterReceiver(receiver);
        }
    }
}