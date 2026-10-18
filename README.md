# Shelves

Application Android (Kotlin, Jetpack Compose, Room) pour ranger ses livres en étagères, suivre ses séries et savoir quels tomes on possède.

Sources de données : voir [docs/sources-de-donnees.md](docs/sources-de-donnees.md).

## Fonctionnalités

- **Étagères** : regroupements de livres créés par l'utilisateur (un livre peut être sur plusieurs étagères).
- **Ajout** : scan du code-barres EAN-13 / EAN-8 (scanner Google Play Services, sans permission caméra) ou saisie de l'ISBN.
- **Recherche** : BnF (notice, série, résumé), Google Books et Open Library (résumé, couverture), interrogées en parallèle.
- **Séries** : à l'ajout d'un tome, les autres tomes de la même édition sont récupérés à la BnF ; chaque série affiche « x / y tomes possédés » et se coche tome par tome.
- **Stockage local** : base Room ; sauvegarde automatique Android vers Google Drive, plus export/import JSON via le sélecteur de fichiers (Google Drive y figure).
- **Partage** : une étagère par SMS ou e-mail à un contact du téléphone, ou vers une autre application ; prêt d'un livre à un contact, et liste des prêts en cours.

## Organisation du code

```
app/src/main/java/fr/shelves/
  ShelvesApp.kt          conteneur de dépendances
  MainActivity.kt        navigation
  data/db/               entités et DAO Room
  data/remote/           BnF (SRU/UNIMARC), Open Library, Google Books, fusion
  data/BookRepository.kt logique métier (ajout, séries, prêts)
  data/BackupManager.kt  export/import JSON
  ui/…                   écrans Compose (étagères, étagère, ajout, livre, série, prêts, réglages)
```

## Ouvrir et compiler

Outils : Gradle 9.8, Android Gradle Plugin 9.4 (Kotlin intégré), Kotlin 2.4, compileSdk 37.
N'importe quel JDK 17 ou plus récent convient, y compris le Java 25 fourni avec Android Studio.

1. Ouvrir le dossier dans Android Studio.
2. Compiler : `./gradlew assembleDebug` ; tests unitaires : `./gradlew test`.
3. Lancer sur un appareil ou un émulateur **avec Google Play** (nécessaire au scanner ; sinon, saisir l'ISBN).

## Licence

Code sous licence [MIT](LICENSE). Les données récupérées restent soumises aux conditions de leurs sources : catalogue de la BnF (licence ouverte Etalab, mention de la source), Google Books et Open Library.
