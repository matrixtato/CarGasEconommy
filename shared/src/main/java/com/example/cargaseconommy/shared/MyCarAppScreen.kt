package com.example.cargaseconommy.shared

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.model.Action
import androidx.car.app.model.Template

class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Custo/Km")
                    .addText("Compare álcool e gasolina em tempo real")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Abrir Google Maps")
                    .setOnClickListener {
                        carContext.startCarApp(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("geo:0,0?q=Posto+de+Combustivel")
                            )
                        )
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Abrir Waze")
                    .setOnClickListener {
                        carContext.startCarApp(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("waze://?q=Posto+de+Combustivel&navigate=yes")
                            )
                        )
                    }
                    .build()
            )
            .build()

        // Usando o MapTemplate para mostrar o mapa ao fundo (se disponível)
        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Cargas Economy")
            .build()
    }
}