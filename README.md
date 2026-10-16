# EasyComanda 1.2 beta

App Android Java, offline, ottimizzata per tablet in orizzontale con layout proporzionale.

## Novità
- Display scuro dell'ultimo articolo, importo verde e visualizzazione in tempo reale dei numeri digitati.
- Intestazione «CONTO (tocca una voce per modificarla)».
- CHIUDI CONTO: conferma, salvataggio atomico in SQLite e azzeramento del carrello solo dopo il salvataggio.
- Le stampe di comanda e preconto NON registrano vendite.
- Statistiche per giornata e per prodotto, con quantità e incassi.
- Storico delle giornate, anteprima/stampa resoconto e esportazione CSV tramite selettore Android.
- Archivio vendite locale persistente, migrazione database 1→2 senza eliminare i log di stampa preesistenti.

## Build GitHub
Carica il contenuto di questa cartella alla radice del repository. GitHub Actions produce un APK debug in Artifacts.

## Note
Il database resta nel dispositivo: esporta regolarmente il CSV, perché cancellazione dati/disinstallazione possono eliminarlo. La stampa su RAL via TCP è da verificare sul modello fisico. Questa versione è un prototipo beta, da collaudare prima dell'uso in una sagra. I resoconti riguardano le vendite registrate con CHIUDI CONTO, non i vecchi log di stampa.


## Versione 1.3 beta
- Colori pastello casuali e persistenti per categoria/prodotti.
- Raggruppamento per nome e prezzo unitario.
- Pulsante SCONTRINO: stampa non fiscale, archiviazione e chiusura conto.
- STAMPA RESOCONTO odierno dalla cassa e resoconti storici nelle statistiche.
- Totale nel display nero, ANNULLA CONTO, ritorno alla cassa in alto e conferma uscita.
- Android minSdk 21.

**Attenzione**: la conferma di invio TCP non garantisce che la stampante abbia materialmente stampato. Se la stampa ha esito incerto, verificare il foglio prima di ritentare. Non disinstallare/cancellare i dati dell’app per non perdere lo storico.
