# FraudWatch — Guide de configuration complet

## Structure du projet
```
FraudWatch/
├── app/
│   ├── src/main/
│   │   ├── java/com/fraudwatch/
│   │   │   ├── FraudWatchApplication.kt    ← Application class
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── model/                  ← Report, AIResponse, OllamaModels
│   │   │   │   ├── remote/                 ← OllamaService, RetrofitClient
│   │   │   │   └── repository/             ← AIRepository, ReportRepository
│   │   │   ├── ui/
│   │   │   │   ├── auth/                   ← LoginActivity, RegisterActivity
│   │   │   │   ├── home/                   ← HomeFragment + HomeViewModel
│   │   │   │   ├── camera/                 ← CameraFragment + CameraViewModel
│   │   │   │   │   └── CameraFragmentDirections.kt
│   │   │   │   ├── result/                 ← ResultFragment + ResultViewModel
│   │   │   │   │   └── ResultFragmentArgs.kt
│   │   │   │   ├── map/                    ← MapFragment + MapViewModel
│   │   │   │   └── history/                ← HistoryFragment + HistoryViewModel
│   │   │   │       └── HistoryFragmentDirections.kt
│   │   │   ├── utils/                      ← Extensions, NotificationHelper, LocationHelper
│   │   │   ├── adapters/                   ← HistoryAdapter
│   │   │   └── viewmodel/                  ← 5 ViewModels MVVM
│   │   └── res/
│   │       ├── layout/                     ← 9 layouts XML Material Design 3
│   │       ├── navigation/nav_graph.xml
│   │       ├── drawable/                   ← 20+ icônes vectorielles + drawables
│   │       ├── mipmap-*/                   ← Icônes launcher (adaptive API 26+)
│   │       ├── anim/                       ← 4 animations slide
│   │       ├── menu/bottom_nav_menu.xml
│   │       ├── xml/network_security_config.xml
│   │       └── values/                     ← colors, strings, themes, dimens
│   ├── build.gradle
│   └── google-services.json               ← À REMPLACER
├── build.gradle
├── settings.gradle
├── gradle.properties                      ← Mettre la clé Maps ici
├── firestore.rules                        ← Règles de sécurité Firestore
├── storage.rules                          ← Règles de sécurité Storage
└── gradlew.bat
```

---

## ÉTAPE 1 — Firebase (OBLIGATOIRE)

### 1.1 Créer le projet Firebase
1. Aller sur https://console.firebase.google.com
2. Créer un projet → "FraudWatch"
3. Ajouter une app Android :
   - Package name : `com.fraudwatch`
   - SHA-1 (optionnel pour auth email)

### 1.2 Télécharger google-services.json
- Télécharger et remplacer `app/google-services.json`

### 1.3 Activer les services Firebase
Dans la console Firebase :
- **Authentication** → Connexion par email/mot de passe → Activer
- **Firestore Database** → Créer la base → Mode test (pour développement)
- **Storage** → Commencer → Mode test

### 1.4 Déployer les règles de sécurité (optionnel en dev)
```bash
# Installer Firebase CLI
npm install -g firebase-tools
firebase login
firebase init
firebase deploy --only firestore:rules,storage
```

### 1.5 Index Firestore requis
La requête `getUserReports()` nécessite un **index composite**.
Lors de la première exécution, Firebase affiche dans logcat :
```
FAILED_PRECONDITION: The query requires an index.
You can create it here: https://console.firebase.google.com/...
```
Cliquez sur le lien pour créer l'index automatiquement.

---

## ÉTAPE 2 — Google Maps (OBLIGATOIRE)

1. Aller sur https://console.cloud.google.com
2. Sélectionner/créer un projet
3. Activer l'API : **Maps SDK for Android**
4. Créer une clé API → **Identifiants**
5. Dans `gradle.properties`, remplacer :
   ```
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE
   ```
   par votre vraie clé.

---

## ÉTAPE 3 — Ollama + LLaVA (Intelligence Artificielle)

### Installation sur votre PC
```bash
# Télécharger Ollama depuis https://ollama.ai
# Puis dans un terminal :
ollama pull llava        # Télécharge le modèle (~4 GB)
ollama serve             # Lance le serveur sur localhost:11434
```

### Connexion depuis l'émulateur
L'émulateur Android accède à votre PC via l'IP `10.0.2.2`.
L'URL API est déjà configurée : `http://10.0.2.2:11434/api/generate`

### Sur appareil physique (Wi-Fi)
Remplacer `10.0.2.2` par l'IP locale de votre PC dans :
`app/src/main/java/com/fraudwatch/data/remote/RetrofitClient.kt`
```kotlin
private const val BASE_URL = "http://192.168.X.X:11434/"
```

---

## ÉTAPE 4 — Police Poppins (Optionnel)

1. Télécharger sur https://fonts.google.com/specimen/Poppins
2. Copier les fichiers dans `app/src/main/res/font/` :
   - `Poppins-Regular.ttf` → renommer en `poppins_regular.ttf`
   - `Poppins-Bold.ttf` → renommer en `poppins_bold.ttf`
3. Dans `res/values/themes.xml`, ajouter dans le style `Theme.FraudWatch` :
   ```xml
   <item name="android:fontFamily">@font/poppins_regular</item>
   ```

---

## ÉTAPE 5 — Ouvrir dans Android Studio

1. Ouvrir Android Studio
2. **File → Open** → Sélectionner le dossier `FraudWatch`
3. Attendre la synchronisation Gradle
4. Connecter un émulateur API 26+ (recommandé: Pixel 6 API 33)
5. **Run → Run 'app'**

---

## Configuration minimale
| Élément | Requis | Notes |
|---------|--------|-------|
| `google-services.json` | ✅ | Firebase Console |
| `MAPS_API_KEY` | ✅ | Google Cloud Console |
| Ollama + LLaVA | ✅ | Pour l'analyse IA |
| Police Poppins | ❌ | Optionnel, améliore le design |
| Index Firestore | ⚠️ | Auto-créé au premier lancement |

---

## Dépannage fréquent

### Erreur "google-services.json not found"
→ Remplacer `app/google-services.json` par le vrai fichier Firebase

### Erreur "MAPS_API_KEY missing"
→ Vérifier `gradle.properties` : `MAPS_API_KEY=votre_clé`

### "Connection refused" pour l'IA
→ Vérifier qu'Ollama tourne : `ollama serve`
→ Vérifier que LLaVA est installé : `ollama list`

### Crashs sur appareils API < 26
→ L'app est optimisée pour API 26+, tester sur émulateur API 30+

### Index Firestore manquant
→ Suivre le lien dans logcat pour créer l'index automatiquement
