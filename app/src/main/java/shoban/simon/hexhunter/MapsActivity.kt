package shoban.simon.hexhunter

import android.Manifest
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.QueryDocumentSnapshot
import android.support.v7.app.AlertDialog
import android.widget.Toast
import com.google.android.gms.maps.model.Marker


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var mapOfReceptacles: HashMap<String, Receptacle>
    private val db = FirebaseFirestore.getInstance()
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        latitude = intent.getDoubleExtra("Latitude", 0.0)
        longitude = intent.getDoubleExtra("Longitude", 0.0)

        mapOfReceptacles = HashMap()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        listenForFirestoreChanges()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnInfoWindowClickListener {
            createDeleteDialog(it)
        }

        val permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    10000)
        }

        // Move camera to current location
        val currentLocation = LatLng(latitude, longitude)
        val mapZoom: Float = 15.toFloat()
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, mapZoom))
    }

    /**
     * Display locations saved in Firebase on map.
     */
    private fun updateUI() {
        mapOfReceptacles.forEach{_, receptacle ->
            mMap.addMarker(
                MarkerOptions().position(
                    LatLng(receptacle.location!!.latitude,
                        receptacle.location!!.longitude
                    )
                ).title(receptacle.documentId + " : " + receptacle.type)
            )
        }
    }

    private fun createDeleteDialog(marker: Marker) {
        val documentId = marker.title.substringBefore(" ")
        val builder1 = AlertDialog.Builder(this)
        builder1.setMessage("Are you sure you want to delete receptacle $documentId?")
        builder1.setCancelable(true)

        builder1.setPositiveButton("Delete Receptacle") {
            dialog, _ -> deleteReceptacleFromFirestore(documentId, marker); dialog.cancel()
        }

        builder1.setNegativeButton("Cancel") {
            dialog, _ -> dialog.cancel()
        }

        val alert11 = builder1.create()
        alert11.show()
    }

    private fun deleteReceptacleFromFirestore(documentId: String, marker: Marker) {
        db.collection("Receptacles").document(documentId).delete()
                .addOnSuccessListener {
                    marker.remove()
                }
                .addOnFailureListener {
                    Toast.makeText(this,
                            "Error deleting document $documentId: ${it.localizedMessage}",
                            Toast.LENGTH_LONG).show()
                }
    }

    private fun listenForFirestoreChanges() {
        db.collection("Receptacles").addSnapshotListener{snapshots, e ->
            if (e != null) {
                Log.w("firestoreListenEvent", "listen:error", e)
                return@addSnapshotListener
            }

            if (snapshots != null) {
                for (changes in snapshots.documentChanges) {
                    when (changes.type) {
                        DocumentChange.Type.ADDED -> displayNewReceptacle(changes.document)
                        DocumentChange.Type.MODIFIED -> updateDisplayedReceptacleData(changes.document)
                        DocumentChange.Type.REMOVED -> removeReceptacleFromDisplay(changes.document)
                    }
                }
            }

            updateUI()
        }
    }

    private fun displayNewReceptacle(document: QueryDocumentSnapshot) {
        mapOfReceptacles[document.id] = Receptacle(
                document.data["Type"] as String,
                document.data["Location"] as GeoPoint?,
                document.data["Description"] as String?,
                document.data["Date Reported"] as String?,
                document.id
        )
    }

    private fun updateDisplayedReceptacleData(document: QueryDocumentSnapshot) {
        mapOfReceptacles[document.id] = Receptacle(
                document.data["Type"] as String,
                document.data["Location"] as GeoPoint?,
                document.data["Description"] as String?,
                document.data["Date Reported"] as String?,
                document.id
        )
    }

    private fun removeReceptacleFromDisplay(document: QueryDocumentSnapshot) {
        mapOfReceptacles.remove(document.id)
    }
}
