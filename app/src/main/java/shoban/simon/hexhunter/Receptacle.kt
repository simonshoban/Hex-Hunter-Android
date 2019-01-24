package shoban.simon.hexhunter

import com.google.firebase.firestore.*
import java.io.Serializable

data class Receptacle (
        var type: String,
        var location: GeoPoint?,
        var description: String?,
        var dateReported: String?,
        val documentId: String) : Serializable