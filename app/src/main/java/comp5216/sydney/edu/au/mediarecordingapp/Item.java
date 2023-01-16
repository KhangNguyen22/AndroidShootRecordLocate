package comp5216.sydney.edu.au.mediarecordingapp;

import android.location.Location;
import android.net.Uri;

import com.google.firebase.storage.StorageReference;

public class Item {
    String city;
    Location location;
    Uri uploadItemUri;
    StorageReference ref;

    public Item(String city, Location location, Uri uploadItemUri, StorageReference ref) {
        this.city = city;
        this.location = location;
        this.uploadItemUri = uploadItemUri;
        this.ref = ref;
    }

    public String getCity() {
        return city;
    }

    public Location getLocation() {
        return location;
    }

    public Uri getUploadItemUri() {
        return uploadItemUri;
    }

    public StorageReference getRef() {
        return ref;
    }
}
