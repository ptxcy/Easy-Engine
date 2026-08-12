# Easy-Engine

Java/LWJGL/OpenGL-Prototyp für die Bachelorarbeit *"Integration kontinuierlicher und
diskreter Einflussfaktoren in der prozeduralen Generierung dreidimensionaler
Landschaften für Echtzeitrendering"*.

## Build & Start

```
./gradlew runEngine
```

Startet Fenster + Rendering-Loop (macOS: `-XstartOnFirstThread` wird vom Gradle-Task
automatisch gesetzt).

## Terrain Editor — Kurzanleitung

Ein In-App-Panel zum Bearbeiten der Landschaftsgenerierung, ohne die App neu zu
starten. Alles hier bezieht sich direkt auf das, was im Fenster sichtbar ist.

### Öffnen & Schließen

Taste `P` drücken. Solange das Panel offen ist, reagiert die Kamera nicht auf WASD —
Eingaben gehen an die Zahlenfelder, nicht an die Spielfigur.

### Der Pool — was die Welt gerade zeigt

Vier Felder: `Temp von`/`bis` und `Feuchte von`/`bis` (jeweils 0–2). Sie bestimmen,
welcher Ausschnitt der 9 möglichen Bodentypen gerade überhaupt auf der Karte
vorkommen darf — überall, nicht nur um den Spieler herum.

- **Gleicher Wert bei von/bis** → ganze Karte = ein einziger Bodentyp.
- **Unterschiedliche Werte** → der Übergang dazwischen ist sichtbar.

Zum Ausprobieren eines einzelnen Bodentyps: alle vier Felder auf denselben engen
Bereich stellen (z. B. Temp 1 bis 1, Feuchte 2 bis 2).

### Position übernehmen

Irgendwohin laufen, wo das Gelände gerade gefällt, dann den Knopf klicken. Er liest
ab, was genau unter den Füßen generiert wurde, und stellt Pool sowie Editier-Zelle
automatisch darauf ein. Danach `Regenerate` drücken — die ganze sichtbare Karte wird
zu genau diesem Gelände.

### Editier-Zelle — was die Regler darunter verändern

Zwei eigene Felder `Temp`/`Feuchte`, unabhängig vom Pool. Sie legen fest, welcher der
9 Bodentypen gerade von den vier Reglern darunter beeinflusst wird — auch wenn der
Pool gerade einen ganz anderen Ausschnitt zeigt. So lässt sich z. B. eine Verblendung
zwischen zwei Zellen ansehen und trotzdem gezielt nur eine davon nachjustieren.

### Die vier Form-Regler

Bestimmen, wie das Gelände der Editier-Zelle geformt ist.

| Regler | Bereich | Was sichtbar wird |
|---|---|---|
| Amplitude | 0–3 | Wie hoch die Berge werden. Höher = mehr Höhenunterschied, niedriger = flacher. |
| Frequenz | 0.1–5 | Hoch = viele kleine Hügel dicht an dicht. Niedrig = wenige breite, weit auseinanderliegende Berge. |
| Persistence | 0.05–0.95 | Niedrig = sanfte, glatte Hügel wie Dünen. Hoch = raue, zerklüftete Felsen. |
| Lacunarity | 1–4 | Niedrig = wirkt eher flach/einheitlich. Um 2 = natürlich wirkendes Gelände. Hoch = viele Detailebenen übereinander, wirkt zerrissen. |

### Die globalen Regler

Wirken auf die ganze Karte, unabhängig vom Bodentyp.

| Regler | Bereich | Was sichtbar wird |
|---|---|---|
| Octaves | 1–8 | Wenige = weiche, runde Hügel ohne Kleinstdetails. Viele = zusätzliche feine Felskanten und Risse. |
| Height Scale | 0.001–0.2 | Klein = riesige, weitläufige Landschaft. Groß = viele kleine Hügel dicht gedrängt. |
| Height Amp. | 1–300 | Wie hoch die höchsten Berge insgesamt werden können (in Metern). |
| Temp Scale | 1e-5–0.01 | Wie großflächig die Temperaturzonen sind. Klein halten, sonst wirken Bodentypen fleckig statt großflächig. |
| Humid. Scale | 1e-5–0.01 | Dasselbe für Feuchtezonen. |
| Temp Lapse | 0–0.05 | Höher = Bergspitzen wirken klimatisch kälter, je höher sie sind. |

### Regenerate

> **Wichtig:** Zahlen eintippen ändert erstmal nur die gespeicherten Werte — am
> Bildschirm passiert nichts, bis `Regenerate` geklickt wird. Danach baut sich die
> aktuell sichtbare Karte mit den neuen Werten neu auf.

### Typischer Ablauf

1. Herumlaufen, bis eine Stelle gefällt — oder Temp/Feuchte manuell eintragen.
2. `Position übernehmen` klicken, dann `Regenerate` — die ganze Karte zeigt jetzt
   genau dieses Gelände.
3. An den vier Form-Reglern schrauben, wieder `Regenerate`, bis es passt.
4. Pool-Bereich vorsichtig erweitern (z. B. Feuchte bis um eins erhöhen),
   `Regenerate`, Übergang anschauen.

Maus über ein Feld halten zeigt eine kurze Beschreibung als Tooltip.
