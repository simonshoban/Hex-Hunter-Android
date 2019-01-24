package shoban.simon.hexhunter

import android.app.ListActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.QueryDocumentSnapshot
import kotlinx.android.synthetic.main.activity_main.*

class DisplayReceptacleActivity : ListActivity() {
    private lateinit var mapOfReceptacles: HashMap<String, Receptacle>
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mapOfReceptacles = HashMap()

        listenForFirestoreChanges()
    }

    private fun updateUI() {
        val listOfReceptacles: MutableList<Receptacle> = ArrayList()
        mapOfReceptacles.forEach {_, value -> listOfReceptacles.add(value) }

        val lv: ListView = listView

        val arrayAdapter = object:  ArrayAdapter<Receptacle>(
                this, android.R.layout.simple_list_item_2, android.R.id.text1, listOfReceptacles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view: View = super.getView(position, convertView, parent)
                val text1: TextView = view.findViewById(android.R.id.text1)
                val text2: TextView = view.findViewById(android.R.id.text2)
                val receptacle = listOfReceptacles[position]

                text1.text = receptacle.type
                text2.text = resources.getString(
                        R.string.location_list_item_info,
                        receptacle.location?.latitude,
                        receptacle.location?.longitude,
                        receptacle.description,
                        receptacle.dateReported
                )

                return view
            }
        }

        lv.adapter = arrayAdapter
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
