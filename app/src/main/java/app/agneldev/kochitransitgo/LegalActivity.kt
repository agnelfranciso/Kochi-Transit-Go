package app.agneldev.kochitransitgo

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.color.DynamicColors

class LegalActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)

        val toolbar = findViewById<Toolbar>(R.id.legalToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val legalAttributionText = findViewById<TextView>(R.id.legalAttributionText)
        legalAttributionText.text = Html.fromHtml(
            "Contains data provided by <a href=\"http://kochimetro.org/opendata/TermsOfUse.pdf\">Kochi Metro Rail Limited</a>.<br/><br/>Use allowed without attribution: No<br/>Creating derived products allowed: Yes",
            Html.FROM_HTML_MODE_LEGACY
        )
        legalAttributionText.movementMethod = LinkMovementMethod.getInstance()
    }
}
