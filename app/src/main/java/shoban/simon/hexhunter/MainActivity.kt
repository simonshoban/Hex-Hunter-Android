package shoban.simon.hexhunter

import kotlinx.android.synthetic.main.activity_main.*
import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.text.Editable
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.hex_hunter_action_bar.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private companion object {
        const val RC_SIGN_IN: Int = 20
        const val OPEN_MAP: Int = 21
        const val OPEN_RECEPTACLE_LIST = 22
        const val DB_SUCCESS_DISPLAY_MILLISECONDS: Long = 4000
    }
    private lateinit var mAuth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private var account: GoogleSignInAccount? = null
    private var longitude: Double = 0.0
    private var latitude: Double = 0.0
    private var locationToSave: GeoPoint? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    private var locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location) {
            longitude = location.longitude
            latitude = location.latitude

            displayLocation()
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setFirestoreSettings()
        setCustomActionBar()
        hideKeyboard()
        setupAuthentication()
        getCurrentLocation()
        setButtonListeners()
    }

    override fun onStart() {
        super.onStart()

        currentUser = mAuth.currentUser
        account = GoogleSignIn.getLastSignedInAccount(this)
    }

    private fun setFirestoreSettings() {
        val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        val settings: FirebaseFirestoreSettings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        firestore.firestoreSettings = settings
    }

    /**
     * Replaces default action bar with our custom one
     */
    private fun setCustomActionBar() {
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.hex_hunter_action_bar)
    }

    /**
     * Hides any soft keyboards when opening the app.
     */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(locationDescription.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    private fun setupAuthentication() {
        mAuth = FirebaseAuth.getInstance()

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        // Request Google ID Token
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(resources.getString(R.string.client_id))
                .requestEmail()
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun getCurrentLocation() {
        val permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // ask permissions here using below code
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    10000)
        }

        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        longitude = location.longitude
        latitude = location.latitude

        displayLocation()

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
    }

    /**
     * Updates Latitude and Longitude display.
     */
    private fun displayLocation() {
        latitudeDisplay.text = resources.getString(R.string.latitude_label, latitude, getLatitudinalHemisphere())
        longitudeDisplay.text = resources.getString(R.string.longitude_label, longitude, getLongitudinalHemisphere())
    }

    /**
     * Returns 'N' if in Northern Hemisphere, 'S' otherwise.
     */
    private fun getLatitudinalHemisphere(): Char {
        return if (latitude >= 0) 'N' else 'S'
    }

    /**
     * Returns 'E' if in Eastern Hemisphere, 'W' otherwise.
     */
    private fun getLongitudinalHemisphere(): Char {
        return if (longitude >= 0) 'E' else 'W'
    }

    private fun setButtonListeners() {
        addListenerToRecordButton()
        addListenerToLeftButton()
        addListenerToRightButton()
    }

    /**
     * Clicking on the Record button will save the current location to Firebase.
     */
    private fun addListenerToRecordButton() {
        recordButton.setOnClickListener {
            recordButton.isEnabled = false
            locationToSave = GeoPoint(latitude, longitude)
            saveLocationToFirebase()
        }
    }

    /**
     * Clicking on the Left button will open a list of all known trash receptacles.
     */
    private fun addListenerToLeftButton() {
        leftButton.setOnClickListener {
            leftButton.isEnabled = false
            val intent = Intent(this, DisplayReceptacleActivity::class.java)
            startActivityForResult(intent, OPEN_RECEPTACLE_LIST)
        }
    }

    /**
     * Clicking on the Right button will open a Google Map will all the trash receptacles displayed.
     */
    private fun addListenerToRightButton() {
        rightButton.setOnClickListener {
            rightButton.isEnabled = false
            rightButton.text = resources.getString(R.string.right_button_active)
            val intent = Intent(this, MapsActivity::class.java)

            intent.putExtra("Latitude", latitude)
            intent.putExtra("Longitude", longitude)

            startActivityForResult(intent, OPEN_MAP)
        }
    }

    private fun saveLocationToFirebase() {
        if (userIsLoggedIn()) {
            writeData()
        } else {
            promptForLogin()
        }
    }

    private fun userIsLoggedIn() = account != null

    private fun promptForLogin() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    /**
     * Writes the current location to Firebase. Action bar turns green with confirmation message on success,
     * a toast is raised with an exception on failure.
     */
    private fun writeData() {
        val data: MutableMap<String, Any> = HashMap()
        val type: String = receptacleType.selectedItem.toString()
        val description: String = locationDescription.text.toString().trim()

        recordButton.text = resources.getString(R.string.record_button_active)
        data += "Type" to type
        data += "Location" to locationToSave as Any
        data += "Description" to description
        data += "Date Reported" to getCurrentDateInIso()

        db.collection("Receptacles")
                .add(data)
                .addOnSuccessListener {
                    addFirestoreConfirmation(type, it.id)

                    Handler().postDelayed({
                        removeFirestoreConfirmation()
                    }, DB_SUCCESS_DISPLAY_MILLISECONDS)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error adding document: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    recordButton.text = resources.getString(R.string.record_button)
                    recordButton.isEnabled = true
                    locationDescription.text = "".toEditable()
                }
    }

    /**
     * Changes the Action bar to green and displays data saved to Firebase.
     */
    private fun addFirestoreConfirmation(type: String, documentId: String) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#00BB00")))
        action_bar_first_line.textSize = 14.toFloat()
        action_bar_first_line.text = resources.getString(
                R.string.firestore_confirmation_label,
                type,
                documentId,
                locationToSave?.latitude,
                locationToSave?.longitude)
    }

    /**
     * Restores the Action Bar to the default appearance.
     */
    private fun removeFirestoreConfirmation() {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(resources.getColor(R.color.colorPrimary, theme)))
        action_bar_first_line.textSize = 24.toFloat()
        action_bar_first_line.text = resources.getString(R.string.app_name)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        when (requestCode) {
            RC_SIGN_IN -> {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    Log.w("Bad", "Google sign in failed", e)
                } finally {
                    recordButton.isEnabled = true
                }
            }
            OPEN_MAP -> {
                rightButton.isEnabled = true
                rightButton.text = resources.getString(R.string.right_button)
            }
            OPEN_RECEPTACLE_LIST -> leftButton.isEnabled = true
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount?) {
        Log.d("Firebase", "firebaseAuthWithGoogle:" + acct?.id)
        Log.d("Firebase", "idToken:" + acct?.idToken)

        val credential = GoogleAuthProvider.getCredential(acct?.idToken, null)
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, write Receptacle data to Firebase
                        Log.d("Good", "signInWithCredential:success")
                        currentUser = mAuth.currentUser
                        account = GoogleSignIn.getLastSignedInAccount(this)
                        writeData()
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("Bad", "signInWithCredential:failure", task.exception)
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show()
                        //Snackbar.make(main_layout, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                    }
                }
    }

    private fun getCurrentDateInIso(): String {
        val DB_DATE_FORMAT = "yyyy-MM-dd"
        return LocalDate.now().format(DateTimeFormatter.ofPattern(DB_DATE_FORMAT))
    }

    private fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)
}
